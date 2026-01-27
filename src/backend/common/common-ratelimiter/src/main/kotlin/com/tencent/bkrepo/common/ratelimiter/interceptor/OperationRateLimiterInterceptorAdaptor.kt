package com.tencent.bkrepo.common.ratelimiter.interceptor

import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit

/**
 * 限流运营指标采集拦截器
 * 收集更细粒度的运营数据
 */
class OperationRateLimiterInterceptorAdaptor(
    private val rateLimiterMetrics: RateLimiterMetrics
) : RateLimiterInterceptorAdapter() {

    private val checkStartTime = ThreadLocal<Long>()

    override fun beforeLimitCheck(resource: String, resourceLimit: ResourceLimit) {
        checkStartTime.set(System.nanoTime())
    }

    override fun afterLimitCheck(
        resource: String,
        resourceLimit: ResourceLimit?,
        result: Boolean,
        e: Exception?
    ) {
        if (resourceLimit == null) return

        // 记录响应时间
        val startTime = checkStartTime.get()
        if (startTime != null) {
            val duration = System.nanoTime() - startTime
            rateLimiterMetrics.recordLimiterResponseTime(resourceLimit.limitDimension, duration)
            checkStartTime.remove()
        }

        // 记录通过或拒绝的许可数（默认为1）
        val permits = 1L
        if (result) {
            rateLimiterMetrics.recordPassedPermits(resourceLimit.limitDimension, resource, permits)
        } else {
            rateLimiterMetrics.recordLimitRejectDetail(resourceLimit.limitDimension, resource, permits)
        }
    }
}



