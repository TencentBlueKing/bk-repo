/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.base.interceptor

import com.tencent.bkrepo.common.api.net.speedtest.Counter.Companion.MB
import com.tencent.bkrepo.replication.pojo.blob.RequestTag
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.AsyncTimeout
import org.slf4j.LoggerFactory
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

class RequestTimeOutInterceptor(
    private val timoutCheckHosts: List<Map<String, String>>
): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enableTimeOutCheck(chain.request())) return chain.proceed(chain.request())
        val request = chain.request()
        val tag = request.tag(RequestTag::class.java) ?: return chain.proceed(request)
        val estimatedTime = getEstimatedTime(tag, request.url.host)
        if (estimatedTime <= 0) return chain.proceed(request)
        return sendRequest(chain, estimatedTime)
    }


    private fun enableTimeOutCheck(request: Request) : Boolean {
        return timoutCheckHosts.any { it[HOST_KEY] == request.url.host }
    }

    private fun getEstimatedTime(tag: RequestTag, host: String): Double {
        val rate = timoutCheckHosts.firstOrNull { it[HOST_KEY] == host }
            ?.get(AVERAGE_RATE_KEY)?.toDouble() ?: return 0.0
        val estimatedTime = if (tag.size <= SPECIAL_TIME_COST*MB*rate) {
            SPECIAL_TIME_COST
        } else {
            (tag.size/MB/rate)
        } * 1.5
        logger.info("Task ${tag.key} maybe will cost $estimatedTime seconds to transfer, size is ${tag.size}")
        return estimatedTime
    }

    private fun sendRequest(chain: Interceptor.Chain, estimatedTime: Double): Response {
        var response: Response? = null
        logger.info("Will send request to the remote host")
        val requestTimeout = object : AsyncTimeout() {
            override fun timedOut() {
                try {
                    logger.info("Will try to close socket ${chain.connection()?.socket()}")
                    chain.connection()?.socket()?.close()
                } catch (e: Exception) {
                    logger.warn("Close socket exception while handling request timeout: $e")
                }
            }
        }.apply {
            timeout(estimatedTime.toLong(), TimeUnit.SECONDS)
        }
        try {
            requestTimeout.withTimeout {
                response = chain.proceed(chain.request())
            }
        } catch (e: Exception) {
            logger.warn("Request timeout exception: $e")
            if (e is InterruptedIOException) {
                throw RuntimeException("RequestTimeOut after $estimatedTime seconds")
            } else
                throw e
        }
        return response!!
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RequestTimeOutInterceptor::class.java)
        private const val SPECIAL_TIME_COST: Double = 60.0
        private const val HOST_KEY = "host"
        private const val AVERAGE_RATE_KEY = "rate"
    }
}

