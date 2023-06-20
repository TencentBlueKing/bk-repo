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

package com.tencent.bkrepo.common.service.proxy

import com.google.common.hash.Hashing
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MS_AUTH_HEADER_UID
import com.tencent.bkrepo.common.api.constant.PROXY_HEADER_NAME
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.constant.urlEncode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.HttpSigner
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.service.util.okhttp.CertTrustManager
import feign.Client
import feign.Feign
import feign.Logger
import feign.RequestInterceptor
import org.apache.commons.codec.digest.HmacAlgorithms
import org.springframework.cloud.openfeign.FeignLoggerFactory

object ProxyFeignClientFactory {

    val clientCacheMap = mutableMapOf<Class<*>, Any>()

    inline fun <reified T> create(serviceName: String): T {
        val gateway = ProxyEnv.getGateway()
//        val url = FeignClientFactory.normalizeUrl(gateway, serviceName)
        val url = when (serviceName) {
            "auth" -> "http://localhost:25902"
            "repository" -> "http://localhost:25901"
            else -> ""
        }
        return clientCacheMap.getOrPut(T::class.java) {
            Feign.builder().logLevel(Logger.Level.BASIC)
                .logger(SpringContextUtils.getBean<FeignLoggerFactory>().create(T::class.java))
                .client(
                    Client.Default(
                        CertTrustManager.disableValidationSSLSocketFactory,
                        CertTrustManager.trustAllHostname
                    )
                )
                .requestInterceptor(createInterceptor())
                .encoder(SpringContextUtils.getBean())
                .decoder(SpringContextUtils.getBean())
                .contract(SpringContextUtils.getBean())
                .retryer(SpringContextUtils.getBean())
                .options(FeignClientFactory.options)
                .errorDecoder(SpringContextUtils.getBean())
                .target(T::class.java, url) as Any
        } as T
    }

    fun createInterceptor(): RequestInterceptor {
        return RequestInterceptor {
            val projectId = ProxyEnv.getProjectId()
            val name = ProxyEnv.getName()
            it.header(PROXY_HEADER_NAME, name)
            HeaderUtils.getHeader(HttpHeaders.ACCEPT_LANGUAGE)?.let { lang ->
                it.header(HttpHeaders.ACCEPT_LANGUAGE, lang)
            }
            HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY)?.let { userId ->
                it.header(MS_AUTH_HEADER_UID, userId.toString())
            } ?: it.header(MS_AUTH_HEADER_UID, "system")

            val sessionKey: String
            try {
                sessionKey = SessionKeyHolder.getSessionKey()
            } catch (e: ErrorCodeException) {
                // 获取不到sessionKey时的请求不需要签名
                return@RequestInterceptor
            }
            // 不要使用feign请求来上传文件，所以这里不存在文件请求，可以完全读取body进行签名
            val bodyToHash = if (it.body() != null && it.body().isNotEmpty()) {
                it.body()
            } else {
                StringPool.EMPTY.toByteArray()
            }
            val algorithm = HmacAlgorithms.HMAC_SHA_1.getName()
            val startTime = System.currentTimeMillis() / HttpSigner.MILLIS_PER_SECOND
            val endTime = startTime + HttpSigner.REQUEST_TTL
            it.query(HttpSigner.PROXY_NAME, name)
                .query(HttpSigner.PROJECT_ID, projectId)
                .query(HttpSigner.SIGN_TIME, "$startTime${HttpSigner.TIME_SPLIT}$endTime".urlEncode())
                .query(HttpSigner.SIGN_ALGORITHM, algorithm)
            val bodyHash = Hashing.sha256().hashBytes(bodyToHash).toString()
            val sig = HttpSigner.sign(it, bodyHash, sessionKey, algorithm)
            it.query(HttpSigner.SIGN, sig)
        }
    }
}
