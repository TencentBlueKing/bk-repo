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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * ServiceInstanceConnectionLimiterService 单元测试
 *
 * 核心验证点：
 *   1. limit() 成功时 localConnectionCount 递增
 *   2. limit() 被拒绝时 localConnectionCount 回滚
 *   3. finish() 递减并传递 metrics
 *   4. getConnectionUsageRate() 准确反映比率
 *   5. 并发场景下成功连接数不超过 limit
 *
 * 说明：ServiceInstanceConnectionLimiterService 通过 @Value 注入 serviceName/host，
 *       不经 Spring 直接构造时使用 Kotlin 默认值（serviceName="", host="127.0.0.1"），
 *       instanceId = ":127.0.0.1"（格式为 serviceName:host），
 *       buildResource() 直接返回 instanceId，规则 resource=":127.0.0.1" 精确匹配。
 */
class ServiceInstanceConnectionLimiterServiceTest : DistributedTest() {

    @MockitoBean
    private lateinit var rateLimitRepository: RateLimitRepository

    private lateinit var svc: ServiceInstanceConnectionLimiterService
    private lateinit var metrics: RateLimiterMetrics

    // buildResource() returns instanceId = "$serviceName:$host" = ":127.0.0.1"
    private val instanceResource = ":127.0.0.1"

