/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.opdata.security

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import com.tencent.bkrepo.common.security.http.platform.PlatformAuthHandler
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import feign.FeignException
import feign.RetryableException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

/**
 * 容错版 PlatformAuthHandler。
 *
 * auth 微服务不可达时按入口分级降级，避免 opdata 整体 500：
 * - `X-BKREPO-API-TYPE: web`（web.conf 已 cookie 鉴权并强制覆盖该 header）→ 采信 `X-BKREPO-UID`，admin 可继续操作；
 * - 其它入口（bkapigw / backend.conf 等）→ 降级匿名，避免伪造 `X-BKREPO-UID` 越权。
 *
 * 安全前提：所有 nginx 入口都已强制覆盖 `X-BKREPO-API-TYPE`（web.conf="web" / bkapigw.conf="bkapigw" /
 * backend.conf="api" 且清空 UID），外部无法伪造 "web" 标识。要求 opdata 端口仅对网关可达。
 *
 * 仅对 Feign 网络异常 / 5xx 降级；4xx 仍按认证失败处理。
 */
class ResilientPlatformAuthHandler(
    authenticationManager: AuthenticationManager
) : PlatformAuthHandler(authenticationManager) {

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        return try {
            super.onAuthenticate(request, authCredentials)
        } catch (e: RetryableException) {
            logger.warn("Auth service unreachable, try fallback. cause=${e.message}")
            fallback(request)
        } catch (e: FeignException) {
            // 仅对 5xx / 网络异常降级，4xx 仍按凭证非法处理
            if (e.status() in 500..599 || e.status() == -1) {
                logger.warn("Auth service error ${e.status()}, try fallback. cause=${e.message}")
                fallback(request)
            } else {
                throw e
            }
        }
    }

    /** web 入口采信 `X-BKREPO-UID`，其它入口降级匿名以防身份伪造。 */
    private fun fallback(request: HttpServletRequest): String {
        val apiType = request.getHeader(HEADER_API_TYPE).orEmpty().trim()
        val userId = if (apiType.equals(API_TYPE_WEB, ignoreCase = true)) {
            request.getHeader(AUTH_HEADER_UID).orEmpty().trim()
                .takeIf { it.isNotEmpty() } ?: ANONYMOUS_USER
        } else {
            logger.warn("Non-web entry [$apiType] in fallback, forcing anonymous.")
            ANONYMOUS_USER
        }
        request.setAttribute(USER_KEY, userId)
        return userId
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResilientPlatformAuthHandler::class.java)
        private const val HEADER_API_TYPE = "X-BKREPO-API-TYPE"
        private const val API_TYPE_WEB = "web"
    }
}
