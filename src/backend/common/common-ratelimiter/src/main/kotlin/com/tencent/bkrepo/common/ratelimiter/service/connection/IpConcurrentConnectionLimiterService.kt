package com.tencent.bkrepo.common.ratelimiter.service.connection

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.connection.IpConcurrentConnectionRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * IP并发连接数限流服务
 */
class IpConcurrentConnectionLimiterService(
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

    private val ipConnectionCount = ConcurrentHashMap<String, AtomicInteger>()

    override fun buildResource(request: HttpServletRequest): String {
        val ip = getClientIp()
        return "/ip/$ip"
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> = emptyList()

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long = 1L

    override fun getLimitDimensions(): List<String> {
        return listOf(LimitDimension.IP_CONCURRENT_CONNECTION.name)
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return IpConcurrentConnectionRateLimitRule::class.java
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "IpConnection:$resource"
    }

    override fun limit(request: HttpServletRequest, applyPermits: Long?) {
        val ip = getClientIp()
        try {
            ipConnectionCount.computeIfAbsent(ip) { AtomicInteger(0) }.incrementAndGet()
            super.limit(request, applyPermits)
        } catch (e: Exception) {
            ipConnectionCount[ip]?.decrementAndGet()
            throw e
        }
    }

    fun finish(request: HttpServletRequest) {
        val ip = getClientIp()
        ipConnectionCount[ip]?.decrementAndGet()
    }

    fun getIpConnections(ip: String): Int {
        return ipConnectionCount[ip]?.get() ?: 0
    }

    private fun getClientIp(): String {
        return HttpContextHolder.getClientAddressFromAttribute()
    }
}
