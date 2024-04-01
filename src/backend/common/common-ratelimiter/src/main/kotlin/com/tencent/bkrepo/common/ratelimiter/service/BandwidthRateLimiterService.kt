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
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.enums.WorkScope
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.exception.InvalidResourceException
import com.tencent.bkrepo.common.ratelimiter.interceptor.MonitorRateLimiterInterceptorAdaptor
import com.tencent.bkrepo.common.ratelimiter.interceptor.RateLimiterInterceptor
import com.tencent.bkrepo.common.ratelimiter.interceptor.RateLimiterInterceptorChain
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.url.user.UserUrlRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.DownloadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.UsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.user.UserDownloadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.user.UserUploadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.stream.DistributedRateLimitInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.servlet.http.HttpServletRequest

abstract class BandwidthRateLimiterService(
    private val taskScheduler: ThreadPoolTaskScheduler,
    private val rateLimiterProperties: RateLimiterProperties,
    private val rateLimiterMetrics: RateLimiterMetrics,
    private val redisTemplate: RedisTemplate<String, String>? = null,
):  RateLimiterService {
    private var rateLimiterCache: ConcurrentHashMap<String, RateLimiter> = ConcurrentHashMap(256)

    private val interceptorChain: RateLimiterInterceptorChain =
        RateLimiterInterceptorChain(mutableListOf(MonitorRateLimiterInterceptorAdaptor(rateLimiterMetrics)))

    var rateLimitRule: RateLimitRule? = null

    var currentRuleHashCode: Int? = null

    init {
        taskScheduler.scheduleWithFixedDelay(this::refreshRateLimitRule, rateLimiterProperties.refreshDuration * 1000)
    }

    override fun limit(request: HttpServletRequest, applyPermits: Long?) {
        throw UnsupportedOperationException()
    }

    override fun addInterceptor(interceptor: RateLimiterInterceptor) {
        this.interceptorChain.addInterceptor(interceptor)
    }

    override fun addInterceptors(interceptors: List<RateLimiterInterceptor>) {
        if (interceptors.isNotEmpty()) {
            this.interceptorChain.addInterceptors(interceptors)
        }
    }

    fun getBandwidthRateLimit(request: HttpServletRequest): ResourceLimit? {
        if (!rateLimiterProperties.enabled) {
            return null
        }
        val resource = buildResource(request)

        val resourceLimit = rateLimitRule?.getRateLimitRule(resource)
            ?: rateLimitRule?.getRateLimitRule(
                resource,
                buildResourceTemplate(request)
            )
        if (resourceLimit == null) {
            logger.info("no rule in ${this.javaClass.simpleName} for request ${request.requestURI}")
        }
        return resourceLimit
    }


    fun bandwidthRateLimit(inputStream: InputStream, resourceLimit: ResourceLimit): DistributedRateLimitInputStream {
        val rateLimiter = getAlgorithmOfRateLimiter(resource, resourceLimit)
        return DistributedRateLimitInputStream(inputStream, rateLimiter)
    }

    abstract fun generateKey(resource: String, resourceLimit: ResourceLimit): String

    abstract fun buildResource(request: HttpServletRequest): String

    abstract fun buildResourceTemplate(request: HttpServletRequest): List<String>

    abstract fun applyPermits(request: HttpServletRequest, applyPermits: Long?): Long

    abstract fun getLimitDimensions(): List<LimitDimension>

    abstract fun getRateLimitRuleClass(): Class<out RateLimitRule>


    open fun createAlgorithmOfRateLimiter(resource: String, resourceLimit: ResourceLimit): RateLimiter {
        if (resourceLimit.limit < 0) {
            throw InvalidResourceException("config limit is ${resourceLimit.limit}")
        }
        return when (resourceLimit.algo) {
            Algorithms.FIXED_WINDOW -> {
                if (rateLimiterProperties.scope == WorkScope.LOCAL) {
                    FixedWindowRateLimiter(resourceLimit.limit, resourceLimit.unit)
                } else {
                    DistributedFixedWindowRateLimiter(
                        resource, resourceLimit.limit, resourceLimit.unit, redisTemplate!!
                    )
                }
            }
            Algorithms.TOKEN_BUCKET -> {
                if (rateLimiterProperties.scope == WorkScope.LOCAL) {
                    val permitsPerSecond = resourceLimit.limit / resourceLimit.unit.toSeconds(1)
                    TokenBucketRateLimiter(permitsPerSecond)
                } else {
                    if (resourceLimit.bucketCapacity == null || resourceLimit.bucketCapacity!! <= 0) {
                        throw AcquireLockFailedException("Resource limit config $resourceLimit is illegal")
                    }
                    val permitsPerSecond = resourceLimit.limit / resourceLimit.unit.toSeconds(1).toDouble()
                    DistributedTokenBucketRateLimiter(
                        resource, permitsPerSecond, resourceLimit.bucketCapacity!!, redisTemplate!!
                    )
                }
            }
            else -> throw AcquireLockFailedException("Unsupported algorithm ${resourceLimit.algo}")
        }
    }

    //TODO 配置异常需要处理
    private fun refreshRateLimitRule() {
        if (!rateLimiterProperties.enabled) return
        val usageRuleConfigs = rateLimiterProperties.rules.filter {
            it.limitDimension in getLimitDimensions()
        }
        // 配置规则变更后需要清理缓存的限流算法实现
        val newRuleHashCode = usageRuleConfigs.hashCode()
        if (currentRuleHashCode == newRuleHashCode) {
            if (rateLimiterCache.size > rateLimiterProperties.cacheCapacity) {
                rateLimiterCache.clear()
            }
            return
        }
        val usageRules = when (getRateLimitRuleClass()) {
            UrlRateLimitRule::class.java -> UrlRateLimitRule()
            UsageRateLimitRule::class.java -> UsageRateLimitRule()
            DownloadUsageRateLimitRule::class.java -> DownloadUsageRateLimitRule()
            UserDownloadUsageRateLimitRule::class.java -> UserDownloadUsageRateLimitRule()
            UserUploadUsageRateLimitRule::class.java -> UserUploadUsageRateLimitRule()
            UserUrlRateLimitRule::class.java -> UserUrlRateLimitRule()
            else -> return
        }
        usageRules.addRateLimitRules(usageRuleConfigs)
        rateLimitRule = usageRules
        rateLimiterCache.clear()
        currentRuleHashCode = newRuleHashCode
        logger.info("rules in ${this.javaClass.simpleName} for request has been refreshed!")
    }

    private fun getAlgorithmOfRateLimiter(
        resource: String, resourceLimit: ResourceLimit
    ): RateLimiter {
        val limitKey = generateKey(resource, resourceLimit)
        var rateLimiter = rateLimiterCache[limitKey]
        if (rateLimiter == null) {
            val newRateLimiter = createAlgorithmOfRateLimiter(limitKey, resourceLimit)
            rateLimiter = rateLimiterCache.putIfAbsent(limitKey, newRateLimiter)
            if (rateLimiter == null) {
                rateLimiter = newRateLimiter
            }
        }
        return rateLimiter
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(BandwidthRateLimiterService::class.java)
    }
}
