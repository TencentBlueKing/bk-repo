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
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.UploadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.util.unit.DataSize
import org.springframework.web.servlet.HandlerMapping
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UploadBandwidthRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/*/",
        limitDimension = LimitDimension.UPLOAD_BANDWIDTH.name, limit = 10,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )

    @BeforeAll
    fun before() {
        init()
        rateLimiterProperties.enabled = true
        rateLimiterProperties.rules = listOf(l1)
        request.requestURI = "/blueking/generic-local/test.txt"
        val uriVariables: MutableMap<String, String> = HashMap()
        uriVariables["projectId"] = "blueking"
        uriVariables["repoName"] = "generic-local"
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriVariables)
        val content = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        request.setContent(content)
        request.method = "PUT"
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = UploadBandwidthRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as UploadBandwidthRateLimiterService).refreshRateLimitRule()
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
    fun ignoreRequestTest() {
        Assertions.assertEquals(false, (rateLimiterService as UploadBandwidthRateLimiterService).ignoreRequest(request))
        request.method = "POST"
        Assertions.assertEquals(false, (rateLimiterService as UploadBandwidthRateLimiterService).ignoreRequest(request))
        request.method = "PATCH"
        Assertions.assertEquals(false, (rateLimiterService as UploadBandwidthRateLimiterService).ignoreRequest(request))
        request.method = "GET"
        Assertions.assertEquals(true, (rateLimiterService as UploadBandwidthRateLimiterService).ignoreRequest(request))
        request.method = "PUT"
    }

    @Test
    fun getRepoInfoTest() {
        val (projectId, repoName) = (rateLimiterService as UploadBandwidthRateLimiterService).getRepoInfoFromAttribute(
            request
        )
        Assertions.assertEquals("blueking", projectId)
        Assertions.assertEquals("generic-local", repoName)

    }

    @Test
    fun buildResourceTest() {
        Assertions.assertEquals(
            "/blueking/generic-local/",
            (rateLimiterService as UploadBandwidthRateLimiterService).buildResource(request)
        )
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            listOf("/blueking/"),
            (rateLimiterService as UploadBandwidthRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertThrows(AcquireLockFailedException::class.java) {
            (rateLimiterService as UploadBandwidthRateLimiterService).getApplyPermits(request, null)
        }
        Assertions.assertEquals(
            10,
            (rateLimiterService as UploadBandwidthRateLimiterService).getApplyPermits(request, 10)
        )
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            UploadBandwidthRateLimitRule::class.java,
            (rateLimiterService as UploadBandwidthRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.UPLOAD_BANDWIDTH.name),
            (rateLimiterService as UploadBandwidthRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as UploadBandwidthRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UploadBandwidthRateLimiterService).buildExtraResource(request)
        )
        var resourceLimit = (rateLimiterService as UploadBandwidthRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UploadBandwidth:" + "/blueking/",
            (rateLimiterService as UploadBandwidthRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )
        l1.resource = "/blueking/generic-local/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UploadBandwidthRateLimiterService).refreshRateLimitRule()
        resourceLimit = (rateLimiterService as UploadBandwidthRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UploadBandwidth:" + "/blueking/generic-local/",
            (rateLimiterService as UploadBandwidthRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )
        l1.resource = "/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UploadBandwidthRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun generateKeyTestNull() {
        val resource = (rateLimiterService as UploadBandwidthRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UploadBandwidthRateLimiterService).buildExtraResource(request)
        )
        l1.resource = "/test/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UploadBandwidthRateLimiterService).refreshRateLimitRule()
        val resourceLimit = (rateLimiterService as UploadBandwidthRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNull(resourceLimit)

        l1.resource = "/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UploadBandwidthRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun limitTest() {
        Assertions.assertThrows(UnsupportedOperationException::class.java) {
            (rateLimiterService as UploadBandwidthRateLimiterService).limit(request)
        }
    }

    @Test
    fun bandwidthRateLimitTest() {
        (rateLimiterService as UploadBandwidthRateLimiterService).bandwidthRateLimit(
            request = request,
            permits = 1,
            circuitBreakerPerSecond = DataSize.ofBytes(0)
        )
        (rateLimiterService as UploadBandwidthRateLimiterService).bandwidthRateLimit(
            request = request,
            permits = 10000,
            circuitBreakerPerSecond = DataSize.ofBytes(0)
        )
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UploadBandwidthRateLimiterService).bandwidthRateLimit(
                request = request,
                permits = 1,
                circuitBreakerPerSecond = DataSize.ofTerabytes(1)
            )
        }
    }

    @Test
    fun bandwidthRateLimit1Test() {
        val content = "1234567891"
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UploadBandwidthRateLimiterService).bandwidthRateStart(
                request = request,
                inputStream = content.byteInputStream(),
                circuitBreakerPerSecond = DataSize.ofTerabytes(1),
                rangeLength = null
            )
        }
        (rateLimiterService as UploadBandwidthRateLimiterService).bandwidthRateStart(
            request = request,
            inputStream = content.byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(1),
            rangeLength = null
        )
    }

    @Test
    fun bandwidthLimitHandlerTest() {
        val resourceLimit =
            (rateLimiterService as UploadBandwidthRateLimiterService).getResLimitInfoAndResInfo(request).first
        Assertions.assertNotNull(resourceLimit)
        assertEqualsLimitInfo(resourceLimit!!.resourceLimit, rateLimiterProperties.rules.first())
        val rateLimiter = (rateLimiterService as UploadBandwidthRateLimiterService)
            .getAlgorithmOfRateLimiter(resourceLimit.resource, resourceLimit.resourceLimit)
        Assertions.assertEquals(
            true,
            (rateLimiterService as UploadBandwidthRateLimiterService).bandwidthLimitHandler(rateLimiter, 1)
        )
        Assertions.assertEquals(
            true,
            (rateLimiterService as UploadBandwidthRateLimiterService).bandwidthLimitHandler(rateLimiter, 100)
        )
    }


}