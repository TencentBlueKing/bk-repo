package com.tencent.bkrepo.common.ratelimiter.service.concurrent

import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.concurrent.UrlConcurrentRequestRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.ConcurrencyLimitService
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
), ConcurrencyLimitService {

    private val urlConcurrentCount = ConcurrentHashMap<String, AtomicInteger>()

    override fun getAlgorithmOfRateLimiter(resource: String, resourceLimit: ResourceLimit): RateLimiter {
        return super.getAlgorithmOfRateLimiter(resource, resourceLimit.copy(algo = Algorithms.SEMAPHORE.name))
    }

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
        if (!rateLimiterProperties.enabled) return
        if (rateLimitRule == null || rateLimitRule!!.isEmpty()) return
        val url = request.requestURI
        try {
            urlConcurrentCount.computeIfAbsent(url) { AtomicInteger(0) }.incrementAndGet()
            request.setAttribute(ACTIVE_ATTR, true)
            limitAndGetAcquiredLimiter(request, applyPermits)
                ?.let { request.setAttribute(ACQUIRED_LIMITER_ATTR, it) }
        } catch (e: Exception) {
            urlConcurrentCount.computeIfPresent(url) { _, counter ->
                if (counter.decrementAndGet() <= 0) null else counter
            }
            throw e
        }
    }

    override fun finish(request: HttpServletRequest) {
        if (request.getAttribute(ACTIVE_ATTR) == null) return
        val url = request.requestURI
        urlConcurrentCount.computeIfPresent(url) { _, counter ->
            if (counter.decrementAndGet() <= 0) null else counter
        }
        (request.getAttribute(ACQUIRED_LIMITER_ATTR) as? RateLimiter)?.release()
        request.removeAttribute(ACTIVE_ATTR)
        request.removeAttribute(ACQUIRED_LIMITER_ATTR)
    }

    fun getUrlConcurrentCount(url: String): Int {
        return urlConcurrentCount[url]?.get() ?: 0
    }

    companion object {
        const val ACQUIRED_LIMITER_ATTR = "RATE_LIMIT_URL_LIMITER"
        const val ACTIVE_ATTR = "RATE_LIMIT_URL_ACTIVE"
    }
}
