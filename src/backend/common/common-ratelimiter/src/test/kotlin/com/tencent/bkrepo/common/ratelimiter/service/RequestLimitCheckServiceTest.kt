package com.tencent.bkrepo.common.ratelimiter.service

import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedTest
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.repository.RateLimitRepository
import com.tencent.bkrepo.common.ratelimiter.service.url.UrlRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.url.UrlRepoRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.url.user.UserUrlRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.url.user.UserUrlRepoRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.DownloadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.UploadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.user.UserDownloadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.usage.user.UserUploadUsageRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * RequestLimitCheckService 综合测试
 *
 * 覆盖：
 *   - enabled=false 全部跳过
 *   - preLimitCheckForUser 调用 4 个服务
 *   - preLimitCheckForNonUser 调用 ip + urlRepo + url（ip 可选）
 *   - postLimitCheck 调用 downloadUsage + userDownloadUsage
 *   - bandwidthFinish 无 attribute 时不抛
 */
class RequestLimitCheckServiceTest : DistributedTest() {

    @MockitoBean
    private lateinit var rateLimitRepository: RateLimitRepository

    private lateinit var scheduler: ThreadPoolTaskScheduler
    private lateinit var metrics: RateLimiterMetrics
    private lateinit var configService: RateLimiterConfigService
    private lateinit var props: RateLimiterProperties

    // ── Mock services ──────────────────────────────────────────────────────────
    private lateinit var urlRepoSvc: UrlRepoRateLimiterService
    private lateinit var urlSvc: UrlRateLimiterService
    private lateinit var userUrlRepoSvc: UserUrlRepoRateLimiterService
    private lateinit var userUrlSvc: UserUrlRateLimiterService
    private lateinit var uploadUsageSvc: UploadUsageRateLimiterService
    private lateinit var userUploadUsageSvc: UserUploadUsageRateLimiterService
    private lateinit var downloadUsageSvc: DownloadUsageRateLimiterService
    private lateinit var userDownloadUsageSvc: UserDownloadUsageRateLimiterService

    @BeforeEach
    fun setup() {
        SecurityContextHolder.clearContext()
        scheduler = ThreadPoolTaskScheduler().apply { initialize() }
        metrics = RateLimiterMetrics(SimpleMeterRegistry())
        configService = RateLimiterConfigService(rateLimitRepository)
        props = RateLimiterProperties().apply { enabled = true }

        urlRepoSvc = mock(UrlRepoRateLimiterService::class.java)
        urlSvc = mock(UrlRateLimiterService::class.java)
        userUrlRepoSvc = mock(UserUrlRepoRateLimiterService::class.java)
        userUrlSvc = mock(UserUrlRateLimiterService::class.java)
        uploadUsageSvc = mock(UploadUsageRateLimiterService::class.java)
        userUploadUsageSvc = mock(UserUploadUsageRateLimiterService::class.java)
        downloadUsageSvc = mock(DownloadUsageRateLimiterService::class.java)
        userDownloadUsageSvc = mock(UserDownloadUsageRateLimiterService::class.java)
    }

    // ─── enabled=false 全部跳过 ──────────────────────────────────────────────────

    @Test
    fun `preLimitCheckForUser — skips all services when enabled is false`() {
        props.enabled = false
        val checkSvc = buildCheckService()
        val request = makeRequest("GET", "/api/test")

        checkSvc.preLimitCheckForUser(request)

        verify(userUrlRepoSvc, never()).limit(any(), any())
        verify(userUrlSvc, never()).limit(any(), any())
        verify(userUploadUsageSvc, never()).limit(any(), any())
        verify(uploadUsageSvc, never()).limit(any(), any())
    }

    @Test
    fun `preLimitCheckForNonUser — skips all services when enabled is false`() {
        props.enabled = false
        val checkSvc = buildCheckService()
        val request = makeRequest("GET", "/api/test")

        checkSvc.preLimitCheckForNonUser(request)

        verify(urlRepoSvc, never()).limit(any(), any())
        verify(urlSvc, never()).limit(any(), any())
    }

    @Test
    fun `postLimitCheck — skips services when enabled is false`() {
        props.enabled = false
        val checkSvc = buildCheckService()
        setRequest("GET", "/api/test")

        checkSvc.postLimitCheck(1024L)

        verify(downloadUsageSvc, never()).limit(any(), any())
        verify(userDownloadUsageSvc, never()).limit(any(), any())
    }

    // ─── preLimitCheckForUser ─────────────────────────────────────────────────────

