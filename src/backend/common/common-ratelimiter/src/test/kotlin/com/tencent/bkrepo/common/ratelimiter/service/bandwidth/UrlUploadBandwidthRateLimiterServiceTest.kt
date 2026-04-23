/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.ratelimiter.service.bandwidth

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.UrlUploadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.util.unit.DataSize
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UrlUploadBandwidthRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/blueking/generic-local/test.txt",
        limitDimension = LimitDimension.URL_UPLOAD_BANDWIDTH.name, limit = 10,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )

    @BeforeAll
    fun before() {
        init()
        rateLimiterProperties.enabled = true
        rateLimiterProperties.rules = listOf(l1)
        request.requestURI = "/blueking/generic-local/test.txt"
        request.method = "PUT"
        val content = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        request.setContent(content)
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = UrlUploadBandwidthRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as UrlUploadBandwidthRateLimiterService).refreshRateLimitRule()
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
    override fun refreshRateLimitRuleChangeTest() {
        super.refreshRateLimitRuleChangeTest()
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
    fun ignoreRequestTest() {
        Assertions.assertFalse((rateLimiterService as UrlUploadBandwidthRateLimiterService).ignoreRequest(request))
    }

    @Test
    fun buildResourceTest() {
        Assertions.assertEquals(
            "/blueking/generic-local/test.txt",
            (rateLimiterService as UrlUploadBandwidthRateLimiterService).buildResource(request)
        )
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            emptyList<String>(),
            (rateLimiterService as UrlUploadBandwidthRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertEquals(
            50L,
            (rateLimiterService as UrlUploadBandwidthRateLimiterService).getApplyPermits(request, 50L)
        )
        Assertions.assertEquals(
            0L,
            (rateLimiterService as UrlUploadBandwidthRateLimiterService).getApplyPermits(request, null)
        )
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            UrlUploadBandwidthRateLimitRule::class.java,
            (rateLimiterService as UrlUploadBandwidthRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.URL_UPLOAD_BANDWIDTH.name),
            (rateLimiterService as UrlUploadBandwidthRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as UrlUploadBandwidthRateLimiterService).buildResource(request)
        val resInfo = ResInfo(resource = resource, extraResource = emptyList())
        val resourceLimit = (rateLimiterService as UrlUploadBandwidthRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UrlUploadBandwidth:/blueking/generic-local/test.txt",
            (rateLimiterService as UrlUploadBandwidthRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )
    }

    @Test
    fun limitTest() {
        Assertions.assertThrows(UnsupportedOperationException::class.java) {
            (rateLimiterService as UrlUploadBandwidthRateLimiterService).limit(request)
        }
    }

    @Test
    fun bandwidthRateLimitTest() {
        val svc = rateLimiterService as UrlUploadBandwidthRateLimiterService
        svc.bandwidthRateLimit(request = request, permits = 1, circuitBreakerPerSecond = DataSize.ofBytes(0))
        svc.bandwidthRateLimit(request = request, permits = 10000, circuitBreakerPerSecond = DataSize.ofBytes(0))
        Assertions.assertThrows(OverloadException::class.java) {
            svc.bandwidthRateLimit(request = request, permits = 1, circuitBreakerPerSecond = DataSize.ofTerabytes(1))
        }
    }

    @Test
    fun bandwidthLimitHandlerTest() {
        val svc = rateLimiterService as UrlUploadBandwidthRateLimiterService
        val resLimitInfo = svc.getResLimitInfoAndResInfo(request).first
        Assertions.assertNotNull(resLimitInfo)
        val rateLimiter = svc.getAlgorithmOfRateLimiter(resLimitInfo!!.resource, resLimitInfo.resourceLimit)
        Assertions.assertTrue(svc.bandwidthLimitHandler(rateLimiter, 1))
        Assertions.assertTrue(svc.bandwidthLimitHandler(rateLimiter, 100))
    }
}
