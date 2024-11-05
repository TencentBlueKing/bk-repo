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

package com.tencent.bkrepo.common.ratelimiter.service.url.user

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.user.UserUrlRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserUrlRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "*:/",
        limitDimension = LimitDimension.USER_URL.name, limit = 1,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )

    @BeforeAll
    fun before() {
        init()
        rateLimiterProperties.enabled = true
        rateLimiterProperties.rules = listOf(l1)
        request.requestURI = "/blueking/generic-local/test.txt"
        request.setAttribute("userId", "admin")
        val attributes = ServletRequestAttributes(request)
        RequestContextHolder.setRequestAttributes(attributes)
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = UserUrlRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as UserUrlRateLimiterService).refreshRateLimitRule()
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
        Assertions.assertEquals(false, (rateLimiterService as UserUrlRateLimiterService).ignoreRequest(request))
    }

    @Test
    fun buildResourceTest() {
        Assertions.assertEquals(
            "admin:/blueking/generic-local/test.txt",
            (rateLimiterService as UserUrlRateLimiterService).buildResource(request)
        )
        request.requestURI = "/api/node/batch/blueking/generic-local"
        request.setAttribute("userId", "test")
        Assertions.assertEquals(
            "test:/api/node/batch/blueking/generic-local",
            (rateLimiterService as UserUrlRateLimiterService).buildResource(request)
        )
        request.requestURI = "/blueking/generic-local/test.txt"
        request.setAttribute("userId", "admin")
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            listOf("admin:"),
            (rateLimiterService as UserUrlRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertEquals(
            1,
            (rateLimiterService as UserUrlRateLimiterService).getApplyPermits(request, null)
        )
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            UserUrlRateLimitRule::class.java,
            (rateLimiterService as UserUrlRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.USER_URL.name),
            (rateLimiterService as UserUrlRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as UserUrlRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UserUrlRateLimiterService).buildExtraResource(request)
        )
        var resourceLimit = (rateLimiterService as UserUrlRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UserUrl:" + "admin:/blueking/generic-local/test.txt",
            (rateLimiterService as UserUrlRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )
        l1.resource = "admin:"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRateLimiterService).refreshRateLimitRule()

        resourceLimit = (rateLimiterService as UserUrlRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UserUrl:" + "admin:",
            (rateLimiterService as UserUrlRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )

        l1.resource = "*:/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun generateKeyTestNull() {
        l1.resource = "test:"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRateLimiterService).refreshRateLimitRule()

        val resource = (rateLimiterService as UserUrlRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UserUrlRateLimiterService).buildExtraResource(request)
        )
        var resourceLimit = (rateLimiterService as UserUrlRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNull(resourceLimit)

        l1.resource = "test:/blueking/generic-local/test.txt"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRateLimiterService).refreshRateLimitRule()
        resourceLimit = (rateLimiterService as UserUrlRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNull(resourceLimit)


        l1.resource = "*:/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun limitTest() {
        // 本地限流验证
        (rateLimiterService as UserUrlRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUrlRateLimiterService).limit(request)
        }

        // 分布式算法验证
        l1.scope = WorkScope.GLOBAL.name
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UserUrlRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUrlRateLimiterService).limit(request)
        }

        l1.resource = "admin:/blueking/"
        l1.limit = 2
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UserUrlRateLimiterService).limit(request)
        (rateLimiterService as UserUrlRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUrlRateLimiterService).limit(request)
        }

        l1.resource = "admin:"
        l1.limit = 1
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UserUrlRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUrlRateLimiterService).limit(request)
        }

        l1.resource = "test:/blueking"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UserUrlRateLimiterService).limit(request)
        (rateLimiterService as UserUrlRateLimiterService).limit(request)
        Assertions.assertDoesNotThrow { (rateLimiterService as UserUrlRateLimiterService).limit(request) }
    }


}