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

package com.tencent.bkrepo.common.ratelimiter.service.usage


import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

class UsageRateLimiterService(
    private val taskScheduler: ThreadPoolTaskScheduler,
    private val rateLimiterProperties: RateLimiterProperties,
    private val redisTemplate: RedisTemplate<String, String>? = null,
): AbstractRateLimiterService(taskScheduler, rateLimiterProperties, redisTemplate)  {

    override fun buildResource(request: HttpServletRequest): String {
        val (projectId, repoName) = getRepoInfo(request)
        return if (repoName.isNullOrEmpty()) {
            "/$projectId/*/"
        } else {
            "/$projectId/$repoName/"
        }
    }

    override fun buildResourceTemplate(request: HttpServletRequest): List<String> {
        val (projectId, repoName) = getRepoInfo(request)
        val result = mutableListOf<String>()
        result.add("/*/*/")
        result.add("/$projectId/*/")
        if (!repoName.isNullOrEmpty()) {
            result.add("/*/$repoName/")
        }
        return result
    }


    private fun getRepoInfo(request: HttpServletRequest): Pair<String?, String?> {
        val projectId = ((request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) )
            as LinkedHashMap<*,*>)["projectId"] as String?
        val repoName = ((request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) )
            as LinkedHashMap<*,*>)["repoName"] as String?
        if (projectId.isNullOrEmpty()) {
            throw InvalidResourceException("Could not find projectId from request ${request.requestURI}")
        }
        return Pair(projectId, repoName)
    }

    override fun applyPermits(request: HttpServletRequest): Long {
        return request.contentLengthLong
    }

    override fun refreshRateLimitRule() {
        if (!rateLimiterProperties.enabled) return
        val usageRules = UrlRateLimitRule()
        val usageRuleConfigs = rateLimiterProperties.rules.filter {
            it.limitDimension in listOf(LimitDimension.USAGE, LimitDimension.USAGE_TEMPLATE)
        }
        if (usageRuleConfigs.isEmpty()) return
        usageRules.addRateLimitRules(usageRuleConfigs)
        rateLimitRule = usageRules
    }
}
