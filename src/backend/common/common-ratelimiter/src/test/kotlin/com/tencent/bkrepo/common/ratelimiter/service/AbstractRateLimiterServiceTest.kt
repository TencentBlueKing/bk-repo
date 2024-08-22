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

package com.tencent.bkrepo.common.ratelimiter.service

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedFixedWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedLeakyRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedSlidingWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedTest
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedTokenBucketRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.FixedWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.LeakyRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.SlidingWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.TokenBucketRateLimiter
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Duration


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class AbstractRateLimiterServiceTest : DistributedTest() {

    lateinit var request: MockHttpServletRequest
    lateinit var rateLimiterService: RateLimiterService
    lateinit var rateLimiterProperties: RateLimiterProperties
    lateinit var taskScheduler: ThreadPoolTaskScheduler
    lateinit var rateLimiterMetrics: RateLimiterMetrics

    @MockBean
    private lateinit var meterRegistry: MeterRegistry

    fun init() {
        request = MockHttpServletRequest()
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        taskScheduler = scheduler
        rateLimiterProperties = RateLimiterProperties()
        rateLimiterMetrics = RateLimiterMetrics(meterRegistry)
    }


    open fun createAlgorithmOfRateLimiterTest() {
        val resource = (rateLimiterService as AbstractRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as AbstractRateLimiterService).buildExtraResource(request)
        )
        val resourceLimit = (rateLimiterService as AbstractRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        // 测试固定窗口本地算法生成
        var rateLimiter = (rateLimiterService as AbstractRateLimiterService)
            .createAlgorithmOfRateLimiter(resource, resourceLimit!!.resourceLimit)
        Assertions.assertInstanceOf(
            FixedWindowRateLimiter::class.java,
            rateLimiter
        )
        // 测试固定窗口分布式算法生成
        val l1 = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name, resource = "/",
            limitDimension = LimitDimension.URL.name, limit = 1,
            duration = Duration.ofSeconds(1), scope = WorkScope.GLOBAL.name
        )
        rateLimiter = (rateLimiterService as AbstractRateLimiterService).createAlgorithmOfRateLimiter(resource, l1)
        Assertions.assertInstanceOf(
            DistributedFixedWindowRateLimiter::class.java,
            rateLimiter
        )
        // 测试limit < 0的场景
        val l2 = ResourceLimit(
            algo = Algorithms.SLIDING_WINDOW.name, resource = "/project3/{(^[a-zA-Z]*\$)}/",
            limitDimension = LimitDimension.URL.name, limit = -1,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        Assertions.assertThrows(InvalidResourceException::class.java) {
            (rateLimiterService as AbstractRateLimiterService).createAlgorithmOfRateLimiter(resource, l2)
        }
        // 测试滑动窗口本地算法生成
        l2.limit = 1
        rateLimiter = (rateLimiterService as AbstractRateLimiterService).createAlgorithmOfRateLimiter(resource, l2)
        Assertions.assertInstanceOf(
            SlidingWindowRateLimiter::class.java,
            rateLimiter
        )
        // 测试滑动窗口分布式算法生成
        l2.scope = WorkScope.GLOBAL.name
        rateLimiter = (rateLimiterService as AbstractRateLimiterService).createAlgorithmOfRateLimiter(resource, l2)
        Assertions.assertInstanceOf(
            DistributedSlidingWindowRateLimiter::class.java,
            rateLimiter
        )
        // 测试不支持的算法类型
        val l3 = ResourceLimit(
            algo = "", resource = "/project3/{(^[0-9]*\$)}/",
            limitDimension = LimitDimension.URL.name, limit = 52428800,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        Assertions.assertThrows(InvalidResourceException::class.java) {
            (rateLimiterService as AbstractRateLimiterService).createAlgorithmOfRateLimiter(resource, l3)
        }
        //测试 capacity 不合规的场景
        val l4 = ResourceLimit(
            algo = Algorithms.LEAKY_BUCKET.name, resource = "/project3/{repo}}/",
            limitDimension = LimitDimension.URL.name, limit = 1,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        Assertions.assertThrows(InvalidResourceException::class.java) {
            (rateLimiterService as AbstractRateLimiterService).createAlgorithmOfRateLimiter(resource, l4)
        }
        l4.capacity = -2
        Assertions.assertThrows(InvalidResourceException::class.java) {
            (rateLimiterService as AbstractRateLimiterService).createAlgorithmOfRateLimiter(resource, l4)
        }
        // 测试漏桶本地算法生成
        l4.capacity = 5
        rateLimiter = (rateLimiterService as AbstractRateLimiterService).createAlgorithmOfRateLimiter(resource, l4)
        Assertions.assertInstanceOf(
            LeakyRateLimiter::class.java,
            rateLimiter
        )
        // 测试漏桶分布式算法生成
        l4.scope = WorkScope.GLOBAL.name
        rateLimiter = (rateLimiterService as AbstractRateLimiterService).createAlgorithmOfRateLimiter(resource, l4)
        Assertions.assertInstanceOf(
            DistributedLeakyRateLimiter::class.java,
            rateLimiter
        )
        // 测试令牌桶本地算法生成
        val l5 = ResourceLimit(
            algo = Algorithms.TOKEN_BUCKET.name, resource = "/project3/",
            limitDimension = LimitDimension.URL.name, limit = 1, capacity = 5,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        rateLimiter = (rateLimiterService as AbstractRateLimiterService).createAlgorithmOfRateLimiter(resource, l5)
        Assertions.assertInstanceOf(
            TokenBucketRateLimiter::class.java,
            rateLimiter
        )
        // 测试令牌桶分布式算法生成
        l5.scope = WorkScope.GLOBAL.name
        rateLimiter = (rateLimiterService as AbstractRateLimiterService).createAlgorithmOfRateLimiter(resource, l5)
        Assertions.assertInstanceOf(
            DistributedTokenBucketRateLimiter::class.java,
            rateLimiter
        )
    }


    open fun refreshRateLimitRuleTest() {
        val hashCode = rateLimiterProperties.rules.hashCode()
        (rateLimiterService as AbstractRateLimiterService).refreshRateLimitRule()
        Assertions.assertEquals(hashCode, (rateLimiterService as AbstractRateLimiterService).currentRuleHashCode)
    }


    open fun getAlgorithmOfRateLimiterTest() {
        val resource = (rateLimiterService as AbstractRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as AbstractRateLimiterService).buildExtraResource(request)
        )
        val resourceLimit = (rateLimiterService as AbstractRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        assertEqualsLimitInfo(resourceLimit!!.resourceLimit, rateLimiterProperties.rules.first())
        val rateLimiter = (rateLimiterService as AbstractRateLimiterService)
            .getAlgorithmOfRateLimiter(resource, resourceLimit.resourceLimit)
        Assertions.assertInstanceOf(
            FixedWindowRateLimiter::class.java,
            rateLimiter
        )
        Assertions.assertEquals(
            rateLimiter,
            (rateLimiterService as AbstractRateLimiterService)
                .getAlgorithmOfRateLimiter(resource, resourceLimit.resourceLimit)
        )
    }


    open fun getResLimitInfoTest() {
        val resourceLimit = (rateLimiterService as AbstractRateLimiterService).getResLimitInfo(request)
        Assertions.assertNotNull(resourceLimit)
        assertEqualsLimitInfo(resourceLimit!!.resourceLimit, rateLimiterProperties.rules.first())
    }


    open fun circuitBreakerCheckTest() {

        var circuitBreakerPerSecond: Long? = null
        (rateLimiterService as AbstractRateLimiterService).circuitBreakerCheck(
            rateLimiterProperties.rules.first(), circuitBreakerPerSecond
        )

        circuitBreakerPerSecond = Long.MAX_VALUE
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as AbstractRateLimiterService).circuitBreakerCheck(
                rateLimiterProperties.rules.first(), circuitBreakerPerSecond
            )
        }
        circuitBreakerPerSecond = Long.MIN_VALUE
        (rateLimiterService as AbstractRateLimiterService).circuitBreakerCheck(
            rateLimiterProperties.rules.first(), circuitBreakerPerSecond
        )
    }


    open fun rateLimitCatchTest() {
        val resourceLimit = (rateLimiterService as AbstractRateLimiterService).getResLimitInfo(request)
        Assertions.assertNotNull(resourceLimit)
        val rateLimiter = (rateLimiterService as AbstractRateLimiterService)
            .getAlgorithmOfRateLimiter(resourceLimit!!.resource, resourceLimit.resourceLimit)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as AbstractRateLimiterService).rateLimitCatch(
                request = request,
                resLimitInfo = resourceLimit,
                applyPermits = Long.MAX_VALUE,
                circuitBreakerPerSecond = null,
            ) { rateLimiter, permits ->
                false
            }
        }
        rateLimiterProperties.dryRun = true
        (rateLimiterService as AbstractRateLimiterService).rateLimitCatch(
            request = request,
            resLimitInfo = resourceLimit,
            applyPermits = Long.MAX_VALUE,
            circuitBreakerPerSecond = null,
        ) { rateLimiter, permits ->
            false
        }
        rateLimiterProperties.dryRun = false
    }
}