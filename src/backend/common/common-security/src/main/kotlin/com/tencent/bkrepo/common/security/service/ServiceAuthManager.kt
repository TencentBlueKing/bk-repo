/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
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

package com.tencent.bkrepo.common.security.service

import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.security.util.JwtUtils
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.concurrent.thread

@Component // 使用kotlin时，spring aop对@Import导入的bean不生效
class ServiceAuthManager(
    properties: ServiceAuthProperties,
) {

    private val signingKey = JwtUtils.createSigningKey(properties.secretKey)

    private var token: String = generateSecurityToken()

    init {
        // 使用单独的一个线程刷新token，防止被其他业务影响。
        thread(isDaemon = true, name = REFRESH_THREAD_NAME) {
            while (true) {
                try {
                    refreshSecurityToken()
                    Thread.sleep(REFRESH_DELAY)
                } catch (e: Exception) {
                    logger.error("Refresh token failed", e)
                }
            }
        }
    }

    fun getSecurityToken(): String {
        return token
    }

    fun verifySecurityToken(token: String) {
        try {
            JwtUtils.validateToken(signingKey, token)
        } catch (exception: ExpiredJwtException) {
            throw SystemErrorException(CommonMessageCode.SERVICE_UNAUTHENTICATED, "Expired token")
        } catch (exception: JwtException) {
            throw SystemErrorException(CommonMessageCode.SERVICE_UNAUTHENTICATED, "Invalid token")
        } catch (exception: IllegalArgumentException) {
            throw SystemErrorException(CommonMessageCode.SERVICE_UNAUTHENTICATED, "Empty token")
        }
    }

    fun refreshSecurityToken() {
        logger.info("Refreshing security token")
        token = generateSecurityToken()
    }

    private fun generateSecurityToken(): String {
        return JwtUtils.generateToken(signingKey, Duration.ofMillis(TOKEN_EXPIRATION))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServiceAuthManager::class.java)
        private const val TOKEN_EXPIRATION = 10 * 60 * 1000L
        private const val REFRESH_DELAY = TOKEN_EXPIRATION - 60 * 1000L
        private const val REFRESH_THREAD_NAME = "ms-token-refresh"
    }
}
