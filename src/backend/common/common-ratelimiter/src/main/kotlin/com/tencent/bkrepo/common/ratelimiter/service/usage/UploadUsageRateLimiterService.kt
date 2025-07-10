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

package com.tencent.bkrepo.common.ratelimiter.service.usage


import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.usage.UploadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import javax.servlet.http.HttpServletRequest

/**
 * 上传容量限流器实现，针对project和repo
 */
open class UploadUsageRateLimiterService(
    taskScheduler: ThreadPoolTaskScheduler,
    rateLimiterProperties: RateLimiterProperties,
    rateLimiterMetrics: RateLimiterMetrics,
    redisTemplate: RedisTemplate<String, String>,
    rateLimiterConfigService: RateLimiterConfigService,
) : AbstractRateLimiterService(
    taskScheduler,
    rateLimiterProperties,
    rateLimiterMetrics,
    redisTemplate,
    rateLimiterConfigService
) {

    override fun buildResource(request: HttpServletRequest): String {
        val (projectId, repoName) = getRepoInfoFromAttribute(request)
        return if (repoName.isNullOrEmpty()) {
            "/$projectId/"
        } else {
            "/$projectId/$repoName/"
        }
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> {
        val (projectId, repoName) = getRepoInfoFromAttribute(request)
        val result = mutableListOf<String>()
        if (!repoName.isNullOrEmpty()) {
            result.add("/$projectId/")
        }
        return result
    }

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long {
        return when (request.method) {
            in UPLOAD_REQUEST_METHOD -> {
                var length = request.contentLengthLong
                if (length == -1L) {
                    logger.warn("content length of ${request.requestURI} is -1")
                    length = 0
                }
                length
            }
            else -> 0
        }
    }

    override fun getLimitDimensions(): List<String> {
        return listOf(
            LimitDimension.UPLOAD_USAGE.name
        )
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return UploadUsageRateLimitRule::class.java
    }

    override fun ignoreRequest(request: HttpServletRequest): Boolean {
        return request.method !in UPLOAD_REQUEST_METHOD
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "UploadUsage:$resource"
    }
}
