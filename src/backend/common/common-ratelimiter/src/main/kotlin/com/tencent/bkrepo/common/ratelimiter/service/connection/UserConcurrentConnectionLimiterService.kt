package com.tencent.bkrepo.common.ratelimiter.service.connection

import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.connection.UserConcurrentConnectionRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.ConcurrencyLimitService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 用户并发连接数限流服务
 */
class UserConcurrentConnectionLimiterService(
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

    private val userConnectionCount = ConcurrentHashMap<String, AtomicInteger>()

    override fun getAlgorithmOfRateLimiter(resource: String, resourceLimit: ResourceLimit): RateLimiter {
        return super.getAlgorithmOfRateLimiter(resource, resourceLimit.copy(algo = Algorithms.SEMAPHORE.name))
    }

    override fun buildResource(request: HttpServletRequest): String {
        val userId = SecurityUtils.getUserId()
        return "/user/$userId/"
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> = emptyList()

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long = 1L

    override fun getLimitDimensions(): List<String> {
        return listOf(LimitDimension.USER_CONCURRENT_CONNECTION.name)
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return UserConcurrentConnectionRateLimitRule::class.java
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "UserConnection:$resource"
    }

    override fun limit(request: HttpServletRequest, applyPermits: Long?) {
        if (!rateLimiterProperties.enabled) return
        if (rateLimitRule == null || rateLimitRule!!.isEmpty()) return
        val userId = SecurityUtils.getUserId()
        try {
            userConnectionCount.computeIfAbsent(userId) { AtomicInteger(0) }.incrementAndGet()
            request.setAttribute(USER_ID_ATTR, userId)
            limitAndGetAcquiredLimiter(request, applyPermits)
                ?.let { request.setAttribute(ACQUIRED_LIMITER_ATTR, it) }
        } catch (e: Exception) {
            userConnectionCount.computeIfPresent(userId) { _, counter ->
                if (counter.decrementAndGet() <= 0) null else counter
            }
            throw e
        }
    }

    override fun finish(request: HttpServletRequest) {
        val userId = request.getAttribute(USER_ID_ATTR) as? String ?: return
        userConnectionCount.computeIfPresent(userId) { _, counter ->
            if (counter.decrementAndGet() <= 0) null else counter
        }
        (request.getAttribute(ACQUIRED_LIMITER_ATTR) as? RateLimiter)?.release()
        request.removeAttribute(USER_ID_ATTR)
        request.removeAttribute(ACQUIRED_LIMITER_ATTR)
    }

    fun getUserConnections(userId: String): Int {
        return userConnectionCount[userId]?.get() ?: 0
    }

    companion object {
        const val USER_ID_ATTR = "RATE_LIMIT_USER_ID"
        const val ACQUIRED_LIMITER_ATTR = "RATE_LIMIT_USER_LIMITER"
    }
}
