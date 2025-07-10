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

package com.tencent.bkrepo.common.ratelimiter.metrics

import com.tencent.bkrepo.common.ratelimiter.constant.RATE_LIMITER_EXCEPTION_COUNT
import com.tencent.bkrepo.common.ratelimiter.constant.RATE_LIMITER_EXCEPTION_COUNT_DESC
import com.tencent.bkrepo.common.ratelimiter.constant.RATE_LIMITER_LIMITED_COUNT
import com.tencent.bkrepo.common.ratelimiter.constant.RATE_LIMITER_LIMITED_COUNT_DESC
import com.tencent.bkrepo.common.ratelimiter.constant.RATE_LIMITER_PASSED_COUNT
import com.tencent.bkrepo.common.ratelimiter.constant.RATE_LIMITER_PASSED_COUNT_DESC
import com.tencent.bkrepo.common.ratelimiter.constant.RATE_LIMITER_TOTAL_COUNT
import com.tencent.bkrepo.common.ratelimiter.constant.RATE_LIMITER_TOTAL_COUNT_DESC
import com.tencent.bkrepo.common.ratelimiter.constant.TAG_NAME
import com.tencent.bkrepo.common.ratelimiter.constant.TAG_STATUS
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

/**
 * 限流指标写入
 */
class RateLimiterMetrics(private val registry: MeterRegistry) {

    fun collectMetrics(
        resource: String, result: Boolean, e: Exception?
    ) {
        try {
            getTotalCounter(resource).increment()
            if (result) {
                getPassedCounter(resource).increment()
            } else {
                getLimitedCounter(resource).increment()
            }
            if (e != null) {
                getExceptionCounter(resource).increment()
            }
        } catch (ignore: Exception) {
        }

    }

    private fun getTotalCounter(resource: String): Counter {
        return getMetricsCount(
            RATE_LIMITER_TOTAL_COUNT, RATE_LIMITER_TOTAL_COUNT_DESC, MetricType.TOTAL.name, resource
        )
    }

    private fun getPassedCounter(resource: String): Counter {
        return getMetricsCount(
            RATE_LIMITER_PASSED_COUNT, RATE_LIMITER_PASSED_COUNT_DESC, MetricType.PASSED.name, resource
        )
    }

    private fun getLimitedCounter(resource: String): Counter {
        return getMetricsCount(
            RATE_LIMITER_LIMITED_COUNT, RATE_LIMITER_LIMITED_COUNT_DESC, MetricType.LIMITED.name, resource
        )
    }

    private fun getExceptionCounter(resource: String): Counter {
        return getMetricsCount(
            RATE_LIMITER_EXCEPTION_COUNT, RATE_LIMITER_EXCEPTION_COUNT_DESC, MetricType.EXCEPTION.name, resource
        )
    }

    private fun getMetricsCount(metricsName: String, metricsDes: String, status: String, resource: String): Counter {
        return Counter.builder(metricsName)
            .description(metricsDes)
            .tag(TAG_STATUS, status)
            .tag(TAG_NAME, resource)
            .register(registry)
    }
}
