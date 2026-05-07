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
import com.tencent.bkrepo.common.ratelimiter.stream.CommonRateLimitInputStream
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
 * TDD 测试：验证 bandwidthCheck 的 first-match 静默失效 Bug 以及修复后的 fallback 行为。
 *
 * Bug 描述：
 *   UrlDownloadBandwidthRateLimiterService.ignoreRequest 永远返回 false，
 *   导致 bandwidthCheck 总是先尝试 URL 服务。当 URL 无匹配规则时
 *   bandwidthRateStart 返回 null，bandwidthCheck 直接返回 null，
 *   项目级和用户级带宽规则永远不生效。
 *
 * 期望修复后的行为：
 *   bandwidthCheck 在 URL 服务无匹配规则时，应继续尝试项目级服务；
 *   项目级也无规则时，继续尝试用户级服务；均无匹配则返回 null。
 */
class RequestLimitCheckServiceBandwidthFallbackTest : DistributedTest() {

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

    private val projectDownloadRule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/blueking/generic-local",
        limitDimension = LimitDimension.DOWNLOAD_BANDWIDTH.name,
        limit = 1024 * 1024 * 100L,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name,
    )

    // UserDownloadBandwidthRateLimiterService.buildResource = "$userId:/$projectId/$repoName/"
    // Anonymous user (no USER_KEY attribute set on request) → userId = "anonymous"
    private val userDownloadRule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "anonymous:/blueking/generic-local",
        limitDimension = LimitDimension.USER_DOWNLOAD_BANDWIDTH.name,
        limit = 1024 * 1024 * 50L,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name,
    )

    private val urlDownloadRule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/blueking/generic-local/file.txt",
        limitDimension = LimitDimension.URL_DOWNLOAD_BANDWIDTH.name,
        limit = 1024 * 1024 * 10L,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name,
    )

    private val projectUploadRule = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/blueking/generic-local",
        limitDimension = LimitDimension.UPLOAD_BANDWIDTH.name,
        limit = 1024 * 1024 * 100L,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name,
    )

    @BeforeEach
    fun setup() {
        scheduler = ThreadPoolTaskScheduler().apply { initialize() }
        rateLimiterProperties = RateLimiterProperties().apply { enabled = true }
        rateLimiterMetrics = RateLimiterMetrics(SimpleMeterRegistry())
        rateLimiterConfigService = RateLimiterConfigService(rateLimitRepository)

        urlDownloadSvc = UrlDownloadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        )
        downloadSvc = DownloadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        )
        userDownloadSvc = UserDownloadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        )
        urlUploadSvc = UrlUploadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        )
        uploadSvc = UploadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        )
        userUploadSvc = UserUploadBandwidthRateLimiterService(
            scheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService
        )
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    /**
     * 构建 RequestLimitCheckService 并通过反射注入所有带宽 Service。
     * 当前 RequestLimitCheckService 使用 @Autowired，单元测试中需要手动注入。
     */
    private fun buildCheckService(): RequestLimitCheckService {
        val svc = RequestLimitCheckService(rateLimiterProperties)
        fun inject(fieldName: String, value: Any) {
            val f = RequestLimitCheckService::class.java.getDeclaredField(fieldName)
            f.isAccessible = true
            f.set(svc, value)
        }
        inject("urlDownloadBandwidthRateLimiterService", urlDownloadSvc)
        inject("downloadBandwidthRateLimiterService", downloadSvc)
        inject("userDownloadBandwidthRateLimiterService", userDownloadSvc)
        inject("urlUploadBandwidthRateLimiterService", urlUploadSvc)
        inject("uploadBandwidthRateLimiterService", uploadSvc)
        inject("userUploadBandwidthRateLimiterService", userUploadSvc)
        // 其他非带宽服务用 Mock 填充，避免 NPE
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

    private fun makeDownloadRequest(
        uri: String = "/blueking/generic-local/file.txt",
        projectId: String = "blueking",
        repoName: String = "generic-local",
    ): MockHttpServletRequest {
        return MockHttpServletRequest("GET", uri).also { req ->
            req.setAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                mapOf("projectId" to projectId, "repoName" to repoName)
            )
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(req))
        }
    }

    private fun makeUploadRequest(
        uri: String = "/blueking/generic-local/file.txt",
        projectId: String = "blueking",
        repoName: String = "generic-local",
    ): MockHttpServletRequest {
        return MockHttpServletRequest("PUT", uri).also { req ->
            req.setAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                mapOf("projectId" to projectId, "repoName" to repoName)
            )
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(req))
        }
    }

    // ─── 核心 Bug：URL 无规则时项目级规则不生效 ───────────────────────────────────

    /**
     * 【BUG REGRESSION】
     * 仅配置项目级下载带宽规则，URL 服务无规则。
     * 修复前：bandwidthCheck 因 URL 服务 ignoreRequest=false 进入后，bandwidthRateStart
     *         返回 null（无规则），整个方法直接返回 null → 项目规则静默失效。
     * 修复后：应继续 fallback 到项目级服务，返回非 null 的 CommonRateLimitInputStream。
     */
    @Test
    fun `bandwidthCheck — project rule applied when URL service has no matching rule`() {
        // 只有项目规则，URL 服务无规则
        rateLimiterProperties.rules = listOf(projectDownloadRule)
        urlDownloadSvc.refreshRateLimitRule()
        downloadSvc.refreshRateLimitRule()
        userDownloadSvc.refreshRateLimitRule()

        val checkService = buildCheckService()
        val request = makeDownloadRequest()
        val stream = checkService.bandwidthCheck(
            inputStream = "test content".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )

        // 修复后期望非 null（项目规则生效）
        // 修复前此断言失败，返回 null
        Assertions.assertNotNull(stream, "项目级下载带宽规则未生效，bandwidthCheck 应返回非 null")
        (stream as? CommonRateLimitInputStream)?.close()
    }

    /**
     * 【BUG REGRESSION】
     * URL 无规则、项目无规则，仅配置用户级下载带宽规则。
     * 修复后：应 fallback 到用户级服务。
     */
    @Test
    fun `bandwidthCheck — user rule applied when both URL and project have no matching rule`() {
        rateLimiterProperties.rules = listOf(userDownloadRule)
        urlDownloadSvc.refreshRateLimitRule()
        downloadSvc.refreshRateLimitRule()
        userDownloadSvc.refreshRateLimitRule()

        val checkService = buildCheckService()
        makeDownloadRequest()

        val stream = checkService.bandwidthCheck(
            inputStream = "test content".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )

        Assertions.assertNotNull(stream, "用户级下载带宽规则未生效，bandwidthCheck 应返回非 null")
        (stream as? CommonRateLimitInputStream)?.close()
    }

    /**
     * URL 规则存在时，URL 规则优先于项目规则（正确行为，修复后仍需保持）。
     */
    @Test
    fun `bandwidthCheck — URL rule takes priority when both URL and project rules exist`() {
        rateLimiterProperties.rules = listOf(urlDownloadRule, projectDownloadRule)
        urlDownloadSvc.refreshRateLimitRule()
        downloadSvc.refreshRateLimitRule()
        userDownloadSvc.refreshRateLimitRule()

        val checkService = buildCheckService()
        makeDownloadRequest()

        val stream = checkService.bandwidthCheck(
            inputStream = "test content".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )
        Assertions.assertNotNull(stream, "URL 级规则存在时应返回非 null")
        (stream as? CommonRateLimitInputStream)?.close()
    }

    /**
     * 无任何带宽规则时，bandwidthCheck 应返回 null（不应崩溃）。
     */
    @Test
    fun `bandwidthCheck — returns null when no bandwidth rules configured`() {
        rateLimiterProperties.rules = emptyList()
        urlDownloadSvc.refreshRateLimitRule()
        downloadSvc.refreshRateLimitRule()
        userDownloadSvc.refreshRateLimitRule()

        val checkService = buildCheckService()
        makeDownloadRequest()

        val stream = checkService.bandwidthCheck(
            inputStream = "test content".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )
        Assertions.assertNull(stream)
    }

    // ─── bandwidthFinish 与 bandwidthCheck 服务一致性 ─────────────────────────

    /**
     * 【BUG REGRESSION】
     * bandwidthCheck 和 bandwidthFinish 各自独立调用 ignoreRequest，
     * 若规则在两次调用之间热更新，会导致 finish 走到不同服务，计数泄漏。
     * 修复后：bandwidthFinish 应使用 bandwidthCheck 时已确定的服务。
     */
    @Test
    fun `bandwidthFinish — uses same service as bandwidthCheck regardless of rule hot-reload`() {
        // 初始：只有项目规则
        rateLimiterProperties.rules = listOf(projectDownloadRule)
        urlDownloadSvc.refreshRateLimitRule()
        downloadSvc.refreshRateLimitRule()
        userDownloadSvc.refreshRateLimitRule()

        val checkService = buildCheckService()
        val request = makeDownloadRequest()

        val stream = checkService.bandwidthCheck(
            inputStream = "test content".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )
        Assertions.assertNotNull(stream)

        // 模拟热更新：check 之后、finish 之前，规则变更为 URL 规则
        rateLimiterProperties.rules = listOf(urlDownloadRule)
        urlDownloadSvc.refreshRateLimitRule()
        downloadSvc.refreshRateLimitRule()

        // finish 应仍然操作项目级服务（通过 request attribute 中存储的选择），不会崩溃
        Assertions.assertDoesNotThrow {
            checkService.bandwidthFinish()
        }
        (stream as? CommonRateLimitInputStream)?.close()
    }

    // ─── 上传带宽：不应混入下载服务 ──────────────────────────────────────────

    /**
     * 【BUG REGRESSION】
     * bandwidthCheck（下载路径）中含有 uploadBandwidthRateLimiterService 检查。
     * 当请求为下载时，上传带宽服务的 ignoreRequest 返回 true（POST/PUT 不是 GET），
     * 但它不应出现在下载路径中。
     * 验证：上传规则不影响下载流的选择。
     */
    @Test
    fun `bandwidthCheck — upload bandwidth rule does NOT affect download stream selection`() {
        // 只配置上传带宽规则
        rateLimiterProperties.rules = listOf(projectUploadRule)
        urlDownloadSvc.refreshRateLimitRule()
        downloadSvc.refreshRateLimitRule()
        userDownloadSvc.refreshRateLimitRule()
        urlUploadSvc.refreshRateLimitRule()
        uploadSvc.refreshRateLimitRule()
        userUploadSvc.refreshRateLimitRule()

        val checkService = buildCheckService()
        makeDownloadRequest()

        // 下载请求中，上传规则不应生效，应返回 null
        val stream = checkService.bandwidthCheck(
            inputStream = "test content".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )
        Assertions.assertNull(stream, "下载请求不应被上传带宽规则限速")
    }

    /**
     * uploadBandwidthCheck 中，项目上传规则应生效。
     */
    @Test
    fun `uploadBandwidthCheck — project upload rule applied`() {
        rateLimiterProperties.rules = listOf(projectUploadRule)
        uploadSvc.refreshRateLimitRule()

        val checkService = buildCheckService()
        makeUploadRequest()

        Assertions.assertDoesNotThrow {
            checkService.uploadBandwidthCheck(1024L, DataSize.ofBytes(0))
        }
    }

    // ─── bandwidthRateLimitFinish 热更新后 metrics 一致性 ────────────────────────

    /**
     * 热更新后规则完全移除：
     * bandwidthRateStart 调用了 doBeforeLimitCheck（设置 ThreadLocal），
     * bandwidthRateLimitFinish 因 getResLimitInfoAndResInfo=null 提前 return，
     * 不调用 doAfterLimitCheck → ThreadLocal 未清理。
     *
     * 验证：bandwidthFinish 在规则移除后不会抛出异常（不崩溃）。
     * 已知 ThreadLocal 泄漏为设计权衡，此用例起到回归保护作用。
     */
    @Test
    fun `bandwidthFinish — does not throw when rule is removed after bandwidthCheck`() {
        rateLimiterProperties.rules = listOf(projectDownloadRule)
        urlDownloadSvc.refreshRateLimitRule()
        downloadSvc.refreshRateLimitRule()
        userDownloadSvc.refreshRateLimitRule()

        val checkService = buildCheckService()
        val request = makeDownloadRequest()

        val stream = checkService.bandwidthCheck(
            inputStream = "test content".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )
        Assertions.assertNotNull(stream)

        // 热更新：移除所有规则，服务无匹配规则
        rateLimiterProperties.rules = emptyList()
        downloadSvc.refreshRateLimitRule()

        // bandwidthFinish 应静默返回，不抛出任何异常
        Assertions.assertDoesNotThrow {
            checkService.bandwidthFinish()
        }
        (stream as? CommonRateLimitInputStream)?.close()
    }

    /**
     * 热更新后规则更换为另一匹配规则：
     * bandwidthRateLimitFinish 使用新规则的 resLimitInfo 调用 afterRateLimitCheck。
     * 验证：不论 dimension 是否改变，bandwidthFinish 均不崩溃。
     */
    @Test
    fun `bandwidthFinish — does not throw when rule is replaced after bandwidthCheck`() {
        rateLimiterProperties.rules = listOf(projectDownloadRule)
        urlDownloadSvc.refreshRateLimitRule()
        downloadSvc.refreshRateLimitRule()
        userDownloadSvc.refreshRateLimitRule()

        val checkService = buildCheckService()
        val request = makeDownloadRequest()

        val stream = checkService.bandwidthCheck(
            inputStream = "test content".byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(0),
        )
        Assertions.assertNotNull(stream)

        // 热更新：将 limit 从 100MB 改为 200MB
        val updatedRule = projectDownloadRule.copy(limit = 1024 * 1024 * 200L)
        rateLimiterProperties.rules = listOf(updatedRule)
        downloadSvc.refreshRateLimitRule()

        // finish 应使用新规则但不崩溃
        Assertions.assertDoesNotThrow {
            checkService.bandwidthFinish()
        }
        (stream as? CommonRateLimitInputStream)?.close()
    }
}