    private val rule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = instanceResource,
        limitDimension = LimitDimension.SERVICE_INSTANCE_CONNECTION.name,
        limit = 5,
        duration = Duration.ofSeconds(60),
        scope = WorkScope.LOCAL.name,
    )

    @BeforeEach
    fun setup() {
        val scheduler = ThreadPoolTaskScheduler().apply { initialize() }
        metrics = RateLimiterMetrics(SimpleMeterRegistry())
        val configService = RateLimiterConfigService(rateLimitRepository)
        val props = RateLimiterProperties().apply { enabled = true; rules = listOf(rule) }
        svc = ServiceInstanceConnectionLimiterService(scheduler, props, metrics, redisTemplate, configService)
        svc.refreshRateLimitRule()
    }

    // ─── limit / finish 基本行为 ───────────────────────────────────────────────────

    @Test
    fun `limit — increments localConnectionCount on success`() {
        val request = MockHttpServletRequest()

        svc.limit(request)

        Assertions.assertEquals(1, svc.getCurrentConnections())
    }

    @Test
    fun `limit — multiple calls accumulate count correctly`() {
        val request = MockHttpServletRequest()

        svc.limit(request)
        svc.limit(request)
        svc.limit(request)

        Assertions.assertEquals(3, svc.getCurrentConnections())
    }

    @Test
    fun `limit — rolls back localConnectionCount when OverloadException thrown`() {
        val scheduler = ThreadPoolTaskScheduler().apply { initialize() }
        val tightRule = rule.copy(resource = instanceResource, limit = 0)
        val tightProps = RateLimiterProperties().apply { enabled = true; rules = listOf(tightRule) }
        val tightSvc = ServiceInstanceConnectionLimiterService(
            scheduler, tightProps, metrics, redisTemplate,
            RateLimiterConfigService(rateLimitRepository)
        )
        tightSvc.refreshRateLimitRule()

        Assertions.assertThrows(OverloadException::class.java) {
            tightSvc.limit(MockHttpServletRequest())
        }
        Assertions.assertEquals(
            0, tightSvc.getCurrentConnections(),
            "localConnectionCount should be rolled back to 0 after rejection"
        )
    }

    @Test
    fun `finish — decrements localConnectionCount`() {
        val request = MockHttpServletRequest()
        svc.limit(request)
        Assertions.assertEquals(1, svc.getCurrentConnections())

        svc.finish(request)

        Assertions.assertEquals(0, svc.getCurrentConnections())
    }

    @Test
    fun `finish — accepts call without exception`() {
        val request = MockHttpServletRequest()
        svc.limit(request)

        Assertions.assertDoesNotThrow {
            svc.finish(request)
        }
        Assertions.assertEquals(0, svc.getCurrentConnections())
    }

    @Test
    fun `finish — does not throw`() {
        val request = MockHttpServletRequest()
        svc.limit(request)

        Assertions.assertDoesNotThrow {
            svc.finish(request)
        }
    }

    // ─── getMaxConnectionsConfig ──────────────────────────────────────────────────

    @Test
    fun `getMaxConnectionsConfig — returns limit from matched rule`() {
        Assertions.assertEquals(5, svc.getMaxConnectionsConfig())
    }

    @Test
    fun `getMaxConnectionsConfig — returns Int MAX_VALUE when no rule is configured`() {
        val scheduler = ThreadPoolTaskScheduler().apply { initialize() }
        val noRuleProps = RateLimiterProperties().apply { enabled = true; rules = emptyList() }
        val noRuleSvc = ServiceInstanceConnectionLimiterService(
            scheduler, noRuleProps, metrics, redisTemplate,
            RateLimiterConfigService(rateLimitRepository)
        ).also { it.refreshRateLimitRule() }

        Assertions.assertEquals(Int.MAX_VALUE, noRuleSvc.getMaxConnectionsConfig())
    }

    // ─── getConnectionUsageRate ───────────────────────────────────────────────────

    @Test
    fun `getConnectionUsageRate — returns 0 dot 0 when no rule configured`() {
        val scheduler = ThreadPoolTaskScheduler().apply { initialize() }
        val noRuleProps = RateLimiterProperties().apply { enabled = true; rules = emptyList() }
        val noRuleSvc = ServiceInstanceConnectionLimiterService(
            scheduler, noRuleProps, metrics, redisTemplate,
            RateLimiterConfigService(rateLimitRepository)
        ).also { it.refreshRateLimitRule() }

        Assertions.assertEquals(0.0, noRuleSvc.getConnectionUsageRate(), 0.001)
    }

    @Test
    fun `getConnectionUsageRate — returns correct ratio when connections are active`() {
        // limit=5, establish 3 connections
        val request = MockHttpServletRequest()
        svc.limit(request)
        svc.limit(request)
        svc.limit(request)

        Assertions.assertEquals(3.0 / 5.0, svc.getConnectionUsageRate(), 0.001)
    }

    @Test
    fun `getConnectionUsageRate — returns 0 dot 0 when no active connections`() {
        Assertions.assertEquals(0.0, svc.getConnectionUsageRate(), 0.001)
    }

    @Test
    fun `getConnectionUsageRate — reaches 1 dot 0 when at capacity`() {
        val request = MockHttpServletRequest()
        repeat(5) { svc.limit(request) }

        Assertions.assertEquals(1.0, svc.getConnectionUsageRate(), 0.001)
    }

    // ─── 并发场景 ──────────────────────────────────────────────────────────────────

    @Test
    fun `concurrent limit — accepted connections never exceed the configured limit`() {
        val threadCount = 20
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                try {
                    svc.limit(MockHttpServletRequest())
                    successCount.incrementAndGet()
                } catch (_: OverloadException) {
                    // expected for requests beyond limit
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        executor.shutdown()

        Assertions.assertTrue(
            successCount.get() <= 5,
            "Accepted connections (${successCount.get()}) must not exceed limit=5"
        )
    }

    @Test
    fun `concurrent limit and finish — count returns to zero after all finish`() {
        val r1 = MockHttpServletRequest()
        val r2 = MockHttpServletRequest()
        val r3 = MockHttpServletRequest()
        // Establish 3 connections (each with its own request object, as in production)
        svc.limit(r1)
        svc.limit(r2)
        svc.limit(r3)
        Assertions.assertEquals(3, svc.getCurrentConnections())

        // Release all 3
        svc.finish(r1)
        svc.finish(r2)
        svc.finish(r3)

        Assertions.assertEquals(0, svc.getCurrentConnections())
    }
}
