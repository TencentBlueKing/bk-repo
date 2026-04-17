package com.tencent.bkrepo.common.ratelimiter.service.ip

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.ip.IpRateLimitRule
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * IP限流器实现，防止单个IP恶意访问
 */
class IpRateLimiterService(
    taskScheduler: ThreadPoolTaskScheduler,
    rateLimiterProperties: RateLimiterProperties,
    rateLimiterMetrics: RateLimiterMetrics,
    redisTemplate: RedisTemplate<String, String>,
    rateLimiterConfigService: RateLimiterConfigService
) : AbstractRateLimiterService(
    taskScheduler,
    rateLimiterProperties,
    rateLimiterMetrics,
    redisTemplate,
    rateLimiterConfigService
) {

    override fun buildResource(request: HttpServletRequest): String {
        // 包含 IP 信息，确保每个 IP 有独立的限流 key
        val clientIp = getClientIp()
        return "/ip/$clientIp"
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> {
        // 将客户端 IP 传递到 extraResource 中，供规则匹配使用
        return listOf(getClientIp())
    }

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long {
        return 1L // IP限流通常按请求次数计数
    }

    override fun getLimitDimensions(): List<String> {
        return listOf(LimitDimension.IP.name)
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return IpRateLimitRule::class.java
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "Ip:$resource"
    }

    private fun getClientIp(): String {
        return HttpContextHolder.getClientAddressFromAttribute()
    }

    override fun ignoreRequest(request: HttpServletRequest): Boolean {
        val clientIp = getClientIp()
        return clientIp == StringPool.UNKNOWN
    }
}



