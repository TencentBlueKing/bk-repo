package com.tencent.bkrepo.common.ratelimiter.service.concurrent

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.concurrent.UserUrlConcurrentRequestRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 用户+URL并发请求限流服务
 * 用于限制特定用户对特定URL的并发执行数量
 */
class UserUrlConcurrentRequestLimiterService(
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

    private val userUrlConcurrentCount = ConcurrentHashMap<String, AtomicInteger>()

    override fun buildResource(request: HttpServletRequest): String {
        val userId = SecurityUtils.getUserId()
        return "/user/$userId${request.requestURI}"
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> = emptyList()

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long = 1L

    override fun getLimitDimensions(): List<String> {
        return listOf(LimitDimension.USER_URL_CONCURRENT_REQUEST.name)
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return UserUrlConcurrentRequestRateLimitRule::class.java
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "UserUrlConcurrentRequest:$resource"
    }

    override fun limit(request: HttpServletRequest, applyPermits: Long?) {
        val resource = buildResource(request)
        try {
            userUrlConcurrentCount.computeIfAbsent(resource) { AtomicInteger(0) }.incrementAndGet()
            super.limit(request, applyPermits)
        } catch (e: Exception) {
            userUrlConcurrentCount[resource]?.decrementAndGet()
            throw e
        }
    }

    fun finish(request: HttpServletRequest) {
        val resource = buildResource(request)
        userUrlConcurrentCount[resource]?.decrementAndGet()
    }

    fun getUserUrlConcurrentCount(userId: String, url: String): Int {
        val resource = "/user/$userId$url"
        return userUrlConcurrentCount[resource]?.get() ?: 0
    }
}
