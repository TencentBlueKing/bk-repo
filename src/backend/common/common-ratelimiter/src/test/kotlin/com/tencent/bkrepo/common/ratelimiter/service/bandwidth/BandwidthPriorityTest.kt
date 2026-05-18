package com.tencent.bkrepo.common.ratelimiter.service.bandwidth

import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedTest
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.repository.RateLimitRepository
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import com.tencent.bkrepo.common.ratelimiter.service.bandwidth.user.UserDownloadBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.bandwidth.user.UserUploadBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.util.unit.DataSize
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping
import java.time.Duration

/**
 * 跨服务（inter-service）带宽优先级测试。
 *
 * intra-service（规则树内部）的优先级逻辑由 [com.tencent.bkrepo.common.ratelimiter.rule.PathResourceLimitRuleTest] 负责。
 * 此处专注于 RequestLimitCheckService.bandwidthCheck 的跨服务选择逻辑：
 *   1. 项目级规则 priority > URL 级规则 priority → 项目级胜出
 *   2. 同 priority → URL 级（列表靠前）胜出
 *   3. 只有用户级有规则 → 用户级胜出
 *   4. 无任何规则 → bandwidthCheck 返回 null
 */
class BandwidthPriorityTest : DistributedTest() {

    @MockitoBean
    private lateinit var rateLimitRepository: RateLimitRepository

    private lateinit var rateLimiterProperties: RateLimiterProperties
    private lateinit var rateLimiterMetrics: RateLimiterMetrics
    private lateinit var rateLimiterConfigService: RateLimiterConfigService
    private lateinit var scheduler: ThreadPoolTaskScheduler

    private lateinit var urlDownloadSvc: UrlDownloadBandwidthRateLimiterService
    private lateinit var downloadSvc: DownloadBandwidthRateLimiterService
    private lateinit var userDownloadSvc: UserDownloadBandwidthRateLimiterService
    private lateinit var urlUploadSvc: UrlUploadBandwidthRateLimiterService
    private lateinit var uploadSvc: UploadBandwidthRateLimiterService
    private lateinit var userUploadSvc: UserUploadBandwidthRateLimiterService

    @BeforeEach
    fun setup() {
        scheduler = ThreadPoolTaskScheduler().apply { initialize() }
        rateLimiterMetrics = RateLimiterMetrics(SimpleMeterRegistry())
        rateLimiterConfigService = RateLimiterConfigService(rateLimitRepository)
    }

