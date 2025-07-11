/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.security.interceptor.devx

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.DEVX_ACCESS_FROM_OFFICE
import com.tencent.bkrepo.common.api.constant.HEADER_DEVX_ACCESS_FROM
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.IpUtils
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 云研发源ip拦截器，只允许项目的云桌面ip通过
 * */
open class DevXAccessInterceptor(private val devXProperties: DevXProperties) : HandlerInterceptor {
    private val httpClient = OkHttpClient.Builder().build()
    private val executor by lazy {
        Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            ThreadFactoryBuilder().setNameFormat("devx-access-%d").build(),
        )
    }
    private val projectIpsCache: LoadingCache<String, Set<String>> = CacheBuilder.newBuilder()
        .maximumSize(devXProperties.cacheSize)
        .refreshAfterWrite(devXProperties.cacheExpireTime)
        .build(object : CacheLoader<String, Set<String>>() {
            override fun load(key: String): Set<String> {
                return listIpFromProject(key) +
                        listCvmIpFromProject(key) +
                        listIpFromProps(key) +
                        listIpFromProjects(key)
            }

            override fun reload(key: String, oldValue: Set<String>): ListenableFuture<Set<String>> {
                return Futures.submit(Callable { load(key) }, executor)
            }
        })

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val user = SecurityUtils.getUserId()
        if (!devXProperties.enabled || user in devXProperties.userWhiteList) {
            return true
        }

        if (devXProperties.srcHeaderName.isNullOrEmpty() || devXProperties.srcHeaderValues.size < 2) {
            throw SystemErrorException(
                CommonMessageCode.SYSTEM_ERROR,
                "devx srcHeaderName or srcHeaderValues not configured"
            )
        }

        val headerValue = request.getHeader(devXProperties.srcHeaderName)
        return when (headerValue) {
            devXProperties.srcHeaderValues[0] -> {
                getProjectId(request)?.let { projectId ->
                    val srcIp = HttpContextHolder.getClientAddress()
                    checkIpBelongToProject(projectId, srcIp)
                }
                true
            }

            devXProperties.srcHeaderValues[1] -> {
                request.setAttribute(HEADER_DEVX_ACCESS_FROM, DEVX_ACCESS_FROM_OFFICE)
                devXProperties.restrictedUserPrefix.forEach { checkUserSuffixAndPrefix(user, prefix = it) }
                devXProperties.restrictedUserSuffix.forEach { checkUserSuffixAndPrefix(user, suffix = it) }
                true
            }

            else -> true
        }
    }

    private fun checkIpBelongToProject(projectId: String, srcIp: String) {
        val projectIps = projectIpsCache.get(projectId)
        if (srcIp !in projectIps && !projectIps.any { it.contains('/') && IpUtils.isInRange(srcIp, it) }) {
            logger.info("Illegal src ip[$srcIp] in project[$projectId].")
            throw PermissionException()
        }
    }

    private fun checkUserSuffixAndPrefix(user: String, prefix: String? = null, suffix: String? = null) {
        val matchPrefix = prefix?.let { user.startsWith(it) } ?: false
        val matchSuffix = suffix?.let { user.endsWith(it) } ?: false

        if (matchPrefix || matchSuffix) {
            logger.info("User[$user] was forbidden because of suffix or prefix")
            throw if (user == ANONYMOUS_USER) AuthenticationException() else PermissionException()
        }
    }

    protected open fun getProjectId(request: HttpServletRequest): String? {
        val uriAttribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) ?: return null
        require(uriAttribute is Map<*, *>)
        return uriAttribute[PROJECT_ID]?.toString()
    }

    private fun listIpFromProject(projectId: String): Set<String> {
        val reqBuilder = Request.Builder().url("${devXProperties.workspaceUrl}?project_id=$projectId")
        logger.info("Update project[$projectId] ips.")
        val ips = HashSet<String>()
        doRequest<List<DevXWorkSpace>>(reqBuilder, jacksonTypeRef())?.forEach { workspace ->
            workspace.innerIp?.substringAfter('.')?.let { ips.add(it) }
        }
        return ips
    }

    private fun listIpFromProjects(projectId: String): Set<String>{
        val projectIdList = devXProperties.projectWhiteList[projectId] ?: emptySet()
        val ips = HashSet<String>()
        projectIdList.forEach {
            ips.addAll(listIpFromProject(it))
            ips.addAll(listCvmIpFromProject(it))
        }
        return ips
    }

    private fun listIpFromProps(projectId: String) = devXProperties.projectCvmWhiteList[projectId] ?: emptySet()

    private fun listCvmIpFromProject(projectId: String): Set<String> {
        val workspaceUrl = devXProperties.cvmWorkspaceUrl.replace("{projectId}", projectId)
        val reqBuilder = Request
            .Builder()
            .url("$workspaceUrl?pageSize=${devXProperties.cvmWorkspacePageSize}")
            .header("X-DEVOPS-UID", devXProperties.cvmWorkspaceUid)
        logger.info("Update project[$projectId] cvm ips.")
        val res = doRequest<PageResponse<DevXCvmWorkspace>>(reqBuilder, jacksonTypeRef())
        if ((res?.totalPages ?: 0) > 1) {
            logger.error("[$projectId] has [${res?.totalPages}] page cvm workspace")
        }
        return res?.records?.mapTo(HashSet()) { it.ip } ?: emptySet()
    }

    private fun <T> doRequest(requestBuilder: Request.Builder, jacksonTypeRef: TypeReference<QueryResponse<T>>): T? {
        val apiAuth = ApiAuth(devXProperties.appCode, devXProperties.appSecret)
        val token = apiAuth.toJsonString().replace(System.lineSeparator(), "")
        val req = requestBuilder.header("X-Bkapi-Authorization", token).build()
        val response = httpClient.newCall(req).execute()
        if (!response.isSuccessful || response.body == null) {
            val errorMsg = response.body?.bytes()?.let { String(it) }
            logger.error("${response.code} $errorMsg")
            return null
        }

        val queryResponse = JsonUtils.objectMapper.readValue(response.body!!.byteStream(), jacksonTypeRef)
        if (queryResponse.status != 0) {
            logger.error("request bkapi failed ${response.code} status:${queryResponse.status}")
            return null
        }
        return queryResponse.data!!
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DevXAccessInterceptor::class.java)
    }
}
