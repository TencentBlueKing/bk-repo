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

package com.tencent.bkrepo.common.ratelimiter.service.concurrent

import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.concurrent.UrlConcurrentRequestRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UrlConcurrentRequestLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/",
        limitDimension = LimitDimension.URL_CONCURRENT_REQUEST.name, limit = 2,
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
        rateLimiterService = UrlConcurrentRequestLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as UrlConcurrentRequestLimiterService).refreshRateLimitRule()
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
    fun buildResourceTest() {
        Assertions.assertEquals(
            "/blueking/generic-local/test.txt",
            (rateLimiterService as UrlConcurrentRequestLimiterService).buildResource(request)
        )
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            emptyList<String>(),
            (rateLimiterService as UrlConcurrentRequestLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertEquals(
            1L,
            (rateLimiterService as UrlConcurrentRequestLimiterService).getApplyPermits(request, null)
        )
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            UrlConcurrentRequestRateLimitRule::class.java,
            (rateLimiterService as UrlConcurrentRequestLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.URL_CONCURRENT_REQUEST.name),
            (rateLimiterService as UrlConcurrentRequestLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as UrlConcurrentRequestLimiterService).buildResource(request)
        val resInfo = ResInfo(resource = resource, extraResource = emptyList())
        val resourceLimit = (rateLimiterService as UrlConcurrentRequestLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UrlConcurrentRequest:" + "/blueking/generic-local/test.txt",
            (rateLimiterService as UrlConcurrentRequestLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )
    }

    @Test
    fun limitTest() {
        val svc = rateLimiterService as UrlConcurrentRequestLimiterService
        val isolatedRequest = org.springframework.mock.web.MockHttpServletRequest().apply {
            requestURI = "/test/limit-test-url"
        }
        // limit=2，连续三次应第三次抛出 OverloadException
        svc.limit(isolatedRequest)
        svc.limit(isolatedRequest)
        Assertions.assertThrows(OverloadException::class.java) {
            svc.limit(isolatedRequest)
        }

        // finish 归还并发计数，但速率限制窗口计数不受影响
        svc.finish(isolatedRequest)
        svc.finish(isolatedRequest)
    }

    @Test
    fun concurrentCounterIncrDecrTest() {
        val svc = rateLimiterService as UrlConcurrentRequestLimiterService
        val isolatedRequest = org.springframework.mock.web.MockHttpServletRequest().apply {
            requestURI = "/test/incr-decr-url"
        }
        val url = isolatedRequest.requestURI
        val before = svc.getUrlConcurrentCount(url)

        // limit 调用后计数 +1（不超限时不会回滚）
        svc.limit(isolatedRequest)
        Assertions.assertEquals(before + 1, svc.getUrlConcurrentCount(url))

        // finish 后计数 -1
        svc.finish(isolatedRequest)
        Assertions.assertEquals(before, svc.getUrlConcurrentCount(url))
    }

    @Test
    fun counterRollsBackOnRejectTest() {
        val svc = rateLimiterService as UrlConcurrentRequestLimiterService
        val isolatedRequest = org.springframework.mock.web.MockHttpServletRequest().apply {
            requestURI = "/test/rollback-url"
        }
        val url = isolatedRequest.requestURI
        // 先把计数打到上限
        svc.limit(isolatedRequest)
        svc.limit(isolatedRequest)
        val countAtLimit = svc.getUrlConcurrentCount(url)

        // 被拒绝时计数应回滚
        try {
            svc.limit(isolatedRequest)
        } catch (e: OverloadException) {
            // expected
        }
        Assertions.assertEquals(countAtLimit, svc.getUrlConcurrentCount(url))
    }
}
