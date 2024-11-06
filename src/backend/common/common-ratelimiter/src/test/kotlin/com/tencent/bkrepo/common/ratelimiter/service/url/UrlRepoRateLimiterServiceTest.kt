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

package com.tencent.bkrepo.common.ratelimiter.service.url

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
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlRepoRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterServiceTest
import com.tencent.bkrepo.repository.pojo.repo.UserRepoCreateRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import java.time.Duration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UrlRepoRateLimiterServiceTest : AbstractRateLimiterServiceTest() {

    val l1 = ResourceLimit(
        algo = Algorithms.FIXED_WINDOW.name, resource = "/*/",
        limitDimension = LimitDimension.URL_REPO.name, limit = 1,
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
        request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, "/{projectId}/{repoName}/**")
        request.contentType = "application/json"
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.initialize()
        rateLimiterService = UrlRepoRateLimiterService(
            taskScheduler = scheduler,
            rateLimiterProperties = rateLimiterProperties,
            redisTemplate = redisTemplate,
            rateLimiterMetrics = rateLimiterMetrics,
            rateLimiterConfigService = rateLimiterConfigService
        )
        (rateLimiterService as UrlRepoRateLimiterService).refreshRateLimitRule()
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
        Assertions.assertEquals(false, (rateLimiterService as UrlRepoRateLimiterService).ignoreRequest(request))
        rateLimiterProperties.specialUrls = listOf("/{projectId}/{repoName}/**")
        Assertions.assertEquals(false, (rateLimiterService as UrlRepoRateLimiterService).ignoreRequest(request))
        rateLimiterProperties.specialUrls = listOf()
        Assertions.assertEquals(true, (rateLimiterService as UrlRepoRateLimiterService).ignoreRequest(request))
    }

    @Test
    fun buildResourceTest() {
        Assertions.assertEquals(
            "/blueking/generic-local/",
            (rateLimiterService as UrlRepoRateLimiterService).buildResource(request)
        )
        request.requestURI = "/api/node/batch/blueking/generic-local"
        Assertions.assertEquals(
            "/blueking/generic-local/",
            (rateLimiterService as UrlRepoRateLimiterService).buildResource(request)
        )
        request.requestURI = "/blueking/generic-local/test.txt"
    }

    @Test
    fun buildExtraResourceTest() {
        Assertions.assertEquals(
            listOf("/blueking/"),
            (rateLimiterService as UrlRepoRateLimiterService).buildExtraResource(request)
        )
    }

    @Test
    fun getApplyPermitsTest() {
        Assertions.assertEquals(1, (rateLimiterService as UrlRepoRateLimiterService).getApplyPermits(request, null))
    }

    @Test
    fun getRateLimitRuleClassTest() {
        Assertions.assertEquals(
            UrlRepoRateLimitRule::class.java,
            (rateLimiterService as UrlRepoRateLimiterService).getRateLimitRuleClass()
        )
    }

    @Test
    fun getLimitDimensionsTest() {
        Assertions.assertEquals(
            listOf(LimitDimension.URL_REPO.name),
            (rateLimiterService as UrlRepoRateLimiterService).getLimitDimensions()
        )
    }

    @Test
    fun generateKeyTest() {
        val resource = (rateLimiterService as UrlRepoRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UrlRepoRateLimiterService).buildExtraResource(request)
        )
        val resourceLimit = (rateLimiterService as UrlRepoRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNotNull(resourceLimit)
        Assertions.assertEquals(
            KEY_PREFIX + "UrlRepo:" + "/blueking/",
            (rateLimiterService as UrlRepoRateLimiterService).generateKey(
                resourceLimit!!.resource, resourceLimit.resourceLimit
            )
        )
    }

    @Test
    fun getRepoInfoTest() {
        val (projectId, repoName) = (rateLimiterService as UrlRepoRateLimiterService).getRepoInfoFromAttribute(
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
        val (projectId, repoName) = (rateLimiterService as UrlRepoRateLimiterService).getRepoInfoFromBody(
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
        val (projectId1, repoName1) = (rateLimiterService as UrlRepoRateLimiterService).getRepoInfoFromBody(
            request
        )
        Assertions.assertEquals("test-projectId1", projectId1)
        Assertions.assertEquals(null, repoName1)

        request.requestURI = "/blueking/generic-local/test.txt"
    }

    @Test
    fun generateKeyTestNull() {
        l1.resource = "/test/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UrlRepoRateLimiterService).refreshRateLimitRule()

        val resource = (rateLimiterService as UrlRepoRateLimiterService).buildResource(request)
        val resInfo = ResInfo(
            resource = resource,
            extraResource = (rateLimiterService as UrlRepoRateLimiterService).buildExtraResource(request)
        )
        val resourceLimit = (rateLimiterService as UrlRepoRateLimiterService).rateLimitRule?.getRateLimitRule(resInfo)
        Assertions.assertNull(resourceLimit)

        l1.resource = "/*/"
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UrlRepoRateLimiterService).refreshRateLimitRule()
    }

    @Test
    fun limitTest() {
        rateLimiterProperties.specialUrls = listOf("*")
        // 本地限流验证
        (rateLimiterService as UrlRepoRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UrlRepoRateLimiterService).limit(request)
        }

        // 分布式算法验证
        l1.scope = WorkScope.GLOBAL.name
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UrlRepoRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UrlRepoRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UrlRepoRateLimiterService).limit(request)
        }

        l1.resource = "/blueking/"
        l1.limit = 2
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UrlRepoRateLimiterService).refreshRateLimitRule()
        (rateLimiterService as UrlRepoRateLimiterService).limit(request)
        (rateLimiterService as UrlRepoRateLimiterService).limit(request)
        Assertions.assertThrows(OverloadException::class.java) {
            (rateLimiterService as UrlRepoRateLimiterService).limit(request)
        }

        l1.resource = "/*/"
        l1.limit = 1
        rateLimiterProperties.rules = listOf(l1)
        (rateLimiterService as UrlRepoRateLimiterService).refreshRateLimitRule()
    }

}
