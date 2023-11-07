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
import com.tencent.bkrepo.common.api.util.IpUtils
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
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 云研发源ip拦截器，只允许项目的云桌面ip通过
 * */
open class DevXAccessInterceptor(private val devXProperties: DevXProperties) : HandlerInterceptor {
    private val httpClient = OkHttpClient.Builder().build()
    private val projectIpsCache: LoadingCache<String, Set<String>> = CacheBuilder.newBuilder()
        .maximumSize(devXProperties.cacheSize)
        .expireAfterWrite(devXProperties.cacheExpireTime)
        .build(CacheLoader.from { key -> listIpFromProject(key) })

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
            throw PermissionException()
        }
    }

    protected open fun getProjectId(request: HttpServletRequest): String? {
        val uriAttribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) ?: return null
        require(uriAttribute is Map<*, *>)
        return uriAttribute[PROJECT_ID]?.toString()
    }

    private fun listIpFromProject(projectId: String): Set<String> {
        val apiAuth = ApiAuth(devXProperties.appCode, devXProperties.appSecret)
        val token = apiAuth.toJsonString().replace(System.lineSeparator(), "")
        val workspaceUrl = devXProperties.workspaceUrl
        val request = Request.Builder()
            .url("$workspaceUrl?project_id=$projectId")
            .header("X-Bkapi-Authorization", token)
            .build()
        logger.info("Update project[$projectId] ips.")
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful || response.body == null) {
            val errorMsg = response.body?.bytes()?.let { String(it) }
            logger.error("${response.code} $errorMsg")
            return devXProperties.projectCvmWhiteList[projectId] ?: emptySet()
        }
        val ips = HashSet<String>()
        devXProperties.projectCvmWhiteList[projectId]?.let { ips.addAll(it) }
        response.body!!.byteStream().readJsonString<QueryResponse>().data.forEach { workspace ->
            workspace.innerIp?.substringAfter('.')?.let { ips.add(it) }
        }
        return ips
    }



    companion object {
        private val logger = LoggerFactory.getLogger(DevXAccessInterceptor::class.java)
    }
}
