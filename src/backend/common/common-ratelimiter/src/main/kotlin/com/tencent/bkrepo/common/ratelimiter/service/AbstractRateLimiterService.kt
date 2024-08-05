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
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedLeakyRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedSlidingWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.DistributedTokenBucketRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.FixedWindowRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.LeakyRateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.algorithm.SlidingWindowRateLimiter
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
import com.tencent.bkrepo.common.ratelimiter.interceptor.TargetRateLimiterInterceptorAdaptor
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.UploadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.url.user.UserUrlRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.DownloadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.UploadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.user.UserDownloadUsageRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.usage.user.UserUploadUsageRateLimitRule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.servlet.HandlerMapping
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest

/**
 * 限流器抽象实现
 */
abstract class AbstractRateLimiterService(
    private val taskScheduler: ThreadPoolTaskScheduler,
    private val rateLimiterProperties: RateLimiterProperties,
    private val rateLimiterMetrics: RateLimiterMetrics,
    private val redisTemplate: RedisTemplate<String, String>? = null,
) : RateLimiterService {

    // 资源对应限限流算法缓存
    private var rateLimiterCache: ConcurrentHashMap<String, RateLimiter> = ConcurrentHashMap(256)

    val interceptorChain: RateLimiterInterceptorChain =
        RateLimiterInterceptorChain(mutableListOf(
            MonitorRateLimiterInterceptorAdaptor(rateLimiterMetrics),
            TargetRateLimiterInterceptorAdaptor()
        ))

    // 限流规则配置
    var rateLimitRule: RateLimitRule? = null

    // 当前限流规则配置hashcode
    var currentRuleHashCode: Int? = null

    init {
        taskScheduler.scheduleWithFixedDelay(this::refreshRateLimitRule, rateLimiterProperties.refreshDuration * 1000)
    }

    override fun limit(request: HttpServletRequest, applyPermits: Long?) {
        if (!rateLimiterProperties.enabled) {
            return
        }
        if (ignoreRequest(request)) return
        var resource = buildResource(request)
        var resourceLimit: ResourceLimit? = null
        var resLimitInfo: ResLimitInfo? = null
        val applyPermits = getApplyPermits(request, applyPermits)
        var pass = false
        var exception: Exception? = null
        try {
            val resInfo = ResInfo(
                resource = resource,
                extraResource = buildExtraResource(request)
            )
            resLimitInfo = rateLimitRule?.getRateLimitRule(resInfo)
            resourceLimit = resLimitInfo?.resourceLimit
            if (resourceLimit == null) {
                logger.info("no rule in ${this.javaClass.simpleName} for request ${request.requestURI}")
                return
            }
            resource = resLimitInfo?.resource!!
            interceptorChain.doBeforeLimitCheck(resource, resourceLimit)
            val rateLimiter = getAlgorithmOfRateLimiter(resource, resourceLimit)
            pass = rateLimiter.tryAcquire(applyPermits)
            if (!pass) {
                logger.warn("resourceLimit for $resource is $resourceLimit")
                val msg = "$resource has exceeded max rate limit: ${resourceLimit.limit} /${resourceLimit.unit}"
                if (rateLimiterProperties.dryRun) {
                    logger.warn(msg)
                } else {
                    throw OverloadException(msg)
                }
            }
        } catch (e: OverloadException) {
            pass = false
            throw e
        } catch (e: AcquireLockFailedException) {
            exception = e
            throw e
        } catch (e: InvalidResourceException) {
            logger.warn("$resourceLimit is invalid")
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

    /**
     * 生成资源对应的唯一key
     */
    abstract fun generateKey(resource: String, resourceLimit: ResourceLimit): String

    /**
     * 根据请求获取对应的资源，用于查找对应限流规则
     */
    abstract fun buildResource(request: HttpServletRequest): String

    /**
     * 根据请求获取对其他资源信息，用于查找对应限流规则
     */
    abstract fun buildExtraResource(request: HttpServletRequest): List<String>

    /**
     * 根据请求获取需要申请的许可数
     */
    abstract fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long

    /**
     * 限流器实现对应的维度
     */
    abstract fun getLimitDimensions(): List<String>

    /**
     * 获取对应限流规则配置实现
     */
    abstract fun getRateLimitRuleClass(): Class<out RateLimitRule>

    /**
     * 对请求进行过滤，不进行限流
     */
    open fun ignoreRequest(request: HttpServletRequest): Boolean {
        return false
    }

    /**
     * 根据资源和限流规则生成对应限流算法
     */
    open fun createAlgorithmOfRateLimiter(resource: String, resourceLimit: ResourceLimit): RateLimiter {
        if (resourceLimit.limit < 0) {
            throw InvalidResourceException("config limit is ${resourceLimit.limit}")
        }
        val unit = try {
            TimeUnit.valueOf(resourceLimit.unit)
        } catch (e: IllegalArgumentException) {
            throw InvalidResourceException("config unit is ${resourceLimit.unit}")
        }
        return when (resourceLimit.algo) {
            Algorithms.FIXED_WINDOW.name -> {
                if (resourceLimit.scope == WorkScope.LOCAL.name) {
                    FixedWindowRateLimiter(resourceLimit.limit, unit)
                } else {
                    DistributedFixedWindowRateLimiter(
                        resource, resourceLimit.limit, unit, redisTemplate!!
                    )
                }
            }
            Algorithms.TOKEN_BUCKET.name -> {
                if (resourceLimit.scope == WorkScope.LOCAL.name) {
                    val permitsPerSecond = resourceLimit.limit / unit.toSeconds(1)
                    TokenBucketRateLimiter(permitsPerSecond)
                } else {
                    if (resourceLimit.capacity == null || resourceLimit.capacity!! <= 0) {
                        throw AcquireLockFailedException("Resource limit config $resourceLimit is illegal")
                    }
                    val permitsPerSecond = resourceLimit.limit / unit.toSeconds(1).toDouble()
                    DistributedTokenBucketRateLimiter(
                        resource, permitsPerSecond, resourceLimit.capacity!!, redisTemplate!!
                    )
                }
            }
            Algorithms.SLIDING_WINDOW.name -> {
                val interval = resourceLimit.interval ?: 1
                if (resourceLimit.scope == WorkScope.LOCAL.name) {
                    SlidingWindowRateLimiter(resourceLimit.limit, interval, unit)
                } else {
                    DistributedSlidingWindowRateLimiter(
                        resource, resourceLimit.limit, interval, unit, redisTemplate!!
                    )
                }
            }
            Algorithms.LEAKY_BUCKET.name -> {
                if (resourceLimit.capacity == null || resourceLimit.capacity!! <= 0) {
                    throw AcquireLockFailedException("Resource limit config $resourceLimit is illegal")
                }
                val rate = resourceLimit.limit / unit.toSeconds(1).toDouble()
                if (resourceLimit.scope == WorkScope.LOCAL.name) {
                    LeakyRateLimiter(rate, resourceLimit.capacity!!)
                } else {

                    DistributedLeakyRateLimiter(
                        resource, rate, resourceLimit.capacity!!, redisTemplate!!
                    )
                }
            }
            else -> {
                throw InvalidResourceException("config algo is ${resourceLimit.algo}")
            }
        }
    }

    fun getRepoInfo(request: HttpServletRequest): Pair<String?, String?> {
        val projectId = ((request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
            as LinkedHashMap<*, *>)["projectId"] as String?
        val repoName = ((request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
            as LinkedHashMap<*, *>)["repoName"] as String?
        if (projectId.isNullOrEmpty()) {
            throw InvalidResourceException("Could not find projectId from request ${request.requestURI}")
        }
        return Pair(projectId, repoName)
    }

    /**
     * 配置规则刷新
     */
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
            UploadUsageRateLimitRule::class.java -> UploadUsageRateLimitRule()
            DownloadUsageRateLimitRule::class.java -> DownloadUsageRateLimitRule()
            UserDownloadUsageRateLimitRule::class.java -> UserDownloadUsageRateLimitRule()
            UserUploadUsageRateLimitRule::class.java -> UserUploadUsageRateLimitRule()
            UserUrlRateLimitRule::class.java -> UserUrlRateLimitRule()
            UploadBandwidthRateLimitRule::class.java -> UploadBandwidthRateLimitRule()
            else -> return
        }
        usageRules.addRateLimitRules(usageRuleConfigs)
        rateLimitRule = usageRules
        rateLimiterCache.clear()
        currentRuleHashCode = newRuleHashCode
        logger.info("rules in ${this.javaClass.simpleName} for request has been refreshed!")
    }

    /**
     * 获取对应限流算法实现
     */
    fun getAlgorithmOfRateLimiter(
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
        private val logger: Logger = LoggerFactory.getLogger(AbstractRateLimiterService::class.java)
        val UPLOAD_REQUEST_METHOD = listOf(HttpMethod.POST.name, HttpMethod.PUT.name, HttpMethod.PATCH.name)
        val DOWNLOAD_REQUEST_METHOD = listOf(HttpMethod.GET.name)
    }
}
