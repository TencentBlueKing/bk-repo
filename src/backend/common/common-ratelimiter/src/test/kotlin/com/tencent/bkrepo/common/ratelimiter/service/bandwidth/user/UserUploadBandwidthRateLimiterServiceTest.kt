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

package com.tencent.bkrepo.common.ratelimiter.service.bandwidth.user

import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.user.UserUploadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.util.unit.DataSize
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserUploadBandwidthRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "*:/*/",
        limitDimension = LimitDimension.USER_UPLOAD_BANDWIDTH.name, limit = 10,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )

    @BeforeAll
    fun before() {
        init()
        rateLimiterProperties.enabled = true
        rateLimiterProperties.rules = listOf(l1)
        request.requestURI = "/blueking/generic-local/test.txt"
        request.setAttribute(USER_KEY, "admin")
        val uriVariables: MutableMap<String, String> = HashMap()
        uriVariables["projectId"] = "blueking"
        uriVariables["repoName"] = "generic-local"
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriVariables)
        val content = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        request.setContent(content)
        request.method = "PUT"
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = UserUploadBandwidthRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as UserUploadBandwidthRateLimiterService).refreshRateLimitRule()
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
        Assertions.assertFalse(
            (rateLimiterService as UserUploadBandwidthRateLimiterService).ignoreRequest(request)
        )
        request.method = "GET"
        Assertions.assertTrue(
            (rateLimiterService as UserUploadBandwidthRateLimiterService).ignoreRequest(request)
        )
        request.method = "PUT"
    }

    @Test
    fun buildResourceTest() {
        Assertions.assertEquals(
            "admin:/blueking/generic-local/",
            (rateLimiterService as UserUploadBandwidthRateLimiterService).buildResource(request)
        )
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            listOf("admin:/blueking/", "admin:"),
            (rateLimiterService as UserUploadBandwidthRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertThrows(AcquireLockFailedException::class.java) {
            (rateLimiterService as UserUploadBandwidthRateLimiterService).getApplyPermits(request, null)
        }
        Assertions.assertEquals(
            10L,
            (rateLimiterService as UserUploadBandwidthRateLimiterService).getApplyPermits(request, 10L)
        )
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            UserUploadBandwidthRateLimitRule::class.java,
            (rateLimiterService as UserUploadBandwidthRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.USER_UPLOAD_BANDWIDTH.name),
            (rateLimiterService as UserUploadBandwidthRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as UserUploadBandwidthRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UserUploadBandwidthRateLimiterService).buildExtraResource(request)
        )
        val resourceLimit = (rateLimiterService as UserUploadBandwidthRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UserUploadBandwidth:admin:/blueking/",
            (rateLimiterService as UserUploadBandwidthRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )
    }

    @Test
    fun limitTest() {
        Assertions.assertThrows(UnsupportedOperationException::class.java) {
            (rateLimiterService as UserUploadBandwidthRateLimiterService).limit(request)
        }
    }

    @Test
    fun bandwidthRateLimitTest() {
        val svc = rateLimiterService as UserUploadBandwidthRateLimiterService
        svc.bandwidthRateLimit(request = request, permits = 1, circuitBreakerPerSecond = DataSize.ofBytes(0))
        svc.bandwidthRateLimit(request = request, permits = 10000, circuitBreakerPerSecond = DataSize.ofBytes(0))
        Assertions.assertThrows(OverloadException::class.java) {
            svc.bandwidthRateLimit(request = request, permits = 1, circuitBreakerPerSecond = DataSize.ofTerabytes(1))
        }
    }

    @Test
    fun bandwidthRateStartTest() {
        val svc = rateLimiterService as UserUploadBandwidthRateLimiterService
        val content = "1234567891"
        Assertions.assertThrows(OverloadException::class.java) {
            svc.bandwidthRateStart(
                request = request,
                inputStream = content.byteInputStream(),
                circuitBreakerPerSecond = DataSize.ofTerabytes(1),
                rangeLength = null
            )
        }
        svc.bandwidthRateStart(
            request = request,
            inputStream = content.byteInputStream(),
            circuitBreakerPerSecond = DataSize.ofBytes(1),
            rangeLength = null
        )
    }

    @Test
    fun bandwidthLimitHandlerTest() {
        val svc = rateLimiterService as UserUploadBandwidthRateLimiterService
        val resLimitInfo = svc.getResLimitInfoAndResInfo(request).first
        Assertions.assertNotNull(resLimitInfo)
        val rateLimiter = svc.getAlgorithmOfRateLimiter(resLimitInfo!!.resource, resLimitInfo.resourceLimit)
        Assertions.assertTrue(svc.bandwidthLimitHandler(rateLimiter, 1))
        Assertions.assertTrue(svc.bandwidthLimitHandler(rateLimiter, 100))
    }
}
