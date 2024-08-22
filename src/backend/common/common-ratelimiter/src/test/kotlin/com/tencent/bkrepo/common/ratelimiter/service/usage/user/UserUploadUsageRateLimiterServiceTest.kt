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

package com.tencent.bkrepo.common.ratelimiter.service.usage.user

import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.api.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.usage.user.UserUploadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping
import java.util.concurrent.TimeUnit

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserUploadUsageRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    var l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "*:/*/",
        limitDimension = LimitDimension.USER_UPLOAD_USAGE.name, limit = 10,
        unit = TimeUnit.SECONDS.name, scope = WorkScope.LOCAL.name
    )

    @BeforeAll
    fun before() {
        init()
        rateLimiterProperties.enabled = true
        rateLimiterProperties.rules = listOf(l1)
        request.requestURI = "/blueking/generic-local/test.txt"
        request.setAttribute("userId", "admin")
        val uriVariables: MutableMap<String, String> = HashMap()
        uriVariables["projectId"] = "blueking"
        uriVariables["repoName"] = "generic-local"
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriVariables)
        val content = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        request.setContent(content)
        request.method = "PUT"
        val attributes = ServletRequestAttributes(request)
        RequestContextHolder.setRequestAttributes(attributes)
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = UserUploadUsageRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics
        )
        (rateLimiterService as UserUploadUsageRateLimiterService).refreshRateLimitRule()
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
        Assertions.assertEquals(false, (rateLimiterService as UserUploadUsageRateLimiterService).ignoreRequest(request))
        request.method = "POST"
        Assertions.assertEquals(false, (rateLimiterService as UserUploadUsageRateLimiterService).ignoreRequest(request))
        request.method = "PATCH"
        Assertions.assertEquals(false, (rateLimiterService as UserUploadUsageRateLimiterService).ignoreRequest(request))
        request.method = "GET"
        Assertions.assertEquals(true, (rateLimiterService as UserUploadUsageRateLimiterService).ignoreRequest(request))
        request.method = "PUT"
    }

    @Test
    fun getRepoInfoTest() {
        val (projectId, repoName) = (rateLimiterService as UserUploadUsageRateLimiterService).getRepoInfo(request)
        Assertions.assertEquals("blueking", projectId)
        Assertions.assertEquals("generic-local", repoName)

    }

    @Test
    fun buildResourceTest() {
        Assertions.assertEquals(
            "admin:/blueking/generic-local/",
            (rateLimiterService as UserUploadUsageRateLimiterService).buildResource(request)
        )
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            listOf("admin:/blueking/", "admin:"),
            (rateLimiterService as UserUploadUsageRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertEquals(
            10,
            (rateLimiterService as UserUploadUsageRateLimiterService).getApplyPermits(request, null)
        )
        request.method = "GET"
        Assertions.assertEquals(
            0,
            (rateLimiterService as UserUploadUsageRateLimiterService).getApplyPermits(request, null)
        )
        request.method = "PUT"
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            UserUploadUsageRateLimitRule::class.java,
            (rateLimiterService as UserUploadUsageRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.USER_UPLOAD_USAGE.name),
            (rateLimiterService as UserUploadUsageRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun generateKeyTestNull() {
        val resource = (rateLimiterService as UserUploadUsageRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UserUploadUsageRateLimiterService).buildExtraResource(request)
        )

        l1.resource = "test:/blueking/generic-local/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUploadUsageRateLimiterService).refreshRateLimitRule()
        var resourceLimit = (rateLimiterService as UserUploadUsageRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNull(resourceLimit)

        l1.resource = "test:"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUploadUsageRateLimiterService).refreshRateLimitRule()
        resourceLimit = (rateLimiterService as UserUploadUsageRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNull(resourceLimit)

        l1.resource = "*:/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUploadUsageRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as UserUploadUsageRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UserUploadUsageRateLimiterService).buildExtraResource(request)
        )
        var resourceLimit = (rateLimiterService as UserUploadUsageRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UserUploadUsage:" + "admin:/blueking/",
            (rateLimiterService as UserUploadUsageRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )

        l1.resource = "admin:/blueking/generic-local/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUploadUsageRateLimiterService).refreshRateLimitRule()
        resourceLimit = (rateLimiterService as UserUploadUsageRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UserUploadUsage:" + "admin:/blueking/generic-local/",
            (rateLimiterService as UserUploadUsageRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )

        l1.resource = "admin:"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUploadUsageRateLimiterService).refreshRateLimitRule()
        resourceLimit = (rateLimiterService as UserUploadUsageRateLimiterService)
            .rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UserUploadUsage:" + "admin:",
            (rateLimiterService as UserUploadUsageRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )

        l1.resource = "*:/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUploadUsageRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun limitTest() {
        // 本地限流验证
        (rateLimiterService as UserUploadUsageRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUploadUsageRateLimiterService).limit(request)
        }

        // 分布式算法验证
        l1.scope = WorkScope.GLOBAL.name
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUploadUsageRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UserUploadUsageRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUploadUsageRateLimiterService).limit(request)
        }

        l1.resource = "admin:/blueking/"
        l1.limit = 2
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUploadUsageRateLimiterService).refreshRateLimitRule()
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUploadUsageRateLimiterService).limit(request)
        }

        l1.resource = "admin:"
        l1.limit = 2
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUploadUsageRateLimiterService).refreshRateLimitRule()
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUploadUsageRateLimiterService).limit(request)
        }

        l1.resource = "test:"
        l1.limit = 20
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUploadUsageRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UserUploadUsageRateLimiterService).limit(request)
        (rateLimiterService as UserUploadUsageRateLimiterService).limit(request)
        Assertions.assertDoesNotThrow { (rateLimiterService as UserUploadUsageRateLimiterService).limit(request) }
    }

}