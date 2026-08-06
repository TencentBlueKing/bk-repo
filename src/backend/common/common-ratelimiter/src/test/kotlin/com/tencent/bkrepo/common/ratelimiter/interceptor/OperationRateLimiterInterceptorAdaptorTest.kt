package com.tencent.bkrepo.common.ratelimiter.interceptor

import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

/**
 * OperationRateLimiterInterceptorAdaptor 单元测试
 *
 * 核心验证点：
 *   1. beforeLimitCheck 记录 ThreadLocal 开始时间
 *   2. afterLimitCheck 清理 ThreadLocal（防止内存泄漏），并记录响应时间
 *   3. result=true → recordPassedPermits；result=false → recordLimitRejectDetail
 *   4. resourceLimit=null 时清理 ThreadLocal 但不记录 metrics
 *   5. 无 beforeLimitCheck 时直接调 afterLimitCheck 不抛异常（startTime=null 分支）
 */
class OperationRateLimiterInterceptorAdaptorTest {

    private val metrics = mock(RateLimiterMetrics::class.java)
    private val adaptor = OperationRateLimiterInterceptorAdaptor(metrics)

    private val resource = "/project/repo/file"
    private val limit = ResourceLimit(
        algo = "FIXED_WINDOW",
        resource = "/",
        limitDimension = LimitDimension.URL.name,
        limit = 100,
    )

    // ─── 正常路径：before → after ─────────────────────────────────────────────────

    @Test
    fun `beforeLimitCheck then afterLimitCheck result=true — records response time and passed permits`() {
        adaptor.beforeLimitCheck(resource, limit)
        adaptor.afterLimitCheck(resource, limit, result = true, e = null)

        verify(metrics).recordLimiterResponseTime(eq(limit.limitDimension), any())
        verify(metrics).recordPassedPermits(eq(limit.limitDimension), eq(resource), eq(1L))
        verify(metrics, never()).recordLimitRejectDetail(any(), any(), any())
    }

    @Test
    fun `beforeLimitCheck then afterLimitCheck result=false — records response time and reject detail`() {
        adaptor.beforeLimitCheck(resource, limit)
        adaptor.afterLimitCheck(resource, limit, result = false, e = null)

        verify(metrics).recordLimiterResponseTime(eq(limit.limitDimension), any())
        verify(metrics).recordLimitRejectDetail(eq(limit.limitDimension), eq(resource), eq(1L))
        verify(metrics, never()).recordPassedPermits(any(), any(), any())
    }

    // ─── resourceLimit=null 时不记录 metrics，但 ThreadLocal 需清理 ─────────────────

    @Test
    fun `afterLimitCheck with null resourceLimit — does not record any metrics`() {
        adaptor.beforeLimitCheck(resource, limit)
        adaptor.afterLimitCheck(resource, resourceLimit = null, result = true, e = null)

        verify(metrics, never()).recordLimiterResponseTime(any(), any())
        verify(metrics, never()).recordPassedPermits(any(), any(), any())
        verify(metrics, never()).recordLimitRejectDetail(any(), any(), any())
    }

    @Test
    fun `afterLimitCheck with null resLimit ThreadLocal is cleared, subsequent call without before does not throw`() {
        adaptor.beforeLimitCheck(resource, limit)
        adaptor.afterLimitCheck(resource, resourceLimit = null, result = true, e = null)

        // Second afterLimitCheck without beforeLimitCheck — should not throw (ThreadLocal is null)
        Assertions.assertDoesNotThrow {
            adaptor.afterLimitCheck(resource, limit, result = true, e = null)
        }
    }

    // ─── 无 beforeLimitCheck 直接调 afterLimitCheck ────────────────────────────────

    @Test
    fun `afterLimitCheck without prior beforeLimitCheck — does not throw`() {
        Assertions.assertDoesNotThrow {
            adaptor.afterLimitCheck(resource, limit, result = true, e = null)
        }
    }

    @Test
    fun `afterLimitCheck without prior beforeLimitCheck — does not record response time (no start time)`() {
        // When startTime is null, recordLimiterResponseTime must NOT be called
        adaptor.afterLimitCheck(resource, limit, result = true, e = null)

        verify(metrics, never()).recordLimiterResponseTime(any(), any())
        // But permits should still be recorded
        verify(metrics).recordPassedPermits(eq(limit.limitDimension), eq(resource), eq(1L))
    }

    // ─── ThreadLocal 无泄漏：连续调用 before/after ──────────────────────────────

    @Test
    fun `consecutive before-after cycles do not accumulate state`() {
        repeat(3) {
            adaptor.beforeLimitCheck(resource, limit)
            adaptor.afterLimitCheck(resource, limit, result = true, e = null)
        }
        // No exception means ThreadLocal was cleaned up each cycle
        Assertions.assertDoesNotThrow {
            adaptor.afterLimitCheck(resource, limit, result = false, e = null)
        }
    }

    // ─── dimension 值正确传递 ───────────────────────────────────────────────────────

    @Test
    fun `afterLimitCheck — uses limitDimension from resourceLimit`() {
        val urlRepoLimit = limit.copy(limitDimension = LimitDimension.URL_REPO.name)
        adaptor.beforeLimitCheck(resource, urlRepoLimit)
        adaptor.afterLimitCheck(resource, urlRepoLimit, result = false, e = null)

        verify(metrics).recordLimitRejectDetail(eq(LimitDimension.URL_REPO.name), eq(resource), eq(1L))
    }
}
