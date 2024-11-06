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

package com.tencent.bkrepo.s3.artifact.auth

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.security.http.core.HttpAuthHandler
import com.tencent.bkrepo.common.security.http.credentials.AnonymousCredentials
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import com.tencent.bkrepo.s3.artifact.utils.AWS4AuthUtil
import com.tencent.bkrepo.s3.constant.AWS4_AUTH_PREFIX
import com.tencent.bkrepo.s3.constant.S3HttpHeaders
import com.tencent.bkrepo.s3.constant.SIGN_NOT_MATCH
import com.tencent.bkrepo.s3.exception.AWS4AuthenticationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import javax.servlet.http.HttpServletRequest

/**
 * AWS4 Http 认证方式
 */
class AWS4AuthHandler(
    authenticationManager: AuthenticationManager
) : HttpAuthHandler {

    @Value("\${spring.application.name}")
    private val applicationName: String = "s3"

    private val authValidator = AbstractAuthValidator.getAuthValidator(authenticationManager)

    override fun extractAuthCredentials(request: HttpServletRequest): HttpAuthCredentials {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION).orEmpty()
        return if (authorizationHeader.startsWith(AWS4_AUTH_PREFIX)) {
            try {
                /**
                 * WAS4认证，请求头中只知道accessKey(即用户名),没法知道SecretKey(即密码)，
                 * 用用户名去db中查出密码来，用客户端同样的算法计算签名，
                 * 如果计算的签名与传进来的签名一样，则认证通过
                 */
                val userName = AWS4AuthUtil.getAccessKey(authorizationHeader)
                buildAWS4AuthorizationInfo(request, userName)
            } catch (exception: Exception) {
                // 认证异常处理
                throw AWS4AuthenticationException(
                    params = arrayOf(SIGN_NOT_MATCH, getRequestResource(request))
                )
            }
        } else AnonymousCredentials()
    }

    @Throws(AWS4AuthenticationException::class)
    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        require(authCredentials is AWS4AuthCredentials)
        val pass = authValidator.validate(authCredentials)
        if (pass) {
            return authCredentials.accessKeyId
        }
        logger.warn("s3 auth fail, request data:$authCredentials")
        throw AWS4AuthenticationException(
            params = arrayOf(SIGN_NOT_MATCH, getRequestResource(request))
        )
    }

    private fun buildAWS4AuthorizationInfo(
        request: HttpServletRequest,
        accessKeyId: String
    ): AWS4AuthCredentials {
        return AWS4AuthCredentials(
            authorization = request.getHeader(HttpHeaders.AUTHORIZATION) ?: "",
            accessKeyId = accessKeyId,
            requestDate = request.getHeader(S3HttpHeaders.X_AMZ_DATE) ?: "",
            contentHash = request.getHeader(S3HttpHeaders.X_AMZ_CONTENT_SHA256) ?: "",
//            uri = request.requestURI.split("?").toTypedArray()[0],
            uri = "/$applicationName"+request.requestURI.split("?").toTypedArray()[0],
            host = request.getHeader(HttpHeaders.HOST) ?: "",
            queryString = request.queryString ?: "",
            method = request.method
        )
    }

    /**
     * 提取请求路径中的resource,即bucket后面的部分, /a/b/c/d -> /c/d
     */
    private fun getRequestResource(request: HttpServletRequest): String{
        val path = request.requestURI.split("?").toTypedArray()[0]
        val components = path.trim('/').split('/')
        return when {
            path == "/" || path.startsWith(applicationName) -> ""
            components.size <= 2 -> ""
            else -> "/" + components.takeLast(2).joinToString("/")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AWS4AuthHandler::class.java)
    }
}
