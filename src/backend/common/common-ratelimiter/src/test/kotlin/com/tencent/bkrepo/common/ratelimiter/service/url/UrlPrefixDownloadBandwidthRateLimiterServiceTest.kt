package com.tencent.bkrepo.common.ratelimiter.service.url

import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlPrefixDownloadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.util.unit.DataSize
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UrlPrefixDownloadBandwidthRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/proj/repo/bigfiles/",
        limitDimension = LimitDimension.URL_PREFIX_DOWNLOAD_BANDWIDTH.name,
        limit = 100,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name
    )

    @BeforeAll
    fun before() {
        init()
        rateLimiterProperties.enabled = true
        rateLimiterProperties.rules = listOf(l1)
        request.requestURI = "/proj/repo/bigfiles/file.zip"
        request.method = "GET"
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = UrlPrefixDownloadBandwidthRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun `should ignore non-download methods`() {
        val svc = rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService
        request.method = "PUT"
        Assertions.assertEquals(true, svc.ignoreRequest(request))
        request.method = "POST"
        Assertions.assertEquals(true, svc.ignoreRequest(request))
        request.method = "GET"
        Assertions.assertEquals(false, svc.ignoreRequest(request))
        request.method = "GET"
    }

    @Test
    fun `buildResource normalizes requestURI`() {
        val svc = rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService
        Assertions.assertEquals("/proj/repo/bigfiles/file.zip", svc.buildResource(request))
    }

    @Test
    fun `buildResource normalizes consecutive slashes`() {
        val svc = rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService
        request.requestURI = "///proj//repo//bigfiles//file.zip"
        Assertions.assertEquals("/proj/repo/bigfiles/file.zip", svc.buildResource(request))
        request.requestURI = "/proj/repo/bigfiles/file.zip"
    }

    @Test
    fun `buildResource normalizes URL-encoded slashes`() {
        val svc = rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService
        request.requestURI = "/proj/repo%2Fbigfiles%2Ffile.zip"
        Assertions.assertEquals("/proj/repo/bigfiles/file.zip", svc.buildResource(request))
        request.requestURI = "/proj/repo/bigfiles/file.zip"
    }

    @Test
    fun `getApplyPermits throws when null`() {
        Assertions.assertThrows(AcquireLockFailedException::class.java) {
            (rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService).getApplyPermits(request, null)
        }
        Assertions.assertEquals(
            50L,
            (rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService).getApplyPermits(request, 50)
        )
    }

    @Test
    fun `getRateLimitRuleClass returns UrlPrefixDownloadBandwidthRateLimitRule`() {
        Assertions.assertEquals(
            UrlPrefixDownloadBandwidthRateLimitRule::class.java,
            (rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun `getLimitDimensions returns URL_PREFIX_DOWNLOAD_BANDWIDTH`() {
        Assertions.assertEquals(
            listOf(LimitDimension.URL_PREFIX_DOWNLOAD_BANDWIDTH.name),
            (rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun `generateKey uses rule prefix so all sub-paths share one bucket`() {
        val svc = rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService

        request.requestURI = "/proj/repo/bigfiles/a.zip"
        val resource1 = svc.buildResource(request)
        val limitInfo1 = svc.rateLimitRule?.getRateLimitRule(ResInfo(resource1, emptyList()))
        Assertions.assertNotNull(limitInfo1)

        request.requestURI = "/proj/repo/bigfiles/b.zip"
        val resource2 = svc.buildResource(request)
        val limitInfo2 = svc.rateLimitRule?.getRateLimitRule(ResInfo(resource2, emptyList()))
        Assertions.assertNotNull(limitInfo2)

        val key1 = svc.generateKey(limitInfo1!!.resource, limitInfo1.resourceLimit)
        val key2 = svc.generateKey(limitInfo2!!.resource, limitInfo2.resourceLimit)
        Assertions.assertEquals(key1, key2)
        Assertions.assertEquals(KEY_PREFIX + "UrlPrefixDownloadBandwidth:/proj/repo/bigfiles/", key1)

        request.requestURI = "/proj/repo/bigfiles/file.zip"
    }

    @Test
    fun `path outside prefix returns null rule`() {
        val svc = rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService
        request.requestURI = "/other/repo/file.zip"
        val resource = svc.buildResource(request)
        val limitInfo = svc.rateLimitRule?.getRateLimitRule(ResInfo(resource, emptyList()))
        Assertions.assertNull(limitInfo)
        request.requestURI = "/proj/repo/bigfiles/file.zip"
    }

    @Test
    fun `bandwidthRateLimitTest`() {
        (rateLimiterService as UrlPrefixDownloadBandwidthRateLimiterService).bandwidthRateLimit(
            request = request,
            permits = 1,
            circuitBreakerPerSecond = DataSize.ofBytes(0)
        )
    }
}
