/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.security.proxy

import cn.hutool.crypto.CryptoException
import com.google.common.hash.Hashing
import com.tencent.bkrepo.auth.api.ServiceProxyClient
import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.constant.ensurePrefix
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.common.security.util.AESUtils
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.service.util.HttpSigner
import com.tencent.bkrepo.common.service.util.HttpSigner.PROJECT_ID
import com.tencent.bkrepo.common.service.util.HttpSigner.PROXY_NAME
import com.tencent.bkrepo.common.service.util.HttpSigner.SIGN
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import org.apache.commons.codec.digest.HmacAlgorithms
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.servlet.AsyncHandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import java.io.ByteArrayOutputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ProxyAuthInterceptor(
    private val proxyAuthProperties: ProxyAuthProperties
) : AsyncHandlerInterceptor {

    private val serviceProxyClient: ServiceProxyClient by lazy { SpringContextUtils.getBean() }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // 设置uid
        request.getHeader(MS_AUTH_HEADER_UID)?.let {
            request.setAttribute(USER_KEY, it)
        }
        if (proxyAuthProperties.enabled) {
            val projectId = request.getParameter(PROJECT_ID)
            val name = request.getParameter(PROXY_NAME)
            if (projectId.isNullOrBlank()) {
                throw AuthenticationException("miss projectId")
            }
            if (name.isNullOrBlank()) {
                throw AuthenticationException("miss name")
            }
            try {
                val proxyKey = serviceProxyClient.getEncryptedKey(projectId, name).data!!
                val secretKey = AESUtils.decrypt(proxyKey.encSecretKey)
                val sessionKey = AESUtils.decrypt(proxyKey.encSessionKey, secretKey)

                val uri = getUrlPath(request)
                val bodyHash = if (request.contentType?.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE) == false &&
                    request.contentType?.startsWith(MediaType.APPLICATION_OCTET_STREAM_VALUE) == false
                ) {
                    val body = ByteArrayOutputStream()
                    request.inputStream.copyTo(body)
                    Hashing.sha256().hashBytes(body.toByteArray()).toString()
                } else {
                    emptyStringHash
                }
                val sig = HttpSigner.sign(request, uri, bodyHash, sessionKey, HmacAlgorithms.HMAC_SHA_1.getName())
                if (sig != request.getParameter(SIGN)) {
                    val signatureStr = HttpSigner.getSignatureStr(request, uri, bodyHash)
                    throw AuthenticationException("Invalid signature, server signature string: $signatureStr")
                }
            } catch (e: RemoteErrorCodeException) {
                logger.error("proxy auth error: ", e)
                throw AuthenticationException(e.localizedMessage)
            } catch (e: CryptoException) {
                logger.error("proxy auth crypto error: ", e)
                throw AuthenticationException(e.localizedMessage)
            }
        }
        return true
    }

    private fun getUrlPath(request: HttpServletRequest): String {
        return request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString().ensurePrefix("/")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProxyAuthInterceptor::class.java)
        private val emptyStringHash = Hashing.sha256().hashBytes(StringPool.EMPTY.toByteArray()).toString()
    }
}
