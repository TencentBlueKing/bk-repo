package com.tencent.bkrepo.common.ratelimiter.service.concurrent

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.concurrent.UrlConcurrentRequestRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * URL并发请求限流服务
 * 用于限制特定URL的并发执行数量，防止数据库高负载
 */
class UrlConcurrentRequestLimiterService(
    taskScheduler: ThreadPoolTaskScheduler,
    rateLimiterProperties: RateLimiterProperties,
    private val rateLimiterMetrics: RateLimiterMetrics,
    redisTemplate: RedisTemplate<String, String>,
    rateLimiterConfigService: RateLimiterConfigService
) : AbstractRateLimiterService(
    taskScheduler,
    rateLimiterProperties,
    rateLimiterMetrics,
    redisTemplate,
    rateLimiterConfigService
) {

    private val urlConcurrentCount = ConcurrentHashMap<String, AtomicInteger>()

    override fun buildResource(request: HttpServletRequest): String {
        return request.requestURI
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> = emptyList()

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long = 1L

    override fun getLimitDimensions(): List<String> {
        return listOf(LimitDimension.URL_CONCURRENT_REQUEST.name)
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return UrlConcurrentRequestRateLimitRule::class.java
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "UrlConcurrentRequest:$resource"
    }

    override fun limit(request: HttpServletRequest, applyPermits: Long?) {
        val url = request.requestURI
        try {
            urlConcurrentCount.computeIfAbsent(url) { AtomicInteger(0) }.incrementAndGet()
            super.limit(request, applyPermits)
        } catch (e: Exception) {
            urlConcurrentCount[url]?.decrementAndGet()
            throw e
        }
    }

    fun finish(request: HttpServletRequest) {
        val url = request.requestURI
        urlConcurrentCount[url]?.decrementAndGet()
    }

    fun getUrlConcurrentCount(url: String): Int {
        return urlConcurrentCount[url]?.get() ?: 0
    }
}
