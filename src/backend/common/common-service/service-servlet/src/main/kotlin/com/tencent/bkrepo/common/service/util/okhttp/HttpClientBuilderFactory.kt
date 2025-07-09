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

package com.tencent.bkrepo.common.service.util.okhttp

import com.tencent.bkrepo.common.service.util.okhttp.CertTrustManager.disableValidationSSLSocketFactory
import com.tencent.bkrepo.common.service.util.okhttp.CertTrustManager.disableValidationTrustManager
import com.tencent.bkrepo.common.service.util.okhttp.CertTrustManager.trustAllHostname
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.internal.threadFactory
import org.springframework.beans.factory.BeanFactory
import org.springframework.cloud.sleuth.instrument.async.TraceableExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * OKHTTP Client工厂方法
 */
object HttpClientBuilderFactory {

    private const val DEFAULT_READ_TIMEOUT_SECONDS = 10 * 1000L
    private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 10 * 1000L

    private val defaultClient by lazy {
        OkHttpClient.Builder()
            .sslSocketFactory(disableValidationSSLSocketFactory, disableValidationTrustManager)
            .hostnameVerifier(trustAllHostname)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS)
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.MILLISECONDS)
            .build()
    }

    fun create(
        certificate: String? = null,
        neverReadTimeout: Boolean = false,
        beanFactory: BeanFactory? = null,
        closeTimeout: Long = 0,
    ): OkHttpClient.Builder {
        return defaultClient.newBuilder()
            .apply {
                certificate?.let {
                    val trustManager = CertTrustManager.createTrustManager(it)
                    val sslSocketFactory = CertTrustManager.createSSLSocketFactory(trustManager)
                    val ssf = if (closeTimeout > 0) {
                        UnsafeSslSocketFactoryImpl(sslSocketFactory, closeTimeout)
                    } else {
                        sslSocketFactory
                    }
                    sslSocketFactory(ssf, trustManager)
                }

                if (neverReadTimeout) {
                    readTimeout(0, TimeUnit.MILLISECONDS)
                }

                writeTimeout(0, TimeUnit.MILLISECONDS)

                beanFactory?.let {
                    val traceableExecutorService = TraceableExecutorService(
                        beanFactory,
                        ThreadPoolExecutor(
                            0,
                            Int.MAX_VALUE,
                            60L,
                            TimeUnit.SECONDS,
                            SynchronousQueue(),
                            threadFactory("OkHttp Dispatcher", false),
                        ),
                    )
                    dispatcher(Dispatcher(traceableExecutorService))
                }
            }
    }
}
