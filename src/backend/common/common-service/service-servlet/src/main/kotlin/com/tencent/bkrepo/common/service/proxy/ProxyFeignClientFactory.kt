/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.common.api.util.okhttp.CertTrustManager
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import feign.Client
import feign.Feign
import feign.Logger
import org.springframework.cloud.openfeign.FeignLoggerFactory

object ProxyFeignClientFactory {

    val clientCacheMap = mutableMapOf<Class<*>, Any>()

    inline fun <reified T> create(serviceName: String): T {
        val gateway = ProxyEnv.getGateway()
        val url = FeignClientFactory.normalizeUrl(gateway, serviceName)
        return clientCacheMap.getOrPut(T::class.java) {
            Feign.builder().logLevel(Logger.Level.BASIC)
                .logger(SpringContextUtils.getBean<FeignLoggerFactory>().create(T::class.java))
                .client(
                    Client.Default(
                        CertTrustManager.disableValidationSSLSocketFactory,
                        CertTrustManager.trustAllHostname
                    )
                )
                .requestInterceptor(ProxyRequestInterceptor())
                .encoder(SpringContextUtils.getBean())
                .decoder(SpringContextUtils.getBean())
                .contract(SpringContextUtils.getBean())
                .retryer(SpringContextUtils.getBean())
                .options(FeignClientFactory.options)
                .errorDecoder(SpringContextUtils.getBean())
                .target(T::class.java, url) as Any
        } as T
    }
}
