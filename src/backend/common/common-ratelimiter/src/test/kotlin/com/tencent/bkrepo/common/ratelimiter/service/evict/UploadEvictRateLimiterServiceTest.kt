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

package com.tencent.bkrepo.common.ratelimiter.service.evict

import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.evict.UploadEvictRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UploadEvictRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/blueking/generic-local",
        limitDimension = LimitDimension.UPLOAD_EVICT.name, limit = 3600,
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
        rateLimiterService = UploadEvictRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as UploadEvictRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            UploadEvictRateLimitRule::class.java,
            (rateLimiterService as UploadEvictRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.UPLOAD_EVICT.name),
            (rateLimiterService as UploadEvictRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun findEvictRule_matchProjectRepo() {
        val svc = rateLimiterService as UploadEvictRateLimiterService
        val result = svc.findEvictRule(
            userId = "user1", clientIp = "1.1.1.1",
            projectId = "blueking", repoName = "generic-local"
        )
        Assertions.assertNotNull(result)
        Assertions.assertEquals("/blueking/generic-local", result!!.resource)
        Assertions.assertEquals(3600L, result.resourceLimit.limit)
    }

    @Test
    fun findEvictRule_returns_null_when_no_match() {
        val svc = rateLimiterService as UploadEvictRateLimiterService
        val result = svc.findEvictRule(
            userId = "nobody", clientIp = "9.9.9.9",
            projectId = "other-project", repoName = "other-repo"
        )
        Assertions.assertNull(result)
    }

    @Test
    fun findEvictRule_returns_null_when_no_rules() {
        val svc = rateLimiterService as UploadEvictRateLimiterService
        rateLimiterProperties.rules = emptyList()
        svc.refreshRateLimitRule()

        val result = svc.findEvictRule(
            userId = "admin", clientIp = "1.1.1.1",
            projectId = "blueking", repoName = "generic-local"
        )
        Assertions.assertNull(result)

        rateLimiterProperties.rules = listOf(l1)
        svc.refreshRateLimitRule()
    }

    @Test
    fun priority_projectRepo_over_project() {
        val svc = rateLimiterService as UploadEvictRateLimiterService
        val repoRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name, resource = "/blueking/generic-local",
            limitDimension = LimitDimension.UPLOAD_EVICT.name, limit = 100,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        val projectRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name, resource = "/blueking",
            limitDimension = LimitDimension.UPLOAD_EVICT.name, limit = 9999,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        rateLimiterProperties.rules = listOf(repoRule, projectRule)
        svc.refreshRateLimitRule()

        val result = svc.findEvictRule(
            userId = "user1", clientIp = "1.1.1.1",
            projectId = "blueking", repoName = "generic-local"
        )
        Assertions.assertNotNull(result)
        Assertions.assertEquals(100L, result!!.resourceLimit.limit)

        rateLimiterProperties.rules = listOf(l1)
        svc.refreshRateLimitRule()
    }
}
