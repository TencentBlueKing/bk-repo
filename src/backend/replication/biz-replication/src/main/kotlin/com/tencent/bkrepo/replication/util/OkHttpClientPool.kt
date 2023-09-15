/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.replication.replica.base.interceptor.RequestTimeOutInterceptor
import com.tencent.bkrepo.replication.replica.base.interceptor.progress.ProgressInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * OkHttpClient池，提供OkHttpClient复用
 * */
object OkHttpClientPool {
    private val clientCache = ConcurrentHashMap<ClusterInfo, OkHttpClient>()
    fun getHttpClient(
        timoutCheckHosts: List<Map<String, String>>,
        clusterInfo: ClusterInfo,
        readTimeout: Duration,
        writeTimeout: Duration,
        closeTimeout: Duration = Duration.ZERO,
        vararg interceptors: Interceptor
    ): OkHttpClient {
        return clientCache.getOrPut(clusterInfo) {
            val builder = HttpClientBuilderFactory.create(
                clusterInfo.certificate,
                closeTimeout = closeTimeout.seconds,
            ).protocols(listOf(Protocol.HTTP_1_1))
                .readTimeout(readTimeout)
                .writeTimeout(writeTimeout)
            interceptors.forEach {
                builder.addInterceptor(
                    it,
                )
            }
            builder.addNetworkInterceptor(ProgressInterceptor())
            builder.addNetworkInterceptor(RequestTimeOutInterceptor(timoutCheckHosts))
            builder.build()
        }
    }
}
