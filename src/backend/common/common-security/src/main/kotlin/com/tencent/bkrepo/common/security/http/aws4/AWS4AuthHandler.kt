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

package com.tencent.bkrepo.common.security.http.aws4

import com.tencent.bkrepo.common.api.constant.AWS4_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.security.util.AWS4AuthUtil
import com.tencent.bkrepo.common.api.exception.AWS4AuthenticationException
import com.tencent.bkrepo.common.security.http.core.HttpAuthHandler
import com.tencent.bkrepo.common.security.http.credentials.AnonymousCredentials
import com.tencent.bkrepo.common.security.http.credentials.HttpAuthCredentials
import com.tencent.bkrepo.common.security.manager.AuthenticationManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import javax.servlet.http.HttpServletRequest

/**
 * AWS4 Http 认证方式
 */
open class AWS4AuthHandler(val authenticationManager: AuthenticationManager) : HttpAuthHandler {

    @Value("\${spring.application.name}")
    private var applicationName: String = "s3"

    override fun extractAuthCredentials(request: HttpServletRequest): HttpAuthCredentials {
        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION).orEmpty()
        return if (authorizationHeader.startsWith(AWS4_AUTH_PREFIX)) {
            try {
                /**
                 * WAS4认证，请求头中只知道accessKey(即用户名),没法知道SecretKey(即密码)，
                 * 用用户名去db中查出密码来，用客户端同样的算法计算签名，
                 * 如果计算的签名与传进来的签名一样，则认证通过
                 */
                var userName = AWS4AuthUtil.getAccessKey(authorizationHeader)
                var password: String? = authenticationManager.findUserPwd(userName)
                    ?: throw AWS4AuthenticationException()
                buildAWS4AuthorizationInfo(request, userName, password!!)
            } catch (exception: Exception) {
                // 认证异常处理
                throw AWS4AuthenticationException(params = arrayOf(
                    request.requestURI.split("?").toTypedArray()[0]
                        .substringAfter("/", "").substringAfter("/"))
                )
            }
        } else AnonymousCredentials()
    }

    @Throws(AWS4AuthenticationException::class)
    override fun onAuthenticate(request: HttpServletRequest, authCredentials: HttpAuthCredentials): String {
        require(authCredentials is AWS4AuthCredentials)
        var flag = AWS4AuthUtil.validAuthorization(authCredentials)
        if (flag) {
            return authCredentials.accessKeyId
        }
        logger.warn("s3 auth fail, request data:$authCredentials")
        return if (flag) authCredentials.accessKeyId else throw AWS4AuthenticationException(
            params = arrayOf(request.requestURI.split("?").toTypedArray()[0]
                .substringAfter("/", "").substringAfter("/"))
        )
    }

    private fun buildAWS4AuthorizationInfo(
        request: HttpServletRequest,
        accessKeyId: String,
        secretAccessKey: String
    ): AWS4AuthCredentials {
        return AWS4AuthCredentials(
            authorization = request.getHeader("Authorization"),
            accessKeyId = accessKeyId,
            secretAccessKey = secretAccessKey,
            requestDate = request.getHeader("x-amz-date"),
            contentHash = request.getHeader("x-amz-content-sha256"),
            //uri = request.requestURI.split("?").toTypedArray()[0],
            uri = "/$applicationName"+request.requestURI.split("?").toTypedArray()[0],
            host = request.getHeader("host"),
            queryString = request.queryString ?: "",
            method = request.method
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AWS4AuthHandler::class.java)
    }
}
