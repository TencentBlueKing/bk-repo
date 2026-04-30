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
 * 背景：opdata 请求经网关会携带 `Authorization: Platform xxx`，
 * 默认 [PlatformAuthHandler] 在 `onAuthenticate` 时通过 Feign 调用 auth 校验 AK/SK，
 * 一旦 auth 不可用（连接拒绝 / 超时 / 5xx），整个 opdata 的所有受保护接口都会 500。
 *
 * 本类在 auth 不可达时降级：
 * - 捕获 Feign 网络类异常 ([RetryableException]) 及服务端 5xx ([FeignException.FeignServerException])；
 * - 返回请求头中的 `X-BKREPO-UID`，若无则返回匿名用户；
 * - 业务层后续的权限判断由 opdata 本地的 `UserAuthProvider` 兜底，
 *   保证 auth 故障期间 opdata 仍可用。
 *
 * 注意：仅在 Feign 调用失败时降级；若 auth 明确返回凭证无效（4xx），仍按认证失败处理，
 * 避免放过真正的非法请求。
 */
class ResilientPlatformAuthHandler(
    authenticationManager: AuthenticationManager
) : PlatformAuthHandler(authenticationManager) {

    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        return try {
            super.onAuthenticate(request, authCredentials)
        } catch (e: RetryableException) {
            // 连接拒绝 / 读超时 等网络异常
            logger.warn("Auth service unreachable, fallback to header uid. cause=${e.message}")
            fallbackUser(request)
        } catch (e: FeignException) {
            // 仅对 5xx 降级，4xx 仍认为是凭证非法
            if (e.status() in 500..599 || e.status() == -1) {
                logger.warn("Auth service error ${e.status()}, fallback to header uid. cause=${e.message}")
                fallbackUser(request)
            } else {
                throw e
            }
        }
    }

    private fun fallbackUser(request: HttpServletRequest): String {
        val userId = request.getHeader(AUTH_HEADER_UID).orEmpty().trim()
            .takeIf { it.isNotEmpty() } ?: ANONYMOUS_USER
        request.setAttribute(USER_KEY, userId)
        return userId
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResilientPlatformAuthHandler::class.java)
    }
}
