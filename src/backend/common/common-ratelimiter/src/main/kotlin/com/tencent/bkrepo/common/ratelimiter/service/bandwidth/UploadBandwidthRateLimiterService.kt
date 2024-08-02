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

package com.tencent.bkrepo.common.ratelimiter.service.bandwidth


import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.UploadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.AbstractBandwidthRateLimiterService
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import javax.servlet.http.HttpServletRequest

/**
 * 上传带宽限流器实现, 针对project/repo进行限流
 */
open class UploadBandwidthRateLimiterService(
    taskScheduler: ThreadPoolTaskScheduler,
    rateLimiterProperties: RateLimiterProperties,
    rateLimiterMetrics: RateLimiterMetrics,
    redisTemplate: RedisTemplate<String, String>? = null,
) : AbstractBandwidthRateLimiterService(taskScheduler, rateLimiterMetrics, redisTemplate, rateLimiterProperties) {

    override fun buildResource(request: HttpServletRequest): String {
        val (projectId, repoName) = getRepoInfo(request)
        return "/$projectId/$repoName/"
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> {
        val (projectId, _) = getRepoInfo(request)
        return listOf("/$projectId/")
    }

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long {
        throw UnsupportedOperationException()
    }

    override fun getLimitDimensions(): List<String> {
        return listOf(
            LimitDimension.UPLOAD_BANDWIDTH.name
        )
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return UploadBandwidthRateLimitRule::class.java
    }

    override fun ignoreRequest(request: HttpServletRequest): Boolean {
        return request.method !in UPLOAD_REQUEST_METHOD
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "UploadBandwidth:$resource"
    }
}
