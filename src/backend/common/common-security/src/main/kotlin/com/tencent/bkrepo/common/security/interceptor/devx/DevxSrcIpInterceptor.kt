/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 云研发源ip拦截器，只允许项目的云桌面ip通过
 * */
open class DevxSrcIpInterceptor(private val devxProperties: DevxProperties) : HandlerInterceptor {
    private val httpClient = OkHttpClient.Builder().build()
    private val projectIpsCache: LoadingCache<String, Set<String>> = CacheBuilder.newBuilder()
        .maximumSize(MAX_CACHE_PROJECT_SIZE)
        .expireAfterWrite(CACHE_EXPIRE_TIME, TimeUnit.SECONDS)
        .build(CacheLoader.from { key -> listIpFromProject(key) })

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val user = SecurityUtils.getUserId()
        if (!devxProperties.enabled || user in devxProperties.userWhiteList) {
            return true
        }

        if (devxProperties.srcHeaderName.isNullOrEmpty() || devxProperties.srcHeaderValues.size < 2) {
            throw SystemErrorException(
                CommonMessageCode.SYSTEM_ERROR,
                "devx srcHeaderName or srcHeaderValues not configured"
            )
        }

        val headerValue = request.getHeader(devxProperties.srcHeaderName)
        return when (headerValue) {
            devxProperties.srcHeaderValues[0] -> {
                getProjectId(request)?.let { projectId ->
                    val srcIp = HttpContextHolder.getClientAddress()
                    checkIpBelongToProject(projectId, srcIp)
                }
                true
            }

            devxProperties.srcHeaderValues[1] -> {
                devxProperties.restrictedUserPrefix.forEach { checkUserSuffixAndPrefix(user, prefix = it) }
                devxProperties.restrictedUserSuffix.forEach { checkUserSuffixAndPrefix(user, suffix = it) }
                true
            }

            else -> true
        }
    }

    private fun checkIpBelongToProject(projectId: String, srcIp: String) {
        if (!inWhiteList(srcIp, projectId)) {
            logger.info("Illegal src ip[$srcIp] in project[$projectId].")
            throw PermissionException()
        }
    }

    private fun checkUserSuffixAndPrefix(user: String, prefix: String? = null, suffix: String? = null) {
        val matchPrefix = prefix?.let { user.startsWith(it) } ?: false
        val matchSuffix = suffix?.let { user.endsWith(it) } ?: false

        if (matchPrefix || matchSuffix) {
            logger.info("User[$user] access from src ip[${HttpContextHolder.getClientAddress()}] was forbidden")
            throw PermissionException()
        }
    }

    protected open fun getProjectId(request: HttpServletRequest): String? {
        val uriAttribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) ?: return null
        require(uriAttribute is Map<*, *>)
        return uriAttribute[PROJECT_ID]?.toString()
    }

    private fun inWhiteList(ip: String, projectId: String): Boolean {
        return projectIpsCache.get(projectId).contains(ip)
    }

    private fun listIpFromProject(projectId: String): Set<String> {
        val apiAuth = ApiAuth(devxProperties.appCode, devxProperties.appSecret)
        val token = apiAuth.toJsonString().replace(System.lineSeparator(), "")
        val workspaceUrl = devxProperties.workspaceUrl
        val request = Request.Builder()
            .url("$workspaceUrl?project_id=$projectId")
            .header("X-Bkapi-Authorization", token)
            .build()
        logger.info("Update project[$projectId] ips.")
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful || response.body == null) {
            val errorMsg = response.body?.bytes()?.let { String(it) }
            logger.error("${response.code} $errorMsg")
            return emptySet()
        }
        val ips = HashSet<String>()
        devxProperties.projectCvmWhiteList[projectId]?.let { ips.addAll(it) }
        return response.body!!.byteStream().readJsonString<QueryResponse>().data.mapTo(ips) {
            it.inner_ip.substringAfter('.')
        }
    }

    data class ApiAuth(
        val bk_app_code: String,
        val bk_app_secret: String,
    )

    data class QueryResponse(
        val status: Int,
        val data: List<DevxWorkSpace>,
    )

    data class DevxWorkSpace(
        val workspace_name: String,
        val project_id: String,
        val creator: String,
        val region_id: String,
        val inner_ip: String,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(DevxSrcIpInterceptor::class.java)
        private const val MAX_CACHE_PROJECT_SIZE = 1000L
        private const val CACHE_EXPIRE_TIME = 60L
    }
}
