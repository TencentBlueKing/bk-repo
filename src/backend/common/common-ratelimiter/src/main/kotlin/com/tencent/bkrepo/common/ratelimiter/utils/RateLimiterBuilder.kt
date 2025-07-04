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

package com.tencent.bkrepo.common.ratelimiter.utils

import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedFixedWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedLeakyRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedSlidingWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedTokenBucketRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.FixedWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.LeakyRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.SlidingWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.TokenBucketRateLimiter
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import org.springframework.data.redis.core.RedisTemplate
import java.util.concurrent.ConcurrentHashMap

object RateLimiterBuilder {

    /**
     * 获取对应限流算法实现
     */
    fun getAlgorithmOfRateLimiter(
        limitKey: String,
        resourceLimit: ResourceLimit,
        redisTemplate: RedisTemplate<String, String>? = null,
        rateLimiterCache: ConcurrentHashMap<String, RateLimiter>,
        resInfo: ResInfo? = null,
        rateLimitRule: RateLimitRule? = null,
    ): RateLimiter {
        return rateLimiterCache[limitKey] ?: run {
            val latestResourceLimit = resInfo?.let {
                rateLimitRule?.getRateLimitRule(it)?.resourceLimit
                    ?: throw AcquireLockFailedException("ResourceLimit is null")
            } ?: resourceLimit
            val newRateLimiter = createAlgorithmOfRateLimiter(limitKey, latestResourceLimit, redisTemplate)
            rateLimiterCache.putIfAbsent(limitKey, newRateLimiter) ?: newRateLimiter
        }
    }

    /**
     * 根据资源和限流规则生成对应限流算法
     */
    fun createAlgorithmOfRateLimiter(
        resource: String,
        resourceLimit: ResourceLimit,
        redisTemplate: RedisTemplate<String, String>? = null
    ): RateLimiter {
        if (resourceLimit.limit < 0) {
            throw InvalidResourceException("config limit is ${resourceLimit.limit}")
        }
        return when (resourceLimit.algo) {
            Algorithms.FIXED_WINDOW.name -> {
                buildFixedWindowRateLimiter(resource, resourceLimit, redisTemplate)
            }

            Algorithms.TOKEN_BUCKET.name -> {
                buildTokenBucketRateLimiter(resource, resourceLimit, redisTemplate)
            }

            Algorithms.SLIDING_WINDOW.name -> {
                buildSlidingWindowRateLimiter(resource, resourceLimit, redisTemplate)
            }

            Algorithms.LEAKY_BUCKET.name -> {
                buildLeakyRateLimiter(resource, resourceLimit, redisTemplate)
            }

            else -> {
                throw InvalidResourceException("config algo is ${resourceLimit.algo}")
            }
        }
    }

    private fun buildFixedWindowRateLimiter(
        resource: String,
        resourceLimit: ResourceLimit,
        redisTemplate: RedisTemplate<String, String>? = null
    ): RateLimiter {
        return if (resourceLimit.scope == WorkScope.LOCAL.name) {
            FixedWindowRateLimiter(
                resourceLimit.limit, resourceLimit.duration, keepConnection = resourceLimit.keepConnection
            )
        } else {
            DistributedFixedWindowRateLimiter(
                resource, resourceLimit.limit, resourceLimit.duration, redisTemplate!!, resourceLimit.keepConnection
            )
        }
    }

    private fun buildTokenBucketRateLimiter(
        resource: String,
        resourceLimit: ResourceLimit,
        redisTemplate: RedisTemplate<String, String>? = null
    ): RateLimiter {
        val permitsPerSecond = (resourceLimit.limit / resourceLimit.duration.seconds.toDouble())
        return if (resourceLimit.scope == WorkScope.LOCAL.name) {
            TokenBucketRateLimiter(permitsPerSecond, resourceLimit.keepConnection)
        } else {
            if (resourceLimit.capacity == null || resourceLimit.capacity!! <= 0) {
                throw InvalidResourceException("Resource limit config $resourceLimit is illegal")
            }
            DistributedTokenBucketRateLimiter(
                resource, permitsPerSecond, resourceLimit.capacity!!, redisTemplate!!, resourceLimit.keepConnection
            )
        }
    }

    private fun buildSlidingWindowRateLimiter(
        resource: String,
        resourceLimit: ResourceLimit,
        redisTemplate: RedisTemplate<String, String>? = null
    ): RateLimiter {
        return if (resourceLimit.scope == WorkScope.LOCAL.name) {
            SlidingWindowRateLimiter(resourceLimit.limit, resourceLimit.duration, resourceLimit.keepConnection)
        } else {
            DistributedSlidingWindowRateLimiter(
                resource, resourceLimit.limit, resourceLimit.duration, redisTemplate!!, resourceLimit.keepConnection
            )
        }
    }

    private fun buildLeakyRateLimiter(
        resource: String,
        resourceLimit: ResourceLimit,
        redisTemplate: RedisTemplate<String, String>? = null
    ): RateLimiter {
        if (resourceLimit.capacity == null || resourceLimit.capacity!! <= 0) {
            throw InvalidResourceException("Resource limit config $resourceLimit is illegal")
        }
        val rate = resourceLimit.limit / resourceLimit.duration.seconds.toDouble()
        return if (resourceLimit.scope == WorkScope.LOCAL.name) {
            LeakyRateLimiter(rate, resourceLimit.capacity!!, resourceLimit.keepConnection)
        } else {
            DistributedLeakyRateLimiter(
                resource, rate, resourceLimit.capacity!!, redisTemplate!!, resourceLimit.keepConnection
            )
        }
    }
}