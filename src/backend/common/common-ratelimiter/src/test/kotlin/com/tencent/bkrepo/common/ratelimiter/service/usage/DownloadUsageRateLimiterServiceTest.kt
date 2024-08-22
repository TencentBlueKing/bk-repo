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

package com.tencent.bkrepo.common.ratelimiter.service.usage

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.usage.DownloadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.servlet.HandlerMapping
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DownloadUsageRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/*/",
        limitDimension = LimitDimension.DOWNLOAD_USAGE.name, limit = 10,
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
        request.method = "GET"
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = DownloadUsageRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics
        )
        (rateLimiterService as DownloadUsageRateLimiterService).refreshRateLimitRule()
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
        Assertions.assertEquals(false, (rateLimiterService as DownloadUsageRateLimiterService).ignoreRequest(request))
        request.method = "POST"
        Assertions.assertEquals(true, (rateLimiterService as DownloadUsageRateLimiterService).ignoreRequest(request))
        request.method = "PATCH"
        Assertions.assertEquals(true, (rateLimiterService as DownloadUsageRateLimiterService).ignoreRequest(request))
        request.method = "PUT"
        Assertions.assertEquals(true, (rateLimiterService as DownloadUsageRateLimiterService).ignoreRequest(request))
        request.method = "HEAD"
        Assertions.assertEquals(true, (rateLimiterService as DownloadUsageRateLimiterService).ignoreRequest(request))
        request.method = "GET"
    }

    @Test
    fun getRepoInfoTest() {
        val (projectId, repoName) = (rateLimiterService as DownloadUsageRateLimiterService).getRepoInfo(request)
        Assertions.assertEquals("blueking", projectId)
        Assertions.assertEquals("generic-local", repoName)

    }

    @Test
    fun buildResourceTest() {
        Assertions.assertEquals(
            "/blueking/generic-local/",
            (rateLimiterService as DownloadUsageRateLimiterService).buildResource(request)
        )
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            listOf("/blueking/"),
            (rateLimiterService as DownloadUsageRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertThrows(AcquireLockFailedException::class.java) {
            (rateLimiterService as DownloadUsageRateLimiterService).getApplyPermits(request, null)
        }

        Assertions.assertEquals(
            10,
            (rateLimiterService as DownloadUsageRateLimiterService).getApplyPermits(request, 10)
        )
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            DownloadUsageRateLimitRule::class.java,
            (rateLimiterService as DownloadUsageRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.DOWNLOAD_USAGE.name),
            (rateLimiterService as DownloadUsageRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as DownloadUsageRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as DownloadUsageRateLimiterService).buildExtraResource(request)
        )
        var resourceLimit = (rateLimiterService as DownloadUsageRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "DownloadUsage:" + "/blueking/",
            (rateLimiterService as DownloadUsageRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )
        l1.resource = "/blueking/generic-local/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as DownloadUsageRateLimiterService).refreshRateLimitRule()
        resourceLimit = (rateLimiterService as DownloadUsageRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "DownloadUsage:" + "/blueking/generic-local/",
            (rateLimiterService as DownloadUsageRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )
        l1.resource = "/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as DownloadUsageRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun generateKeyTestNull() {
        val resource = (rateLimiterService as DownloadUsageRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as DownloadUsageRateLimiterService).buildExtraResource(request)
        )
        l1.resource = "/test/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as DownloadUsageRateLimiterService).refreshRateLimitRule()
        val resourceLimit = (rateLimiterService as DownloadUsageRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNull(resourceLimit)

        l1.resource = "/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as DownloadUsageRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun limitTest() {
        // 本地限流验证
        (rateLimiterService as DownloadUsageRateLimiterService).limit(request, 10)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as DownloadUsageRateLimiterService).limit(request, 10)
        }

        // 分布式算法验证
        l1.scope = WorkScope.GLOBAL.name
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as DownloadUsageRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as DownloadUsageRateLimiterService).limit(request, 10)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as DownloadUsageRateLimiterService).limit(request, 10)
        }

        l1.resource = "/blueking/"
        l1.limit = 2
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as DownloadUsageRateLimiterService).refreshRateLimitRule()
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as DownloadUsageRateLimiterService).limit(request, 10)
        }

        l1.resource = "/blueking/"
        l1.limit = 20
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as DownloadUsageRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as DownloadUsageRateLimiterService).limit(request, 10)
        (rateLimiterService as DownloadUsageRateLimiterService).limit(request, 10)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as DownloadUsageRateLimiterService).limit(request, 10)
        }
    }

}