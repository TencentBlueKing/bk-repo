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
import com.tencent.bkrepo.common.ratelimiter.rule.evict.DownloadEvictRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import java.time.Duration

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DownloadEvictRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/blueking/generic-local",
        limitDimension = LimitDimension.DOWNLOAD_EVICT.name, limit = 3600,
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
        rateLimiterService = DownloadEvictRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as DownloadEvictRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            DownloadEvictRateLimitRule::class.java,
            (rateLimiterService as DownloadEvictRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.DOWNLOAD_EVICT.name),
            (rateLimiterService as DownloadEvictRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun findEvictRule_matchProjectRepo() {
        val svc = rateLimiterService as DownloadEvictRateLimiterService
        val result = svc.findEvictRule(
            userId = "user1", clientIp = "1.1.1.1",
            projectId = "blueking", repoName = "generic-local"
        )
        Assertions.assertNotNull(result)
        Assertions.assertEquals("/blueking/generic-local", result!!.resource)
    }

    @Test
    fun findEvictRule_matchProject_when_no_repoRule() {
        val svc = rateLimiterService as DownloadEvictRateLimiterService
        // 只有项目规则
        val projectRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name, resource = "/blueking",
            limitDimension = LimitDimension.DOWNLOAD_EVICT.name, limit = 7200,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        rateLimiterProperties.rules = listOf(projectRule)
        svc.refreshRateLimitRule()

        val result = svc.findEvictRule(
            userId = "user1", clientIp = "1.1.1.1",
            projectId = "blueking", repoName = "other-repo"
        )
        Assertions.assertNotNull(result)
        Assertions.assertEquals("/blueking", result!!.resource)

        // 恢复
        rateLimiterProperties.rules = listOf(l1)
        svc.refreshRateLimitRule()
    }

    @Test
    fun findEvictRule_matchUser_when_no_projectRule() {
        val svc = rateLimiterService as DownloadEvictRateLimiterService
        val userRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name, resource = "/user/admin",
            limitDimension = LimitDimension.DOWNLOAD_EVICT.name, limit = 1800,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        rateLimiterProperties.rules = listOf(userRule)
        svc.refreshRateLimitRule()

        val result = svc.findEvictRule(
            userId = "admin", clientIp = "1.1.1.1",
            projectId = null, repoName = null
        )
        Assertions.assertNotNull(result)
        Assertions.assertEquals("/user/admin", result!!.resource)

        rateLimiterProperties.rules = listOf(l1)
        svc.refreshRateLimitRule()
    }

    @Test
    fun findEvictRule_matchIp_when_no_other_rules() {
        val svc = rateLimiterService as DownloadEvictRateLimiterService
        val ipRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name, resource = "/ip/1.1.1.1",
            limitDimension = LimitDimension.DOWNLOAD_EVICT.name, limit = 900,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        rateLimiterProperties.rules = listOf(ipRule)
        svc.refreshRateLimitRule()

        val result = svc.findEvictRule(
            userId = "user1", clientIp = "1.1.1.1",
            projectId = null, repoName = null
        )
        Assertions.assertNotNull(result)
        Assertions.assertEquals("/ip/1.1.1.1", result!!.resource)

        rateLimiterProperties.rules = listOf(l1)
        svc.refreshRateLimitRule()
    }

    @Test
    fun findEvictRule_returns_null_when_no_match() {
        val svc = rateLimiterService as DownloadEvictRateLimiterService
        val result = svc.findEvictRule(
            userId = "nobody", clientIp = "9.9.9.9",
            projectId = "other-project", repoName = "other-repo"
        )
        Assertions.assertNull(result)
    }

    @Test
    fun priority_projectRepo_over_project() {
        val svc = rateLimiterService as DownloadEvictRateLimiterService
        val repoRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name, resource = "/blueking/generic-local",
            limitDimension = LimitDimension.DOWNLOAD_EVICT.name, limit = 100,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        val projectRule = ResourceLimit(
            algo = Algorithms.FIXED_WINDOW.name, resource = "/blueking",
            limitDimension = LimitDimension.DOWNLOAD_EVICT.name, limit = 999,
            duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
        )
        rateLimiterProperties.rules = listOf(repoRule, projectRule)
        svc.refreshRateLimitRule()

        val result = svc.findEvictRule(
            userId = "user1", clientIp = "1.1.1.1",
            projectId = "blueking", repoName = "generic-local"
        )
        Assertions.assertNotNull(result)
        // project/repo 规则优先于 project 规则
        Assertions.assertEquals(100L, result!!.resourceLimit.limit)

        rateLimiterProperties.rules = listOf(l1)
        svc.refreshRateLimitRule()
    }
}
