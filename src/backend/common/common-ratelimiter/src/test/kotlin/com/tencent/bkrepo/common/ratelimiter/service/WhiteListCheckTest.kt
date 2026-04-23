package com.tencent.bkrepo.common.ratelimiter.service

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedTest
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.repository.RateLimitRepository
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.url.UrlRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.servlet.HandlerMapping
import java.time.Duration

/**
 * 白名单检查测试
 *
 * 覆盖场景：
 *   - projectWhiteListEnabled=false：全放通，无需匹配
 *   - 精确匹配命中白名单：放通
 *   - 不在白名单：抛 OverloadException
 *   - 空白名单：抛 OverloadException
 *   - 通配符匹配（含 *）：命中/未命中
 *   - 其他正则字符（$、^）：命中
 *   - 畸形正则（不崩溃，当作未命中）
 *   - 无 projectId（InvalidResourceException）+ URL 在 specialUrlsIgnoreProjectWhiteList：放通
 *   - 无 projectId + URL 不在 special 列表：抛 OverloadException
 */
class WhiteListCheckTest : DistributedTest() {

    @MockitoBean
    private lateinit var rateLimitRepository: RateLimitRepository

    private lateinit var svc: UrlRateLimiterService
    private lateinit var props: RateLimiterProperties

    private val baseRule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/",
        limitDimension = LimitDimension.URL.name,
        limit = 100,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name,
    )

    @BeforeEach
    fun setup() {
        props = RateLimiterProperties().apply {
            enabled = true
            rules = listOf(baseRule)
            projectWhiteListEnabled = true
        }
        val scheduler = ThreadPoolTaskScheduler().apply { initialize() }
        val metrics = RateLimiterMetrics(SimpleMeterRegistry())
        val configService = RateLimiterConfigService(rateLimitRepository)
        svc = UrlRateLimiterService(scheduler, props, metrics, redisTemplate, configService)
        svc.refreshRateLimitRule()
    }

    // ─── projectWhiteListEnabled = false ─────────────────────────────────────────

    @Test
    fun `disabled — passes regardless of whitelist content`() {
        props.projectWhiteListEnabled = false
        props.projectWhiteList = emptyList()

        Assertions.assertDoesNotThrow {
            svc.whiteListCheck(requestWithProject("any-project"))
        }
    }

    // ─── 精确匹配 ─────────────────────────────────────────────────────────────────

    @Test
    fun `exact match — project in whitelist passes`() {
        props.projectWhiteList = listOf("blueking", "devops")

        Assertions.assertDoesNotThrow {
            svc.whiteListCheck(requestWithProject("blueking"))
        }
    }

    @Test
    fun `exact match — project not in whitelist throws`() {
        props.projectWhiteList = listOf("blueking")

        Assertions.assertThrows(OverloadException::class.java) {
            svc.whiteListCheck(requestWithProject("other-project"))
        }
    }

    @Test
    fun `empty whitelist — always throws`() {
        props.projectWhiteList = emptyList()

        Assertions.assertThrows(OverloadException::class.java) {
            svc.whiteListCheck(requestWithProject("blueking"))
        }
    }

    // ─── 通配符 / 正则 ────────────────────────────────────────────────────────────

    @Test
    fun `wildcard * — matches prefix`() {
        props.projectWhiteList = listOf("bk-*")

        Assertions.assertDoesNotThrow {
            svc.whiteListCheck(requestWithProject("bk-ci"))
        }
        Assertions.assertDoesNotThrow {
            svc.whiteListCheck(requestWithProject("bk-repo"))
        }
    }

    @Test
    fun `wildcard * — does not match unrelated project`() {
        props.projectWhiteList = listOf("bk-*")

        Assertions.assertThrows(OverloadException::class.java) {
            svc.whiteListCheck(requestWithProject("other-project"))
        }
    }

    @Test
    fun `regex chars dollar and caret — exact anchored match`() {
        // pattern "^blueking$" matches only "blueking"
        props.projectWhiteList = listOf("^blueking\$")

        Assertions.assertDoesNotThrow {
            svc.whiteListCheck(requestWithProject("blueking"))
        }
        Assertions.assertThrows(OverloadException::class.java) {
            svc.whiteListCheck(requestWithProject("bk-blueking"))
        }
    }

    @Test
    fun `malformed regex — does not crash, treated as non-match`() {
        // "[unclosed" is an invalid regex; isPotentialRegex returns true due to '[',
        // Regex compilation throws inside catch → returns false → not in whitelist
        props.projectWhiteList = listOf("[unclosed")

        Assertions.assertThrows(OverloadException::class.java) {
            svc.whiteListCheck(requestWithProject("blueking"))
        }
    }

    // ─── 无 projectId 场景（InvalidResourceException 分支）──────────────────────

    @Test
    fun `no projectId — URL in specialUrlsIgnoreProjectWhiteList passes`() {
        props.specialUrlsIgnoreProjectWhiteList = listOf("/api/*/node/search")

        val request = MockHttpServletRequest()
        // 不设置 URI_TEMPLATE_VARIABLES_ATTRIBUTE → getRepoInfoFromAttribute 抛 InvalidResourceException
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/*/node/search")

        Assertions.assertDoesNotThrow {
            svc.whiteListCheck(request)
        }
    }

    @Test
    fun `no projectId — URL not in specialUrlsIgnoreProjectWhiteList throws`() {
        props.specialUrlsIgnoreProjectWhiteList = listOf("/api/*/node/search")

        val request = MockHttpServletRequest()
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/*/some-other-path")

        Assertions.assertThrows(OverloadException::class.java) {
            svc.whiteListCheck(request)
        }
    }

    @Test
    fun `no projectId and no special URL attribute — throws`() {
        props.specialUrlsIgnoreProjectWhiteList = emptyList()
        val request = MockHttpServletRequest()

        Assertions.assertThrows(OverloadException::class.java) {
            svc.whiteListCheck(request)
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private fun requestWithProject(projectId: String): MockHttpServletRequest {
        val request = MockHttpServletRequest()
        request.requestURI = "/$projectId/generic-local/test.txt"
        request.setAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            mapOf("projectId" to projectId, "repoName" to "generic-local")
        )
        return request
    }
}
