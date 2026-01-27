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

package com.tencent.bkrepo.common.ratelimiter.service.ip

import com.tencent.bkrepo.common.api.constant.CLIENT_ADDRESS
import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.ip.IpRateLimitRule
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
class IpRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    private val testIp = "192.168.1.100"

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/ip/$testIp",
        limitDimension = LimitDimension.IP.name, limit = 1,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )

    @BeforeAll
    fun before() {
        init()
        rateLimiterProperties.enabled = true
        rateLimiterProperties.rules = listOf(l1)
        request.requestURI = "/blueking/generic-local/test.txt"
        // 设置客户端 IP 属性，HttpContextHolder.getClientAddressFromAttribute() 读取此字段
        request.setAttribute(CLIENT_ADDRESS, testIp)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = IpRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as IpRateLimiterService).refreshRateLimitRule()
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
            "/ip/$testIp",
            (rateLimiterService as IpRateLimiterService).buildResource(request)
        )
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            listOf(testIp),
            (rateLimiterService as IpRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertEquals(
            1L,
            (rateLimiterService as IpRateLimiterService).getApplyPermits(request, null)
        )
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            IpRateLimitRule::class.java,
            (rateLimiterService as IpRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.IP.name),
            (rateLimiterService as IpRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun ignoreRequest_knownIp_returns_false() {
        Assertions.assertFalse((rateLimiterService as IpRateLimiterService).ignoreRequest(request))
    }

    @Test
    fun ignoreRequest_unknownIp_returns_true() {
        // 清除 RequestContextHolder 以模拟无 IP 上下文，HttpContextHolder 会返回 UNKNOWN
        val savedAttributes = RequestContextHolder.getRequestAttributes()
        RequestContextHolder.resetRequestAttributes()
        try {
            Assertions.assertTrue((rateLimiterService as IpRateLimiterService).ignoreRequest(request))
        } finally {
            RequestContextHolder.setRequestAttributes(savedAttributes)
        }
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as IpRateLimiterService).buildResource(request)
        Assertions.assertEquals(
            KEY_PREFIX + "Ip:/ip/$testIp",
            (rateLimiterService as IpRateLimiterService).generateKey(resource, l1)
        )
    }

    @Test
    fun limitTest() {
        val svc = rateLimiterService as IpRateLimiterService
        svc.limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            svc.limit(request)
        }

        // 全局规则（/ip）适配所有 IP
        val globalRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name, resource = "/ip",
            limitDimension = LimitDimension.IP.name, limit = 2,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        rateLimiterProperties.rules = listOf(globalRule)
        svc.refreshRateLimitRule()
        svc.limit(request)
        svc.limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            svc.limit(request)
        }

        // 恢复
        rateLimiterProperties.rules = listOf(l1)
        svc.refreshRateLimitRule()
    }
}
