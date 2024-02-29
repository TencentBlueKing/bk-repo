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

package com.tencent.bkrepo.common.ratelimiter.service


import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedFixedWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedTokenBucketRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.FixedWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.TokenBucketRateLimiter
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.exception.OverloadException
import com.tencent.bkrepo.common.ratelimiter.interceptor.MonitorRateLimiterInterceptorAdaptor
import com.tencent.bkrepo.common.ratelimiter.interceptor.RateLimiterInterceptor
import com.tencent.bkrepo.common.ratelimiter.interceptor.RateLimiterInterceptorChain
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.ResourceLimit
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ConcurrentHashMap
import javax.servlet.http.HttpServletRequest

abstract class AbstractRateLimiterService(
    private val taskScheduler: ThreadPoolTaskScheduler,
    private val rateLimiterProperties: RateLimiterProperties,
    private val rateLimiterMetrics: RateLimiterMetrics,
    private val redisTemplate: RedisTemplate<String, String>? = null,
): RateLimiterService {

    //TODO 需要考虑请求过多导致缓存过大
    private val rateLimiterCache: ConcurrentHashMap<String, RateLimiter> = ConcurrentHashMap(256)

    private val interceptorChain: RateLimiterInterceptorChain =
        RateLimiterInterceptorChain(mutableListOf(MonitorRateLimiterInterceptorAdaptor(rateLimiterMetrics)))

    var rateLimitRule: RateLimitRule? = null

    init {
        taskScheduler.scheduleWithFixedDelay(this::refreshRateLimitRule, rateLimiterProperties.refreshDuration * 1000)
    }

    override fun limit(request: HttpServletRequest) {
        val resource = buildResource(request)
        interceptorChain.doBeforeLimitCheck(resource)
        val applyPermits = applyPermits(request)
        var resourceLimit: ResourceLimit? = null
        var pass = false
        var exception: Exception? = null
        try {
            resourceLimit = rateLimitRule?.getRateLimitRule(resource)
                ?: rateLimitRule?.getRateLimitRule(
                    resource,
                    buildResourceTemplate(request)
                )
            if (resourceLimit == null) {
                logger.info("no rule for request ${request.requestURI}")
                return
            }
            val rateLimiter = getAlgorithmOfRateLimiter(resource, resourceLimit, applyPermits)
            pass = rateLimiter.tryAcquire()
            if (!pass) {
                throw OverloadException("$resource has exceeded max rate limit:" +
                                            " ${resourceLimit.limit} /${resourceLimit.unit}")
            }
        } catch (e: OverloadException) {
            pass = false
            throw e
        } catch (e: AcquireLockFailedException) {
            exception = e
            throw e
        } catch (e: InvalidResourceException) {
            exception = e
            throw e
        } catch (e: Exception) {
            val newException = AcquireLockFailedException("internal error: $e")
            exception = newException
            throw newException
        } finally {
            interceptorChain.doAfterLimitCheck(resource, resourceLimit, pass, exception, applyPermits)
        }

    }

    override fun addInterceptor(interceptor: RateLimiterInterceptor) {
            this.interceptorChain.addInterceptor(interceptor)
    }

    override fun addInterceptors(interceptors: List<RateLimiterInterceptor>) {
        if (interceptors.isNotEmpty()) {
            this.interceptorChain.addInterceptors(interceptors)
        }
    }

    open fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return resource
    }

    abstract fun buildResource(request: HttpServletRequest): String

    abstract fun buildResourceTemplate(request: HttpServletRequest): List<String>

    abstract fun applyPermits(request: HttpServletRequest): Long

    open fun createAlgorithmOfRateLimiter(resource: String, resourceLimit: ResourceLimit, permits: Long): RateLimiter {
        if (resourceLimit.limit < 0) {
            throw InvalidResourceException("config limit is ${resourceLimit.limit}")
        }
        return when (resourceLimit.algo) {
            Algorithms.FIXED_WINDOW -> {
                if (rateLimiterProperties.scope == WorkScope.LOCAL) {
                    FixedWindowRateLimiter(resourceLimit.limit, resourceLimit.unit, permits)
                } else {
                    DistributedFixedWindowRateLimiter(
                        resource, resourceLimit.limit, resourceLimit.unit, redisTemplate!!, permits
                    )
                }
            }
            Algorithms.TOKEN_BUCKET -> {
                if (rateLimiterProperties.scope == WorkScope.LOCAL) {
                    val permitsPerSecond = resourceLimit.limit / resourceLimit.unit.toSeconds(1)
                    TokenBucketRateLimiter(permitsPerSecond, permits)
                } else {
                    if (resourceLimit.bucketCapacity == null || resourceLimit.bucketCapacity!! <= 0) {
                        throw AcquireLockFailedException("Resource limit config for $resource is illegal")
                    }
                    val permitsPerSecond = resourceLimit.limit / resourceLimit.unit.toSeconds(1)
                    DistributedTokenBucketRateLimiter(
                        resource, permitsPerSecond, resourceLimit.bucketCapacity!!, redisTemplate!!, permits
                    )
                }
            }
            else -> throw AcquireLockFailedException("Unsupported algorithm ${resourceLimit.algo}")
        }
    }

    private fun getAlgorithmOfRateLimiter(
        resource: String, resourceLimit: ResourceLimit, permits: Long = 1
    ): RateLimiter {
        val limitKey = generateKey(resource, resourceLimit)
        var rateLimiter = rateLimiterCache[limitKey]
        if (rateLimiter == null) {
            val newRateLimiter = createAlgorithmOfRateLimiter(resource, resourceLimit, permits)
            rateLimiter = rateLimiterCache.putIfAbsent(limitKey, newRateLimiter)
            if (rateLimiter == null) {
                rateLimiter = newRateLimiter
            }
        }
        return rateLimiter
    }

    abstract fun refreshRateLimitRule()

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AbstractRateLimiterService::class.java)

    }
}
