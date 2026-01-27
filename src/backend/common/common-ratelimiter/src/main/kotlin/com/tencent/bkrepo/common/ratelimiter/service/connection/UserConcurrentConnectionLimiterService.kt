package com.tencent.bkrepo.common.ratelimiter.service.connection

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.connection.UserConcurrentConnectionRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
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
) {

    private val userConnectionCount = ConcurrentHashMap<String, AtomicInteger>()

    override fun buildResource(request: HttpServletRequest): String {
        val userId = SecurityUtils.getUserId()
        return "/user/$userId"
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
        val userId = SecurityUtils.getUserId()
        try {
            userConnectionCount.computeIfAbsent(userId) { AtomicInteger(0) }.incrementAndGet()
            super.limit(request, applyPermits)
        } catch (e: Exception) {
            userConnectionCount[userId]?.decrementAndGet()
            throw e
        }
    }

    fun finish(request: HttpServletRequest) {
        val userId = SecurityUtils.getUserId()
        userConnectionCount[userId]?.decrementAndGet()
    }

    fun getUserConnections(userId: String): Int {
        return userConnectionCount[userId]?.get() ?: 0
    }
}
