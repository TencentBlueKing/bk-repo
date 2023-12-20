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

package com.tencent.bkrepo.fs.server.filter

import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.IpUtils
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.interceptor.devx.DevXProperties
import com.tencent.bkrepo.fs.server.context.ReactiveRequestContextHolder
import com.tencent.bkrepo.fs.server.utils.DevxWorkspaceUtils
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

class DevXAccessFilter(
    private val devXProperties: DevXProperties
) : CoHandlerFilterFunction {
    override suspend fun filter(
        request: ServerRequest,
        next: suspend (ServerRequest) -> ServerResponse
    ): ServerResponse {
        if (uncheckedUrlPrefixList.any { request.path().startsWith(it) }) {
            return next(request)
        }

        val user = ReactiveSecurityUtils.getUser()
        if (!devXProperties.enabled || user in devXProperties.userWhiteList) {
            return next(request)
        }

        if (devXProperties.srcHeaderName.isNullOrEmpty() || devXProperties.srcHeaderValues.size < 2) {
            throw SystemErrorException(
                CommonMessageCode.SYSTEM_ERROR,
                "devx srcHeaderName or srcHeaderValues not configured"
            )
        }

        val headerValue = request.headers().firstHeader(devXProperties.srcHeaderName!!)
        when (headerValue) {
            devXProperties.srcHeaderValues[0] -> {
                getProjectId(request)?.let { projectId ->
                    val srcIp = ReactiveRequestContextHolder.getClientAddress()
                    checkIpBelongToProject(projectId, srcIp)
                }
            }

            devXProperties.srcHeaderValues[1] -> {
                devXProperties.restrictedUserPrefix.forEach { checkUserSuffixAndPrefix(user, prefix = it) }
                devXProperties.restrictedUserSuffix.forEach { checkUserSuffixAndPrefix(user, suffix = it) }
            }
        }

        return next(request)
    }

    private fun getProjectId(request: ServerRequest): String? {
        return try {
            request.pathVariable(PROJECT_ID)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private suspend fun checkIpBelongToProject(projectId: String, srcIp: String, isRetry: Boolean = false) {
        val projectIps = DevxWorkspaceUtils.getIpList(projectId).awaitSingle()
        val notBelong = srcIp !in projectIps &&
            !projectIps.any { it.contains('/') && IpUtils.isInRange(srcIp, it) }
        if (notBelong && !isRetry && !DevxWorkspaceUtils.knownIp(srcIp)) {
            DevxWorkspaceUtils.refreshIpListCache(projectId)
            return checkIpBelongToProject(projectId, srcIp, true)
        }
        if (notBelong) {
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


    companion object {
        private val logger = LoggerFactory.getLogger(DevXAccessFilter::class.java)
        private val uncheckedUrlPrefixList = listOf("/login", "/devx/login", "/service", "/token", "/ioa")
    }
}
