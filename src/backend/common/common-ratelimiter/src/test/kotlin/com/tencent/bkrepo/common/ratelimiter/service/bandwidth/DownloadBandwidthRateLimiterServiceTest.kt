/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.DownloadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.servlet.HandlerMapping
import java.util.concurrent.TimeUnit

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DownloadBandwidthRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/*/",
        limitDimension = LimitDimension.DOWNLOAD_BANDWIDTH.name, limit = 10,
        unit = TimeUnit.SECONDS.name, scope = WorkScope.LOCAL.name
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
        request.method = "GET"
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = DownloadBandwidthRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics
        )
        (rateLimiterService as DownloadBandwidthRateLimiterService).refreshRateLimitRule()
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
        Assertions.assertEquals(
            false,
            (rateLimiterService as DownloadBandwidthRateLimiterService).ignoreRequest(request)
        )
        request.method = "POST"
        Assertions.assertEquals(
            true,
            (rateLimiterService as DownloadBandwidthRateLimiterService).ignoreRequest(request)
        )
        request.method = "PATCH"
        Assertions.assertEquals(
            true,
            (rateLimiterService as DownloadBandwidthRateLimiterService).ignoreRequest(request)
        )
        request.method = "PUT"
        Assertions.assertEquals(
            true,
            (rateLimiterService as DownloadBandwidthRateLimiterService).ignoreRequest(request)
        )
        request.method = "GET"
    }

    @Test
    fun getRepoInfoTest() {
        val (projectId, repoName) = (rateLimiterService as DownloadBandwidthRateLimiterService).getRepoInfo(request)
        Assertions.assertEquals("blueking", projectId)
        Assertions.assertEquals("generic-local", repoName)

    }

    @Test
    fun buildResourceTest() {
        Assertions.assertEquals(
            "/blueking/generic-local/",
            (rateLimiterService as DownloadBandwidthRateLimiterService).buildResource(request)
        )
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            listOf("/blueking/"),
            (rateLimiterService as DownloadBandwidthRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertThrows(AcquireLockFailedException::class.java) {
            (rateLimiterService as DownloadBandwidthRateLimiterService).getApplyPermits(request, null)
        }
        Assertions.assertEquals(
            10,
            (rateLimiterService as DownloadBandwidthRateLimiterService).getApplyPermits(request, 10)
        )
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            DownloadBandwidthRateLimitRule::class.java,
            (rateLimiterService as DownloadBandwidthRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.DOWNLOAD_BANDWIDTH.name),
            (rateLimiterService as DownloadBandwidthRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as DownloadBandwidthRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as DownloadBandwidthRateLimiterService).buildExtraResource(request)
        )
        var resourceLimit = (rateLimiterService as DownloadBandwidthRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "DownloadBandwidth:" + "/blueking/",
            (rateLimiterService as DownloadBandwidthRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )
        l1.resource = "/blueking/generic-local/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as DownloadBandwidthRateLimiterService).refreshRateLimitRule()
        resourceLimit = (rateLimiterService as DownloadBandwidthRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "DownloadBandwidth:" + "/blueking/generic-local/",
            (rateLimiterService as DownloadBandwidthRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )
        l1.resource = "/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as DownloadBandwidthRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun generateKeyTestNull() {
        val resource = (rateLimiterService as DownloadBandwidthRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as DownloadBandwidthRateLimiterService).buildExtraResource(request)
        )
        l1.resource = "/test/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as DownloadBandwidthRateLimiterService).refreshRateLimitRule()
        val resourceLimit = (rateLimiterService as DownloadBandwidthRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNull(resourceLimit)

        l1.resource = "/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as DownloadBandwidthRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun limitTest() {
        Assertions.assertThrows(UnsupportedOperationException::class.java) { rateLimiterService.limit(request) }
    }
}