    private fun initServices(vararg rules: ResourceLimit) {
        rateLimiterProperties = RateLimiterProperties().apply {
            enabled = true
            this.rules = rules.toList()
        }
        urlDownloadSvc = UrlDownloadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        ).also { it.refreshRateLimitRule() }
        downloadSvc = DownloadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        ).also { it.refreshRateLimitRule() }
        userDownloadSvc = UserDownloadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        ).also { it.refreshRateLimitRule() }
        urlUploadSvc = UrlUploadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        ).also { it.refreshRateLimitRule() }
        uploadSvc = UploadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        ).also { it.refreshRateLimitRule() }
        userUploadSvc = UserUploadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        ).also { it.refreshRateLimitRule() }
    }

    // ─── inter-service: bandwidthCheck 跨服务优先级 ──────────────────────────────

    /**
     * URL 服务有规则（priority=0），项目服务也有规则（priority=10）。
     * 期望：项目服务的规则胜出，selected attribute 为 "projectDownload"。
     *
     * 关键设置：
     *  - UrlDownloadBandwidthRateLimiterService.buildResource = request.requestURI
     *    → URL 规则配置成请求 URI 的完整路径即可匹配
     *  - DownloadBandwidthRateLimiterService.buildResource = /$projectId/$repoName/
     *    → 需设置 URI_TEMPLATE_VARIABLES_ATTRIBUTE；规则路径写 /$projectId/$repoName
     */
    @Test
    fun `inter-service - project rule with higher priority beats URL rule with lower priority`() {
        val urlRule = resource(
            path = "/blueking/generic-local/file.txt",
            dim = LimitDimension.URL_DOWNLOAD_BANDWIDTH.name,
            limit = 50L,
            priority = 0
        )
        val projectRule = resource(
            path = "/blueking/generic-local",
            dim = LimitDimension.DOWNLOAD_BANDWIDTH.name,
            limit = 200L,
            priority = 10
        )
        initServices(urlRule, projectRule)

        val request = makeDownloadRequest(
            uri = "/blueking/generic-local/file.txt",
            projectId = "blueking",
            repoName = "generic-local"
        )

        // 验证两个服务都能找到匹配规则
        Assertions.assertEquals(0, urlDownloadSvc.getMatchedRulePriority(request), "URL 服务 priority 应为 0")
        Assertions.assertEquals(10, downloadSvc.getMatchedRulePriority(request), "项目服务 priority 应为 10")

        buildCheckService().bandwidthCheck(
            inputStream = "data".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )

        Assertions.assertEquals(
            "projectDownload",
            request.getAttribute(RequestLimitCheckService.SELECTED_DOWNLOAD_BANDWIDTH_SERVICE),
            "priority=10 的项目规则应胜出，选中 projectDownload"
        )
    }

    /**
     * URL 服务和项目服务拥有同等 priority（均为 5）。
     * 期望：URL 服务胜出（candidates 列表靠前，maxByOrNull 遇 tie 保留第一个）。
     */
    @Test
    fun `inter-service - URL service wins on tie because it appears first in candidate list`() {
        val urlRule = resource(
            path = "/blueking/generic-local/file.txt",
            dim = LimitDimension.URL_DOWNLOAD_BANDWIDTH.name,
            limit = 50L,
            priority = 5
        )
        val projectRule = resource(
            path = "/blueking/generic-local",
            dim = LimitDimension.DOWNLOAD_BANDWIDTH.name,
            limit = 200L,
            priority = 5
        )
        initServices(urlRule, projectRule)

        val request = makeDownloadRequest(
            uri = "/blueking/generic-local/file.txt",
            projectId = "blueking",
            repoName = "generic-local"
        )

        buildCheckService().bandwidthCheck(
            inputStream = "data".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )

        Assertions.assertEquals(
            "urlDownload",
            request.getAttribute(RequestLimitCheckService.SELECTED_DOWNLOAD_BANDWIDTH_SERVICE),
            "同 priority 时列表靠前的 urlDownload 应胜出"
        )
    }

    /**
     * 只有用户级别有规则，URL 级别和项目级别都没有。
     * 期望：用户级服务胜出，selected attribute 为 "userDownload"。
     */
    @Test
    fun `inter-service - user level service wins when it is the only one with a matching rule`() {
        // UserDownloadBandwidthRateLimiterService.buildResource = "$userId:/$projectId/$repoName/"
        // ResourcePathUtils.getUserAndPath splits at first ':', so resource must NOT start with '/'
        // Anonymous user (no USER_KEY attribute set) → userId = "anonymous"
        val userRule = resource(
            path = "anonymous:/blueking/generic-local",
            dim = LimitDimension.USER_DOWNLOAD_BANDWIDTH.name,
            limit = 100L,
            priority = 0
        )
        initServices(userRule)

        val request = makeDownloadRequest(
            uri = "/blueking/generic-local/file.txt",
            projectId = "blueking",
            repoName = "generic-local"
        )

        buildCheckService().bandwidthCheck(
            inputStream = "data".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )

        Assertions.assertEquals(
            "userDownload",
            request.getAttribute(RequestLimitCheckService.SELECTED_DOWNLOAD_BANDWIDTH_SERVICE),
            "只有用户级规则时应选中 userDownload"
        )
    }

    /**
     * 所有服务都没有匹配规则。
     * 期望：bandwidthCheck 返回 null，不设置 selected attribute。
     */
    @Test
    fun `inter-service - bandwidthCheck returns null when no service has a matching rule`() {
        initServices(/* 无规则 */)

        val request = makeDownloadRequest(
            uri = "/blueking/generic-local/file.txt",
            projectId = "blueking",
            repoName = "generic-local"
        )

        val stream = buildCheckService().bandwidthCheck(
            inputStream = "data".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )

        Assertions.assertNull(stream, "无规则时 bandwidthCheck 应返回 null")
        Assertions.assertNull(
            request.getAttribute(RequestLimitCheckService.SELECTED_DOWNLOAD_BANDWIDTH_SERVICE),
            "无规则时不应设置 selected attribute"
        )
    }

    /**
     * 只有 URL 级别有规则（priority=0），项目级别无规则。
     * 期望：URL 服务胜出，不回退到不存在的服务。
     */
    @Test
    fun `inter-service - URL service selected when only URL rule exists`() {
        val urlRule = resource(
            path = "/blueking/generic-local/file.txt",
            dim = LimitDimension.URL_DOWNLOAD_BANDWIDTH.name,
            limit = 50L,
            priority = 0
        )
        initServices(urlRule)

        val request = makeDownloadRequest(
            uri = "/blueking/generic-local/file.txt",
            projectId = "blueking",
            repoName = "generic-local"
        )

        buildCheckService().bandwidthCheck(
            inputStream = "data".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )

        Assertions.assertEquals(
            "urlDownload",
            request.getAttribute(RequestLimitCheckService.SELECTED_DOWNLOAD_BANDWIDTH_SERVICE),
            "只有 URL 规则时应选中 urlDownload"
        )
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private fun resource(path: String, dim: String, limit: Long, priority: Int = 0) = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = path,
        limitDimension = dim,
        limit = limit,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name,
        priority = priority,
    )

    /**
     * 创建带有 project/repo URI 模板变量的下载请求。
     *
     * DownloadBandwidthRateLimiterService.buildResource 使用 URI_TEMPLATE_VARIABLES_ATTRIBUTE
     * 提取 projectId 和 repoName，测试中必须手动设置该属性。
     */
    private fun makeDownloadRequest(
        uri: String,
        projectId: String,
        repoName: String,
    ): MockHttpServletRequest {
        return MockHttpServletRequest("GET", uri).also { req ->
            req.setAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                mapOf("projectId" to projectId, "repoName" to repoName)
            )
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(req))
        }
    }

    private fun buildCheckService(): RequestLimitCheckService {
        val svc = RequestLimitCheckService(rateLimiterProperties)
        fun inject(name: String, value: Any) {
            RequestLimitCheckService::class.java.getDeclaredField(name)
                .also { it.isAccessible = true }
                .set(svc, value)
        }
        inject("urlDownloadBandwidthRateLimiterService", urlDownloadSvc)
        inject("downloadBandwidthRateLimiterService", downloadSvc)
        inject("userDownloadBandwidthRateLimiterService", userDownloadSvc)
        inject("urlUploadBandwidthRateLimiterService", urlUploadSvc)
        inject("uploadBandwidthRateLimiterService", uploadSvc)
        inject("userUploadBandwidthRateLimiterService", userUploadSvc)
        listOf(
            "urlRepoRateLimiterService", "urlRateLimiterService", "userUrlRepoRateLimiterService",
            "uploadUsageRateLimiterService", "userUrlRateLimiterService", "userUploadUsageRateLimiterService",
            "downloadUsageRateLimiterService", "userDownloadUsageRateLimiterService",
        ).forEach { name ->
            runCatching {
                val f = RequestLimitCheckService::class.java.getDeclaredField(name)
                f.isAccessible = true
                if (f.get(svc) == null) f.set(svc, Mockito.mock(f.type))
            }
        }
        return svc
    }
}
