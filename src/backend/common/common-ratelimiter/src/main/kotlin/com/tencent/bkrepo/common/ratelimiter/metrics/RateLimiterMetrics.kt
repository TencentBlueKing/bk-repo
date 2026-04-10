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

import com.tencent.bkrepo.common.metrics.constant.CONNECTION_ACCEPTED_COUNT
import com.tencent.bkrepo.common.metrics.constant.CONNECTION_ACCEPTED_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.CONNECTION_ACTIVE
import com.tencent.bkrepo.common.metrics.constant.CONNECTION_ACTIVE_DESC
import com.tencent.bkrepo.common.metrics.constant.CONNECTION_DURATION
import com.tencent.bkrepo.common.metrics.constant.CONNECTION_DURATION_DESC
import com.tencent.bkrepo.common.metrics.constant.CONNECTION_MAX
import com.tencent.bkrepo.common.metrics.constant.CONNECTION_MAX_DESC
import com.tencent.bkrepo.common.metrics.constant.CONNECTION_REJECTED_COUNT
import com.tencent.bkrepo.common.metrics.constant.CONNECTION_REJECTED_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.CONNECTION_USAGE
import com.tencent.bkrepo.common.metrics.constant.CONNECTION_USAGE_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_CHECK_DURATION
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_CHECK_DURATION_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_DEGRADED_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_DEGRADED_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_DIMENSION_LIMITED_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_DIMENSION_LIMITED_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_DIMENSION_PASSED_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_DIMENSION_PASSED_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_DIMENSION_PASS_RATE
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_DIMENSION_PASS_RATE_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_DIMENSION_TOTAL_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_DIMENSION_TOTAL_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_EXCEPTION_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_EXCEPTION_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_LIMITED_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_LIMITED_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_OVERALL_LIMIT_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_OVERALL_LIMIT_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_OVERALL_PASS_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_OVERALL_PASS_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_OVERALL_PASS_RATE
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_OVERALL_PASS_RATE_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_PASSED_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_PASSED_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_PASSED_PERMITS
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_PASSED_PERMITS_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_REJECT_PERMITS
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_REJECT_PERMITS_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_RESOURCE_LIMITED_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_RESOURCE_LIMITED_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_RESOURCE_PASSED_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_RESOURCE_PASSED_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_RESOURCE_PASS_RATE
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_RESOURCE_PASS_RATE_DESC
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_TOTAL_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_TOTAL_COUNT_DESC
import com.tencent.bkrepo.common.metrics.constant.TAG_DIMENSION
import com.tencent.bkrepo.common.metrics.constant.TAG_NAME
import com.tencent.bkrepo.common.metrics.constant.TAG_REASON
import com.tencent.bkrepo.common.metrics.constant.TAG_RESOURCE
import com.tencent.bkrepo.common.metrics.constant.TAG_STATUS
import com.tencent.bkrepo.common.ratelimiter.service.connection.ServiceInstanceConnectionLimiterService
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.ObjectProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 限流指标写入
 */
