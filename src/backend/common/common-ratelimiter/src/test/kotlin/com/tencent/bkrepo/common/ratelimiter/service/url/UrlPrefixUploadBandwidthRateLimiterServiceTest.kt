package com.tencent.bkrepo.common.ratelimiter.service.url

import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlPrefixUploadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.util.unit.DataSize
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UrlPrefixUploadBandwidthRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/proj/repo/bigfiles/",
        limitDimension = LimitDimension.URL_PREFIX_UPLOAD_BANDWIDTH.name,
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
        request.method = "PUT"
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = UrlPrefixUploadBandwidthRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun `should ignore non-upload methods`() {
        val svc = rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService
        request.method = "GET"
        Assertions.assertEquals(true, svc.ignoreRequest(request))
        request.method = "PUT"
        Assertions.assertEquals(false, svc.ignoreRequest(request))
        request.method = "POST"
        Assertions.assertEquals(false, svc.ignoreRequest(request))
        request.method = "PATCH"
        Assertions.assertEquals(false, svc.ignoreRequest(request))
        request.method = "PUT"
    }

    @Test
    fun `buildResource normalizes requestURI`() {
        val svc = rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService
        Assertions.assertEquals("/proj/repo/bigfiles/file.zip", svc.buildResource(request))
    }

    @Test
    fun `buildResource normalizes consecutive slashes`() {
        val svc = rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService
        request.requestURI = "///proj//repo//bigfiles//file.zip"
        Assertions.assertEquals("/proj/repo/bigfiles/file.zip", svc.buildResource(request))
        request.requestURI = "/proj/repo/bigfiles/file.zip"
    }

    @Test
    fun `buildResource normalizes URL-encoded slashes`() {
        val svc = rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService
        request.requestURI = "/proj/repo%2Fbigfiles%2Ffile.zip"
        Assertions.assertEquals("/proj/repo/bigfiles/file.zip", svc.buildResource(request))
        request.requestURI = "/proj/repo/bigfiles/file.zip"
    }

    @Test
    fun `getApplyPermits throws when null`() {
        Assertions.assertThrows(AcquireLockFailedException::class.java) {
            (rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService).getApplyPermits(request, null)
        }
        Assertions.assertEquals(
            50L,
            (rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService).getApplyPermits(request, 50)
        )
    }

    @Test
    fun `getRateLimitRuleClass returns UrlPrefixUploadBandwidthRateLimitRule`() {
        Assertions.assertEquals(
            UrlPrefixUploadBandwidthRateLimitRule::class.java,
            (rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun `getLimitDimensions returns URL_PREFIX_UPLOAD_BANDWIDTH`() {
        Assertions.assertEquals(
            listOf(LimitDimension.URL_PREFIX_UPLOAD_BANDWIDTH.name),
            (rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun `generateKey uses rule prefix so all sub-paths share one bucket`() {
        val svc = rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService

        // file a.zip
        request.requestURI = "/proj/repo/bigfiles/a.zip"
        val resource1 = svc.buildResource(request)
        val resInfo1 = ResInfo(resource1, svc.buildExtraResource(request))
        val limitInfo1 = svc.rateLimitRule?.getRateLimitRule(resInfo1)
        Assertions.assertNotNull(limitInfo1)

        // file b.zip
        request.requestURI = "/proj/repo/bigfiles/b.zip"
        val resource2 = svc.buildResource(request)
        val resInfo2 = ResInfo(resource2, svc.buildExtraResource(request))
        val limitInfo2 = svc.rateLimitRule?.getRateLimitRule(resInfo2)
        Assertions.assertNotNull(limitInfo2)

        // Both should use the rule prefix as key → same bucket
        val key1 = svc.generateKey(limitInfo1!!.resource, limitInfo1.resourceLimit)
        val key2 = svc.generateKey(limitInfo2!!.resource, limitInfo2.resourceLimit)
        Assertions.assertEquals(key1, key2)
        Assertions.assertEquals(KEY_PREFIX + "UrlPrefixUploadBandwidth:/proj/repo/bigfiles/", key1)

        request.requestURI = "/proj/repo/bigfiles/file.zip"
    }

    @Test
    fun `different prefix rules produce different keys`() {
        val svc = rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService
        val otherRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name,
            resource = "/other/repo/files/",
            limitDimension = LimitDimension.URL_PREFIX_UPLOAD_BANDWIDTH.name,
            limit = 50,
            duration = Duration.ofSeconds(1),
            scope = WorkScope.LOCAL.name
        )
        rateLimiterProperties.rules = listOf(l1, otherRule)
        svc.refreshRateLimitRule()

        request.requestURI = "/proj/repo/bigfiles/x.zip"
        val res1 = svc.buildResource(request)
        val info1 = svc.rateLimitRule?.getRateLimitRule(ResInfo(res1, emptyList()))

        request.requestURI = "/other/repo/files/y.zip"
        val res2 = svc.buildResource(request)
        val info2 = svc.rateLimitRule?.getRateLimitRule(ResInfo(res2, emptyList()))

        Assertions.assertNotNull(info1)
        Assertions.assertNotNull(info2)
        Assertions.assertNotEquals(
            svc.generateKey(info1!!.resource, info1.resourceLimit),
            svc.generateKey(info2!!.resource, info2.resourceLimit)
        )

        rateLimiterProperties.rules = listOf(l1)
        svc.refreshRateLimitRule()
        request.requestURI = "/proj/repo/bigfiles/file.zip"
    }

    @Test
    fun `bandwidthRateLimitTest`() {
        (rateLimiterService as UrlPrefixUploadBandwidthRateLimiterService).bandwidthRateLimit(
            request = request,
            permits = 1,
            circuitBreakerPerSecond = DataSize.ofBytes(0)
        )
    }
}