    @Test
    fun `preLimitCheckForUser — calls all four user services`() {
        val checkSvc = buildCheckService()
        val request = makeRequest("GET", "/api/test")

        checkSvc.preLimitCheckForUser(request)

        verify(userUrlRepoSvc).limit(request)
        verify(userUrlSvc).limit(request)
        verify(userUploadUsageSvc).limit(request)
        verify(uploadUsageSvc).limit(request)
    }

    @Test
    fun `preLimitCheckForUser — calls services in order userUrlRepo userUrl userUploadUsage uploadUsage`() {
        val inOrder = org.mockito.Mockito.inOrder(userUrlRepoSvc, userUrlSvc, userUploadUsageSvc, uploadUsageSvc)
        val checkSvc = buildCheckService()
        val request = makeRequest("GET", "/api/test")

        checkSvc.preLimitCheckForUser(request)

        inOrder.verify(userUrlRepoSvc).limit(request)
        inOrder.verify(userUrlSvc).limit(request)
        inOrder.verify(userUploadUsageSvc).limit(request)
        inOrder.verify(uploadUsageSvc).limit(request)
    }

    // ─── preLimitCheckForNonUser ──────────────────────────────────────────────────

    @Test
    fun `preLimitCheckForNonUser — calls urlRepo and url services`() {
        val checkSvc = buildCheckService()
        val request = makeRequest("GET", "/api/test")

        checkSvc.preLimitCheckForNonUser(request)

        verify(urlRepoSvc).limit(request)
        verify(urlSvc).limit(request)
    }

    @Test
    fun `preLimitCheckForNonUser — does not throw when ipRateLimiterService is null`() {
        val checkSvc = buildCheckService(withIpService = false)
        val request = makeRequest("GET", "/api/test")

        Assertions.assertDoesNotThrow {
            checkSvc.preLimitCheckForNonUser(request)
        }
    }

    // ─── postLimitCheck ────────────────────────────────────────────────────────────

    @Test
    fun `postLimitCheck — calls downloadUsage and userDownloadUsage with applyPermits`() {
        val checkSvc = buildCheckService()
        val request = setRequest("GET", "/api/download/file")

        checkSvc.postLimitCheck(4096L)

        verify(downloadUsageSvc).limit(request, 4096L)
        verify(userDownloadUsageSvc).limit(request, 4096L)
    }

    // ─── bandwidthFinish without prior bandwidthCheck ─────────────────────────────

    @Test
    fun `bandwidthFinish — does not throw when no attribute set`() {
        val checkSvc = buildCheckService()
        setRequest("GET", "/api/download/file")

        Assertions.assertDoesNotThrow {
            checkSvc.bandwidthFinish()
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────────

    private fun buildCheckService(
        withIpService: Boolean = false,
    ): RequestLimitCheckService {
        val svc = RequestLimitCheckService(props)
        fun injectField(name: String, value: Any) {
            val f = RequestLimitCheckService::class.java.getDeclaredField(name)
            f.isAccessible = true
            f.set(svc, value)
        }
        injectField("urlRepoRateLimiterService", urlRepoSvc)
        injectField("urlRateLimiterService", urlSvc)
        injectField("userUrlRepoRateLimiterService", userUrlRepoSvc)
        injectField("userUrlRateLimiterService", userUrlSvc)
        injectField("uploadUsageRateLimiterService", uploadUsageSvc)
        injectField("userUploadUsageRateLimiterService", userUploadUsageSvc)
        injectField("downloadUsageRateLimiterService", downloadUsageSvc)
        injectField("userDownloadUsageRateLimiterService", userDownloadUsageSvc)

        // Bandwidth services: use mocks to avoid real service setup
        listOf(
            "downloadBandwidthRateLimiterService",
            "uploadBandwidthRateLimiterService",
            "userUploadBandwidthRateLimiterService",
            "userDownloadBandwidthRateLimiterService",
            "urlUploadBandwidthRateLimiterService",
            "urlDownloadBandwidthRateLimiterService",
        ).forEach { name ->
            runCatching {
                val f = RequestLimitCheckService::class.java.getDeclaredField(name)
                f.isAccessible = true
                if (f.get(svc) == null) f.set(svc, mock(f.type))
            }
        }
        return svc
    }

    private fun makeRequest(method: String, uri: String): MockHttpServletRequest {
        return MockHttpServletRequest(method, uri)
    }

    private fun setRequest(method: String, uri: String): MockHttpServletRequest {
        val req = MockHttpServletRequest(method, uri)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(req))
        return req
    }
}