class RateLimiterMetrics(
    private val registry: MeterRegistry,
    connectionLimiterServiceProvider: ObjectProvider<ServiceInstanceConnectionLimiterService>? = null
) {

    private val connectionLimiterService by lazy {
        connectionLimiterServiceProvider?.getObject()
    }

    // 连接拒绝计数
    private val rejectedCounter: Counter = Counter.builder(CONNECTION_REJECTED_COUNT)
        .description(CONNECTION_REJECTED_COUNT_DESC)
        .register(registry)

    // 连接接受计数
    private val acceptedCounter: Counter = Counter.builder(CONNECTION_ACCEPTED_COUNT)
        .description(CONNECTION_ACCEPTED_COUNT_DESC)
        .register(registry)

    // 连接处理时长
    private val connectionDurationTimer: Timer = Timer.builder(CONNECTION_DURATION)
        .description(CONNECTION_DURATION_DESC)
        .register(registry)

    // 各维度限流资源的实时状态
    private val resourcePassRateMap = ConcurrentHashMap<String, AtomicLong>()
    private val resourceLimitRateMap = ConcurrentHashMap<String, AtomicLong>()
    private val dimensionStatsMap = ConcurrentHashMap<String, DimensionStats>()

    init {
        connectionLimiterServiceProvider?.let { registerConnectionGauges() }

        // 注册汇总级别的gauge
        Gauge.builder(RATE_LIMITER_OVERALL_PASS_COUNT) {
            resourcePassRateMap.values.sumOf { it.get() }.toDouble()
        }
            .description(RATE_LIMITER_OVERALL_PASS_COUNT_DESC)
            .register(registry)

        Gauge.builder(RATE_LIMITER_OVERALL_LIMIT_COUNT) {
            resourceLimitRateMap.values.sumOf { it.get() }.toDouble()
        }
            .description(RATE_LIMITER_OVERALL_LIMIT_COUNT_DESC)
            .register(registry)

        Gauge.builder(RATE_LIMITER_OVERALL_PASS_RATE) {
            val total = resourcePassRateMap.values.sumOf { it.get() } +
                resourceLimitRateMap.values.sumOf { it.get() }
            if (total > 0) {
                resourcePassRateMap.values.sumOf { it.get() }.toDouble() / total
            } else 0.0
        }
            .description(RATE_LIMITER_OVERALL_PASS_RATE_DESC)
            .baseUnit("percent")
            .register(registry)
    }

    fun collectMetrics(
        resource: String,
        result: Boolean,
        e: Exception?,
        dimension: String? = null,
        permits: Long = 1L
    ) {
        try {
            getTotalCounter(resource, dimension).increment()
            if (result) {
                getPassedCounter(resource, dimension).increment(permits.toDouble())
                resourcePassRateMap.computeIfAbsent(resource) { AtomicLong(0) }.addAndGet(permits)
            } else {
                getLimitedCounter(resource, dimension).increment()
                resourceLimitRateMap.computeIfAbsent(resource) { AtomicLong(0) }.incrementAndGet()
            }
            if (e != null) {
                getExceptionCounter(resource, dimension).increment()
            }

            // 维度级别统计
            dimension?.let { updateDimensionStats(it, result, permits) }

            // 注册资源级别的实时通过率gauge
            registerResourceGauges(resource)
        } catch (ignore: Exception) {
        }
    }

    private fun updateDimensionStats(dimension: String, passed: Boolean, permits: Long) {
        val stats = dimensionStatsMap.computeIfAbsent(dimension) {
            val newStats = DimensionStats()
            registerDimensionGauges(dimension, newStats)
            newStats
        }
        if (passed) {
            stats.passedCount.addAndGet(permits)
        } else {
            stats.limitedCount.incrementAndGet()
        }
        stats.totalCount.incrementAndGet()
    }

    private fun registerDimensionGauges(dimension: String, stats: DimensionStats) {
        Gauge.builder(RATE_LIMITER_DIMENSION_PASSED_COUNT) { stats.passedCount.get().toDouble() }
            .description(RATE_LIMITER_DIMENSION_PASSED_COUNT_DESC)
            .tag(TAG_DIMENSION, dimension)
            .register(registry)

        Gauge.builder(RATE_LIMITER_DIMENSION_LIMITED_COUNT) { stats.limitedCount.get().toDouble() }
            .description(RATE_LIMITER_DIMENSION_LIMITED_COUNT_DESC)
            .tag(TAG_DIMENSION, dimension)
            .register(registry)

        Gauge.builder(RATE_LIMITER_DIMENSION_TOTAL_COUNT) { stats.totalCount.get().toDouble() }
            .description(RATE_LIMITER_DIMENSION_TOTAL_COUNT_DESC)
            .tag(TAG_DIMENSION, dimension)
            .register(registry)

        Gauge.builder(RATE_LIMITER_DIMENSION_PASS_RATE) {
            val total = stats.totalCount.get()
            if (total > 0) stats.passedCount.get().toDouble() / total else 0.0
        }
            .description(RATE_LIMITER_DIMENSION_PASS_RATE_DESC)
            .tag(TAG_DIMENSION, dimension)
            .baseUnit("percent")
            .register(registry)
    }

    private fun registerResourceGauges(resource: String) {
        if (!resourcePassRateMap.containsKey(resource)) {
            Gauge.builder(RATE_LIMITER_RESOURCE_PASSED_COUNT) {
                resourcePassRateMap[resource]?.get()?.toDouble() ?: 0.0
            }
                .description(RATE_LIMITER_RESOURCE_PASSED_COUNT_DESC)
                .tag(TAG_NAME, resource)
                .register(registry)
        }

        if (!resourceLimitRateMap.containsKey(resource)) {
            Gauge.builder(RATE_LIMITER_RESOURCE_LIMITED_COUNT) {
                resourceLimitRateMap[resource]?.get()?.toDouble() ?: 0.0
            }
                .description(RATE_LIMITER_RESOURCE_LIMITED_COUNT_DESC)
                .tag(TAG_NAME, resource)
                .register(registry)

            Gauge.builder(RATE_LIMITER_RESOURCE_PASS_RATE) {
                val passed = resourcePassRateMap[resource]?.get() ?: 0L
                val limited = resourceLimitRateMap[resource]?.get() ?: 0L
                val total = passed + limited
                if (total > 0) passed.toDouble() / total else 0.0
            }
                .description(RATE_LIMITER_RESOURCE_PASS_RATE_DESC)
                .tag(TAG_NAME, resource)
                .baseUnit("percent")
                .register(registry)
        }
    }

    private fun getTotalCounter(resource: String, dimension: String? = null): Counter {
        return getMetricsCount(
            RATE_LIMITER_TOTAL_COUNT, RATE_LIMITER_TOTAL_COUNT_DESC, MetricType.TOTAL.name, resource, dimension
        )
    }

    private fun getPassedCounter(resource: String, dimension: String? = null): Counter {
        return getMetricsCount(
            RATE_LIMITER_PASSED_COUNT, RATE_LIMITER_PASSED_COUNT_DESC, MetricType.PASSED.name, resource, dimension
        )
    }

    private fun getLimitedCounter(resource: String, dimension: String? = null): Counter {
        return getMetricsCount(
            RATE_LIMITER_LIMITED_COUNT, RATE_LIMITER_LIMITED_COUNT_DESC, MetricType.LIMITED.name, resource, dimension
        )
    }

    private fun getExceptionCounter(resource: String, dimension: String? = null): Counter {
        return getMetricsCount(
            RATE_LIMITER_EXCEPTION_COUNT, RATE_LIMITER_EXCEPTION_COUNT_DESC,
            MetricType.EXCEPTION.name, resource, dimension
        )
    }

    private fun getMetricsCount(
        metricsName: String,
        metricsDes: String,
        status: String,
        resource: String,
        dimension: String? = null
    ): Counter {
        val builder = Counter.builder(metricsName)
            .description(metricsDes)
            .tag(TAG_STATUS, status)
            .tag(TAG_NAME, resource)
        dimension?.let { builder.tag(TAG_DIMENSION, it) }
        return builder.register(registry)
    }

    private data class DimensionStats(
        val passedCount: AtomicLong = AtomicLong(0),
        val limitedCount: AtomicLong = AtomicLong(0),
        val totalCount: AtomicLong = AtomicLong(0)
    )

    private fun registerConnectionGauges() {
        // 当前活跃连接数
        Gauge.builder(CONNECTION_ACTIVE) {
            connectionLimiterService?.getCurrentConnections() ?: 0
        }
            .description(CONNECTION_ACTIVE_DESC)
            .register(registry)

        // 最大连接数配置
        Gauge.builder(CONNECTION_MAX) {
            connectionLimiterService?.getMaxConnectionsConfig() ?: Int.MAX_VALUE
        }
            .description(CONNECTION_MAX_DESC)
            .register(registry)

        // 连接使用率
        Gauge.builder(CONNECTION_USAGE) {
            connectionLimiterService?.getConnectionUsageRate() ?: 0.0
        }
            .description(CONNECTION_USAGE_DESC)
            .baseUnit("percent")
            .register(registry)
    }

    /**
     * 记录连接被拒绝
     */
    fun recordConnectionRejected() {
        rejectedCounter.increment()
    }

    /**
     * 记录连接被接受
     */
    fun recordConnectionAccepted() {
        acceptedCounter.increment()
    }

    /**
     * 记录连接处理时长
     */
    fun recordConnectionDuration(startTimeNanos: Long) {
        val duration = System.nanoTime() - startTimeNanos
        connectionDurationTimer.record(duration, TimeUnit.NANOSECONDS)
    }

    /**
     * 记录限流拒绝详情
     */
    fun recordLimitRejectDetail(dimension: String, resource: String, permits: Long) {
        getOrCreateRejectPermitsSummary(dimension, resource).record(permits.toDouble())
    }

    /**
     * 记录限流通过的许可数
     */
    fun recordPassedPermits(dimension: String, resource: String, permits: Long) {
        getOrCreatePassedPermitsSummary(dimension, resource).record(permits.toDouble())
    }

    /**
     * 记录限流器响应时间
     */
    fun recordLimiterResponseTime(dimension: String, durationNanos: Long) {
        getOrCreateResponseTimer(dimension).record(durationNanos, TimeUnit.NANOSECONDS)
    }

    /**
     * 记录限流降级
     */
    fun recordDegraded(dimension: String, reason: String) {
        Counter.builder(RATE_LIMITER_DEGRADED_COUNT)
            .description(RATE_LIMITER_DEGRADED_COUNT_DESC)
            .tag(TAG_DIMENSION, dimension)
            .tag(TAG_REASON, reason)
            .register(registry)
            .increment()
    }

    private fun getOrCreateRejectPermitsSummary(dimension: String, resource: String): DistributionSummary {
        return DistributionSummary.builder(RATE_LIMITER_REJECT_PERMITS)
            .description(RATE_LIMITER_REJECT_PERMITS_DESC)
            .tag(TAG_DIMENSION, dimension)
            .tag(TAG_RESOURCE, resource)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
    }

    private fun getOrCreatePassedPermitsSummary(dimension: String, resource: String): DistributionSummary {
        return DistributionSummary.builder(RATE_LIMITER_PASSED_PERMITS)
            .description(RATE_LIMITER_PASSED_PERMITS_DESC)
            .tag(TAG_DIMENSION, dimension)
            .tag(TAG_RESOURCE, resource)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
    }

    private fun getOrCreateResponseTimer(dimension: String): Timer {
        return Timer.builder(RATE_LIMITER_CHECK_DURATION)
            .description(RATE_LIMITER_CHECK_DURATION_DESC)
            .tag(TAG_DIMENSION, dimension)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
    }
}
