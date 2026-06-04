package com.tencent.bkrepo.common.ratelimiter.service.url

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlPrefixUploadRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UrlPrefixUploadRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name,
        resource = "/generic/proj/repo/",
        limitDimension = LimitDimension.URL_PREFIX_UPLOAD_RATE.name,
        limit = 2,
        duration = Duration.ofSeconds(1),
        scope = WorkScope.LOCAL.name
    )

    @BeforeAll
    fun before() {
        init()
        rateLimiterProperties.enabled = true
        rateLimiterProperties.rules = listOf(l1)
        request.requestURI = "/generic/proj/repo/upload/file.zip"
        request.method = "PUT"
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = UrlPrefixUploadRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as UrlPrefixUploadRateLimiterService).refreshRateLimitRule()
    }

    @Test
    override fun createAlgorithmOfRateLimiterTest() {
        super.createAlgorithmOfRateLimiterTest()
    }

    @Test
    override fun refreshRateLimitRuleTest() {
        super.refreshRateLimitRuleTest()
    }

    @Test
    override fun getAlgorithmOfRateLimiterTest() {
        super.getAlgorithmOfRateLimiterTest()
    }

    @Test
    override fun getResLimitInfoTest() {
        super.getResLimitInfoTest()
    }

    @Test
    override fun circuitBreakerCheckTest() {
        super.circuitBreakerCheckTest()
    }

    @Test
    override fun rateLimitCatchTest() {
        super.rateLimitCatchTest()
    }

    @Test
    fun `should ignore non-upload methods`() {
        val svc = rateLimiterService as UrlPrefixUploadRateLimiterService
        request.method = "GET"
        Assertions.assertEquals(true, svc.ignoreRequest(request))
        request.method = "DELETE"
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
    fun `buildResource returns requestURI`() {
        Assertions.assertEquals(
            "/generic/proj/repo/upload/file.zip",
            (rateLimiterService as UrlPrefixUploadRateLimiterService).buildResource(request)
        )
    }

    @Test
    fun `buildExtraResource returns empty list`() {
        Assertions.assertEquals(
            emptyList<String>(),
            (rateLimiterService as UrlPrefixUploadRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun `getApplyPermits always returns 1`() {
        Assertions.assertEquals(
            1L,
            (rateLimiterService as UrlPrefixUploadRateLimiterService).getApplyPermits(request, null)
        )
    }

    @Test
    fun `getRateLimitRuleClass returns UrlPrefixUploadRateLimitRule`() {
        Assertions.assertEquals(
            UrlPrefixUploadRateLimitRule::class.java,
            (rateLimiterService as UrlPrefixUploadRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun `getLimitDimensions returns URL_PREFIX_UPLOAD_RATE`() {
        Assertions.assertEquals(
            listOf(LimitDimension.URL_PREFIX_UPLOAD_RATE.name),
            (rateLimiterService as UrlPrefixUploadRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun `generateKey uses matched prefix`() {
        val svc = rateLimiterService as UrlPrefixUploadRateLimiterService
        val resource = svc.buildResource(request)
        val resInfo = ResInfo(resource = resource, extraResource = svc.buildExtraResource(request))
        val resLimitInfo = svc.rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resLimitInfo)
        Assertions.assertEquals(
            KEY_PREFIX + "UrlPrefixUploadRate:/generic/proj/repo/",
            svc.generateKey(resLimitInfo!!.resource, resLimitInfo.resourceLimit)
        )
    }

    @Test
    fun `prefix rule applies to sub-paths with shared counter`() {
        val svc = rateLimiterService as UrlPrefixUploadRateLimiterService
        request.requestURI = "/generic/proj/repo/path/a.zip"
        val resA = ResInfo(resource = svc.buildResource(request), extraResource = emptyList())
        request.requestURI = "/generic/proj/repo/path/b.zip"
        val resB = ResInfo(resource = svc.buildResource(request), extraResource = emptyList())
        val limitA = svc.rateLimitRule?.getRateLimitRule(resA)
        val limitB = svc.rateLimitRule?.getRateLimitRule(resB)
        Assertions.assertNotEquals(limitA?.resource, limitB?.resource)
        Assertions.assertEquals(
            svc.generateKey(limitA!!.resource, limitA.resourceLimit),
            svc.generateKey(limitB!!.resource, limitB.resourceLimit)
        )
        request.requestURI = "/generic/proj/repo/upload/file.zip"
    }

    @Test
    fun `limit should reject requests beyond threshold`() {
        request.requestURI = "/generic/proj/repo/upload/unique-file.zip"
        val svc = rateLimiterService as UrlPrefixUploadRateLimiterService
        svc.limit(request)
        svc.limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            svc.limit(request)
        }
        request.requestURI = "/generic/proj/repo/upload/file.zip"
    }

    @Test
    fun `malformed URI with consecutive slashes still matches rule`() {
        val svc = rateLimiterService as UrlPrefixUploadRateLimiterService
        request.requestURI = "///generic//proj//repo//upload/file.zip"
        val resInfo = ResInfo(resource = svc.buildResource(request), extraResource = emptyList())
        Assertions.assertNotNull(svc.rateLimitRule?.getRateLimitRule(resInfo))
        Assertions.assertEquals(
            KEY_PREFIX + "UrlPrefixUploadRate:/generic/proj/repo/",
            svc.generateKey(resInfo.resource, l1)
        )
        request.requestURI = "/generic/proj/repo/upload/file.zip"
    }

    @Test
    fun `malformed URI with URL-encoded slashes still matches rule`() {
        val svc = rateLimiterService as UrlPrefixUploadRateLimiterService
        request.requestURI = "/generic/proj/repo%2Fupload%2Ffile.zip"
        val resInfo = ResInfo(resource = svc.buildResource(request), extraResource = emptyList())
        Assertions.assertNotNull(svc.rateLimitRule?.getRateLimitRule(resInfo))
        Assertions.assertEquals(
            KEY_PREFIX + "UrlPrefixUploadRate:/generic/proj/repo/",
            svc.generateKey(resInfo.resource, l1)
        )
        request.requestURI = "/generic/proj/repo/upload/file.zip"
    }
}
