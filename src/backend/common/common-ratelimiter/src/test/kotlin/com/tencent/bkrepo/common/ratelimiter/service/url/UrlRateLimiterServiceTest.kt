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

package com.tencent.bkrepo.common.ratelimiter.service.url

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UrlRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/",
        limitDimension = LimitDimension.URL.name, limit = 1,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )

    @BeforeAll
    fun before() {
        init()
        rateLimiterProperties.enabled = true
        rateLimiterProperties.rules = listOf(l1)
        request.requestURI = "/blueking/generic-local/test.txt"
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = UrlRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as UrlRateLimiterService).refreshRateLimitRule()
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
        Assertions.assertEquals(false, (rateLimiterService as UrlRateLimiterService).ignoreRequest(request))
    }

    @Test
    fun buildResourceTest() {
        Assertions.assertEquals(
            "/blueking/generic-local/test.txt",
            (rateLimiterService as UrlRateLimiterService).buildResource(request)
        )
        request.requestURI = "/api/node/batch/blueking/generic-local"
        Assertions.assertEquals(
            "/api/node/batch/blueking/generic-local",
            (rateLimiterService as UrlRateLimiterService).buildResource(request)
        )
        request.requestURI = "/blueking/generic-local/test.txt"
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            emptyList<String>(),
            (rateLimiterService as UrlRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertEquals(1, (rateLimiterService as UrlRateLimiterService).getApplyPermits(request, null))
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            UrlRateLimitRule::class.java,
            (rateLimiterService as UrlRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.URL.name),
            (rateLimiterService as UrlRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as UrlRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UrlRateLimiterService).buildExtraResource(request)
        )
        val resourceLimit = (rateLimiterService as UrlRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "Url:" + "/blueking/generic-local/test.txt",
            (rateLimiterService as UrlRateLimiterService).generateKey(
                resourceLimit!!.resource, resourceLimit.resourceLimit
            )
        )
    }

    @Test
    fun generateKeyTestNull() {
        l1.resource = "/test/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UrlRateLimiterService).refreshRateLimitRule()

        val resource = (rateLimiterService as UrlRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UrlRateLimiterService).buildExtraResource(request)
        )
        val resourceLimit = (rateLimiterService as UrlRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNull(resourceLimit)

        l1.resource = "/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UrlRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun limitTest() {
        // 本地限流验证
        (rateLimiterService as UrlRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UrlRateLimiterService).limit(request)
        }

        // 分布式算法验证
        l1.scope = WorkScope.GLOBAL.name
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UrlRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UrlRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UrlRateLimiterService).limit(request)
        }

        l1.resource = "/blueking/"
        l1.limit = 2
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UrlRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UrlRateLimiterService).limit(request)
        (rateLimiterService as UrlRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UrlRateLimiterService).limit(request)
        }
    }

}
