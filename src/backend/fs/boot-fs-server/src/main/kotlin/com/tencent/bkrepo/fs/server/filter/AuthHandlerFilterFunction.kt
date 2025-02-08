/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.PLATFORM_KEY
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.fs.server.service.PermissionService
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils.bearerToken
import com.tencent.bkrepo.fs.server.utils.ReactiveSecurityUtils.platformCredentials
import com.tencent.bkrepo.fs.server.utils.SecurityManager
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * 认证过滤器，处理服务器认证
 * */
class AuthHandlerFilterFunction(
    private val securityManager: SecurityManager,
    private val permissionService: PermissionService
) : CoHandlerFilterFunction {

    override suspend fun filter(
        request: ServerRequest,
        next: suspend (ServerRequest) -> ServerResponse
    ): ServerResponse {
        if (uncheckedUrlPrefixList.any { request.path().startsWith(it) }) {
            return next(request)
        }
        var user = ANONYMOUS_USER

        val platformAuthCredentials = request.platformCredentials()
        if (platformAuthCredentials != null) {
            request.exchange().attributes[PLATFORM_KEY] = permissionService.checkPlatformAccount(
                accessKey = platformAuthCredentials.accessKey,
                secretKey = platformAuthCredentials.secretKey
            )
            request.exchange().attributes[USER_KEY] =
                request.headers().header(AUTH_HEADER_UID).firstOrNull() ?: ANONYMOUS_USER
            return next(request)
        }

        val token = if (request.path().startsWith("/service")) {
            request.headers().header("X-BKREPO-MS-UID").firstOrNull()?.let {
                user = it
            }
            request.headers().header("X-BKREPO-SECURITY-TOKEN").firstOrNull()
        } else {
            request.bearerToken()
        } ?: throw AuthenticationException("missing token.")

        try {
            val jws = securityManager.validateToken(token)
            request.exchange().attributes[USER_KEY] = jws.body.subject ?: user
        } catch (exception: ExpiredJwtException) {
            logger.info("validate token[$token] failed:", exception)
            throw AuthenticationException("Expired token")
        } catch (exception: JwtException) {
            logger.info("validate token[$token] failed:", exception)
            throw AuthenticationException("Invalid token")
        } catch (exception: IllegalArgumentException) {
            logger.info("validate token[$token] failed:", exception)
            throw AuthenticationException("Empty token")
        }
        return next(request)
    }

    companion object {
        private val uncheckedUrlPrefixList = listOf("/login", "/devx/login", "/ioa", "/client/metrics/push")
        private val logger = LoggerFactory.getLogger(AuthHandlerFilterFunction::class.java)
    }
}
