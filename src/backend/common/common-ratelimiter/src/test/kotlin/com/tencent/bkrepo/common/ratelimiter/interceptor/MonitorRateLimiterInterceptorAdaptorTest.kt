package com.tencent.bkrepo.common.ratelimiter.interceptor

import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

/**
 * MonitorRateLimiterInterceptorAdaptor 单元测试
 *
 * 核心验证点：
 *   1. afterLimitCheck(result=true) 调用 collectMetrics
 *   2. afterLimitCheck(result=false) 同样调用 collectMetrics
 *   3. afterLimitCheck(resourceLimit=null) 跳过 collectMetrics（避免空指针）
 *   4. afterLimitCheck 带异常时 collectMetrics 同样被调用
 *   5. beforeLimitCheck 是 no-op，不触发 metrics
 */
class MonitorRateLimiterInterceptorAdaptorTest {

    private val metrics = mock(RateLimiterMetrics::class.java)
    private val adaptor = MonitorRateLimiterInterceptorAdaptor(metrics)

    private val resource = "/test/resource"
    private val limit = ResourceLimit(
        algo = "FIXED_WINDOW",
        resource = "/",
        limitDimension = LimitDimension.URL.name,
        limit = 100,
    )

    // ─── beforeLimitCheck ────────────────────────────────────────────────────────

    @Test
    fun `beforeLimitCheck — is a no-op, does not call metrics`() {
        adaptor.beforeLimitCheck(resource, limit)
        verify(metrics, never()).collectMetrics(any(), any(), any(), any(), any())
    }

    // ─── afterLimitCheck 正常路径 ─────────────────────────────────────────────────

    @Test
    fun `afterLimitCheck — calls collectMetrics with result=true`() {
        adaptor.afterLimitCheck(resource, limit, result = true, e = null)

        verify(metrics).collectMetrics(
            resource = eq(resource),
            result = eq(true),
            e = eq(null),
            dimension = eq(limit.limitDimension),
            permits = eq(1L),
        )
    }

    @Test
    fun `afterLimitCheck — calls collectMetrics with result=false`() {
        adaptor.afterLimitCheck(resource, limit, result = false, e = null)

        verify(metrics).collectMetrics(
            resource = eq(resource),
            result = eq(false),
            e = eq(null),
            dimension = eq(limit.limitDimension),
            permits = eq(1L),
        )
    }

    @Test
    fun `afterLimitCheck — passes exception to collectMetrics`() {
        val ex = RuntimeException("rate limit error")
        adaptor.afterLimitCheck(resource, limit, result = false, e = ex)

        verify(metrics).collectMetrics(
            resource = eq(resource),
            result = eq(false),
            e = eq(ex),
            dimension = eq(limit.limitDimension),
            permits = eq(1L),
        )
    }

    // ─── afterLimitCheck 边界：resourceLimit=null ─────────────────────────────────

    @Test
    fun `afterLimitCheck — skips collectMetrics when resourceLimit is null`() {
        adaptor.afterLimitCheck(resource, resourceLimit = null, result = true, e = null)

        verify(metrics, never()).collectMetrics(any(), any(), any(), any(), any())
    }

    @Test
    fun `afterLimitCheck — skips collectMetrics when resourceLimit is null even with exception`() {
        val ex = RuntimeException("error")
        adaptor.afterLimitCheck(resource, resourceLimit = null, result = false, e = ex)

        verify(metrics, never()).collectMetrics(any(), any(), any(), any(), any())
    }
}
