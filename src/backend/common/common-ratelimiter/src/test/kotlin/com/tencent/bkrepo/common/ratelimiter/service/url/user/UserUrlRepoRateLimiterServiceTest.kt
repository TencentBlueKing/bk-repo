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
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.user.UserUrlRepoRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import com.tencent.bkrepo.repository.pojo.repo.UserRepoCreateRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import java.time.Duration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserUrlRepoRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "*:/*/",
        limitDimension = LimitDimension.USER_URL_REPO.name, limit = 1,
        duration = Duration.ofSeconds(1), scope = WorkScope.LOCAL.name
    )

    @BeforeAll
    fun before() {
        init()
        rateLimiterProperties.enabled = true
        rateLimiterProperties.rules = listOf(l1)
        request.requestURI = "/blueking/generic-local/test.txt"
        request.setAttribute("userId", "admin")
        request.requestURI = "/blueking/generic-local/test.txt"
        val uriVariables: MutableMap<String, String> = HashMap()
        uriVariables["projectId"] = "blueking"
        uriVariables["repoName"] = "generic-local"
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriVariables)
        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, "/{projectId}/{repoName}/**")
        val attributes = ServletRequestAttributes(request)
        RequestContextHolder.setRequestAttributes(attributes)
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = UserUrlRepoRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics
        )
        (rateLimiterService as UserUrlRepoRateLimiterService).refreshRateLimitRule()
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
        rateLimiterProperties.specialUrls = listOf("*")
        Assertions.assertEquals(false, (rateLimiterService as UserUrlRepoRateLimiterService).ignoreRequest(request))
        rateLimiterProperties.specialUrls = listOf("/{projectId}/{repoName}/**")
        Assertions.assertEquals(false, (rateLimiterService as UserUrlRepoRateLimiterService).ignoreRequest(request))
        rateLimiterProperties.specialUrls = listOf()
        Assertions.assertEquals(true, (rateLimiterService as UserUrlRepoRateLimiterService).ignoreRequest(request))
    }

    @Test
    fun buildResourceTest() {
        Assertions.assertEquals(
            "admin:/blueking/generic-local/",
            (rateLimiterService as UserUrlRepoRateLimiterService).buildResource(request)
        )
        request.requestURI = "/api/node/batch/blueking/generic-local"
        request.setAttribute("userId", "test")
        Assertions.assertEquals(
            "test:/blueking/generic-local/",
            (rateLimiterService as UserUrlRepoRateLimiterService).buildResource(request)
        )
        request.requestURI = "/blueking/generic-local/test.txt"
        request.setAttribute("userId", "admin")
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            listOf("admin:/blueking/", "admin:"),
            (rateLimiterService as UserUrlRepoRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertEquals(
            1,
            (rateLimiterService as UserUrlRepoRateLimiterService).getApplyPermits(request, null)
        )
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            UserUrlRepoRateLimitRule::class.java,
            (rateLimiterService as UserUrlRepoRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.USER_URL_REPO.name),
            (rateLimiterService as UserUrlRepoRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as UserUrlRepoRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UserUrlRepoRateLimiterService).buildExtraResource(request)
        )
        var resourceLimit =
            (rateLimiterService as UserUrlRepoRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UserUrlRepo:" + "admin:/blueking/",
            (rateLimiterService as UserUrlRepoRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )
        l1.resource = "admin:"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRepoRateLimiterService).refreshRateLimitRule()

        resourceLimit = (rateLimiterService as UserUrlRepoRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UserUrlRepo:" + "admin:",
            (rateLimiterService as UserUrlRepoRateLimiterService)
                .generateKey(resourceLimit!!.resource, resourceLimit.resourceLimit)
        )

        l1.resource = "*:/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRepoRateLimiterService).refreshRateLimitRule()
    }


    @Test
    fun getRepoInfoTest() {
        val (projectId, repoName) = (rateLimiterService as UserUrlRepoRateLimiterService).getRepoInfoFromAttribute(
            request
        )
        Assertions.assertEquals("blueking", projectId)
        Assertions.assertEquals("generic-local", repoName)
    }

    @Test
    fun getRepoInfoFromBodyTest() {
        request.requestURI = "/api/node/search"
        request.contentType = "application/json"
        val queryModelBuilder = NodeQueryBuilder()
            .select(PROJECT_ID, REPO_NAME)
            .sortByAsc("fullPath")
            .page(1, 10)
            .projectId("test-projectId")
            .repoName("test-repoName")

        val queryModel = queryModelBuilder.build()
        request.setContent(queryModel.toJsonString().toByteArray())
        val (projectId, repoName) = (rateLimiterService as UserUrlRepoRateLimiterService).getRepoInfoFromBody(
            request
        )
        Assertions.assertEquals("test-projectId", projectId)
        Assertions.assertEquals("test-repoName", repoName)

        request.setContent(
            UserRepoCreateRequest(
                projectId = "test-projectId1",
                name = "test-repoName1",
                type = RepositoryType.GENERIC,
                category = RepositoryCategory.COMPOSITE,
                display = false
            ).toJsonString().toByteArray()
        )
        val (projectId1, repoName1) = (rateLimiterService as UserUrlRepoRateLimiterService).getRepoInfoFromBody(
            request
        )
        Assertions.assertEquals("test-projectId1", projectId1)
        Assertions.assertEquals(null, repoName1)

        request.requestURI = "/blueking/generic-local/test.txt"
    }

    @Test
    fun generateKeyTestNull() {
        l1.resource = "test:"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRepoRateLimiterService).refreshRateLimitRule()

        val resource = (rateLimiterService as UserUrlRepoRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UserUrlRepoRateLimiterService).buildExtraResource(request)
        )
        var resourceLimit =
            (rateLimiterService as UserUrlRepoRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNull(resourceLimit)

        l1.resource = "test:/blueking/generic-local/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRepoRateLimiterService).refreshRateLimitRule()
        resourceLimit = (rateLimiterService as UserUrlRepoRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNull(resourceLimit)


        l1.resource = "*:/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRepoRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun limitTest() {
        rateLimiterProperties.specialUrls = listOf("*")
        // 本地限流验证
        (rateLimiterService as UserUrlRepoRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUrlRepoRateLimiterService).limit(request)
        }

        // 分布式算法验证
        l1.scope = WorkScope.GLOBAL.name
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRepoRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UserUrlRepoRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUrlRepoRateLimiterService).limit(request)
        }

        l1.resource = "admin:/blueking/"
        l1.limit = 2
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRepoRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UserUrlRepoRateLimiterService).limit(request)
        (rateLimiterService as UserUrlRepoRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUrlRepoRateLimiterService).limit(request)
        }

        l1.resource = "admin:"
        l1.limit = 1
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRepoRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UserUrlRepoRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UserUrlRepoRateLimiterService).limit(request)
        }

        l1.resource = "test:/blueking"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRepoRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UserUrlRepoRateLimiterService).limit(request)
        (rateLimiterService as UserUrlRepoRateLimiterService).limit(request)
        Assertions.assertDoesNotThrow { (rateLimiterService as UserUrlRepoRateLimiterService).limit(request) }

        l1.resource = "*:/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UserUrlRepoRateLimiterService).refreshRateLimitRule()
    }


}