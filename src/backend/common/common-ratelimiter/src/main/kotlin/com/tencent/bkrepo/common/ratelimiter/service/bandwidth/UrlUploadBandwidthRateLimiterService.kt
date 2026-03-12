package com.tencent.bkrepo.common.ratelimiter.service.bandwidth

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.UrlUploadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.AbstractBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * URL上传带宽限流服务
 */
class UrlUploadBandwidthRateLimiterService(
    taskScheduler: ThreadPoolTaskScheduler,
    rateLimiterProperties: RateLimiterProperties,
    rateLimiterMetrics: RateLimiterMetrics,
    redisTemplate: RedisTemplate<String, String>,
    rateLimiterConfigService: RateLimiterConfigService
) : AbstractBandwidthRateLimiterService(
    taskScheduler,
    rateLimiterMetrics,
    redisTemplate,
    rateLimiterProperties,
    rateLimiterConfigService
) {

    override fun buildResource(request: HttpServletRequest): String {
        return request.requestURI
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> = emptyList()

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long {
        return applyPermits ?: 0L
    }

    override fun getLimitDimensions(): List<String> {
        return listOf(LimitDimension.URL_UPLOAD_BANDWIDTH.name)
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return UrlUploadBandwidthRateLimitRule::class.java
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "UrlUploadBandwidth:$resource"
    }

    override fun ignoreRequest(request: HttpServletRequest): Boolean = false
}
