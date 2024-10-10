/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.user.UserUrlRepoRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
import javax.servlet.http.HttpServletRequest

/**
 * user+urlRepo限流器实现
 */
class UserUrlRepoRateLimiterService(
    taskScheduler: ThreadPoolTaskScheduler,
    rateLimiterProperties: RateLimiterProperties,
    rateLimiterMetrics: RateLimiterMetrics,
    redisTemplate: RedisTemplate<String, String>? = null,
) : AbstractRateLimiterService(taskScheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate) {

    override fun ignoreRequest(request: HttpServletRequest): Boolean {
        if (rateLimiterProperties.specialUrls.contains(StringPool.POUND)) {
            return false
        }
        return !rateLimiterProperties.specialUrls.contains(request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE))
    }

    override fun buildResource(request: HttpServletRequest): String {
        val userId = HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER
        val (projectId, repoName) = try {
            getRepoInfoFromAttribute(request)
        } catch (e: InvalidResourceException) {
            getRepoInfoFromBody(request)
        }
        return if (repoName.isNullOrEmpty()) {
            "$userId:/$projectId/"
        } else {
            "$userId:/$projectId/$repoName/"
        }
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> {
        val userId = HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER
        val (projectId, repoName) = try {
            getRepoInfoFromAttribute(request)
        } catch (e: InvalidResourceException) {
            getRepoInfoFromBody(request)
        }
        val result = mutableListOf<String>()
        if (!repoName.isNullOrEmpty()) {
            result.add("$userId:/$projectId/")
        }
        result.add("$userId:")
        return result
    }

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long {
        return 1
    }

    override fun getLimitDimensions(): List<String> {
        return listOf(LimitDimension.USER_URL_REPO.name)
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return UserUrlRepoRateLimitRule::class.java
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "UserUrlRepo:$resource"
    }

}
