package com.tencent.bkrepo.common.ratelimiter.service.connection

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedTest
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.repository.RateLimitRepository
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import com.tencent.bkrepo.common.api.constant.USER_KEY
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Duration

/**
 * TDD 测试：UserConcurrentConnectionLimiterService 的两个 Bug
 *
 * Bug 1 — finish() 依赖 SecurityUtils.getUserId() 不安全
 *   finish() 直接调用 SecurityUtils.getUserId()，afterCompletion 时安全上下文已清除，
 *   返回 "anonymous"，导致 userId="testUser" 的连接计数永久不归零。
 *   修复方案：preHandle 时将 userId 写入 request attribute，finish() 从 attribute 读取。
 *
 * Bug 2 — 本地 map key 不清理（内存泄漏）
 *   连接数降为 0 后，userConnectionCount 中对应 key 永不移除，
 *   长期运行后 map 随用户数线性增长。
 *   修复方案：decrementAndGet() <= 0 时通过 computeIfPresent 移除 key。
 */
class UserConcurrentConnectionLimiterServiceTest : DistributedTest() {

    @MockitoBean
    private lateinit var rateLimitRepository: RateLimitRepository

    private lateinit var svc: UserConcurrentConnectionLimiterService

    // 每个用户最多 2 个并发连接
    private val rule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/",
        limitDimension = LimitDimension.USER_CONCURRENT_CONNECTION.name,
        limit = 2,
        duration = Duration.ofSeconds(60),
        scope = WorkScope.LOCAL.name,
    )

    @AfterEach
    fun tearDown() {
        RequestContextHolder.resetRequestAttributes()
    }

    @BeforeEach
    fun setup() {
        val scheduler = ThreadPoolTaskScheduler().apply { initialize() }
        val props = RateLimiterProperties().apply { enabled = true; rules = listOf(rule) }
        val metrics = RateLimiterMetrics(SimpleMeterRegistry())
        val configService = RateLimiterConfigService(rateLimitRepository)
        svc = UserConcurrentConnectionLimiterService(scheduler, props, metrics, redisTemplate, configService)
        svc.refreshRateLimitRule()
    }

    // ─── Bug 1：finish 需要从 request attribute 读取 userId ─────────────────────

    /**
     * 【BUG REGRESSION】
     * 模拟 afterCompletion 场景：安全上下文已清除，finish() 调用 SecurityUtils.getUserId()
     * 返回 "anonymous"，导致 "testUser" 的计数未归零。
     *
     * 修复后：finish() 应从 request attribute 读取 userId，安全上下文无关。
     */
    @Test
    fun `finish — decrements correct user when security context is cleared in afterCompletion`() {
        val request = MockHttpServletRequest()
        setUser(request, "testUser")

        svc.limit(request)
        Assertions.assertEquals(1, svc.getUserConnections("testUser"), "limit 后计数应为 1")

        // Simulate security context cleared in afterCompletion (limit() already stored userId in request attr)
        RequestContextHolder.resetRequestAttributes()

        // finish() reads userId from request attribute "RATE_LIMIT_USER_ID", independent of security context
        svc.finish(request)
        Assertions.assertEquals(
            0, svc.getUserConnections("testUser"),
            "afterCompletion 安全上下文清除后，finish 仍应正确归还 testUser 的连接计数"
        )
    }

    /**
     * 正常路径：limit 和 finish 对称，计数归零。
     */
    @Test
    fun `limit and finish — symmetric increment and decrement`() {
        val request = MockHttpServletRequest()
        setUser(request, "userA")

        svc.limit(request)
        Assertions.assertEquals(1, svc.getUserConnections("userA"))

        svc.finish(request)
        Assertions.assertEquals(0, svc.getUserConnections("userA"))
    }

    /**
     * 超出并发上限时 limit 抛出 OverloadException，且本地计数回滚。
     */
    @Test
    fun `limit — throws OverloadException when connection limit exceeded and rolls back count`() {
        val request = MockHttpServletRequest()
        setUser(request, "userB")

        svc.limit(request)
        svc.limit(request)
        val countAtLimit = svc.getUserConnections("userB")

        Assertions.assertThrows(OverloadException::class.java) {
            svc.limit(request)
        }
        Assertions.assertEquals(countAtLimit, svc.getUserConnections("userB"))
    }

    // ─── Bug 2：map key 应在计数归零时清除 ───────────────────────────────────────

    @Test
    fun `finish — removes map key when count reaches zero`() {
        val request = MockHttpServletRequest()
        setUser(request, "gcUser")

        svc.limit(request)
        Assertions.assertEquals(1, svc.getUserConnections("gcUser"))

        svc.finish(request)

        val mapField = UserConcurrentConnectionLimiterService::class.java.getDeclaredField("userConnectionCount")
        mapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = mapField.get(svc) as java.util.concurrent.ConcurrentHashMap<*, *>

        Assertions.assertFalse(
            map.containsKey("gcUser"),
            "计数归零后 map 中应移除 key 以防内存泄漏，修复前此断言失败"
        )
    }

    @Test
    fun `finish — does NOT remove map key when count still above zero`() {
        val request = MockHttpServletRequest()
        setUser(request, "multiUser")

        svc.limit(request)
        svc.limit(request)
        Assertions.assertEquals(2, svc.getUserConnections("multiUser"))

        svc.finish(request)
        Assertions.assertEquals(1, svc.getUserConnections("multiUser"))

        val mapField = UserConcurrentConnectionLimiterService::class.java.getDeclaredField("userConnectionCount")
        mapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = mapField.get(svc) as java.util.concurrent.ConcurrentHashMap<*, *>

        // 计数仍为 1，key 不应被移除
        Assertions.assertTrue(map.containsKey("multiUser"), "计数 > 0 时不应移除 key")
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    /**
     * SecurityUtils.getUserId() reads from HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY),
     * NOT from SecurityContextHolder. Set the attribute on the request and bind it to RequestContextHolder.
     */
    private fun setUser(request: MockHttpServletRequest, userId: String) {
        request.setAttribute(USER_KEY, userId)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }
}
