package com.tencent.bkrepo.common.ratelimiter.service.evict

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.evict.UploadEvictRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

class UploadEvictRateLimiterService(
    taskScheduler: ThreadPoolTaskScheduler,
    rateLimiterProperties: RateLimiterProperties,
    rateLimiterMetrics: RateLimiterMetrics,
    redisTemplate: RedisTemplate<String, String>,
    rateLimiterConfigService: RateLimiterConfigService,
) : AbstractEvictRateLimiterService(
    taskScheduler, rateLimiterProperties, rateLimiterMetrics, redisTemplate, rateLimiterConfigService,
) {
    override val keyPrefix = "UploadEvict"
    override fun getLimitDimensions() = listOf(LimitDimension.UPLOAD_EVICT.name)
    override fun getRateLimitRuleClass(): Class<out RateLimitRule> = UploadEvictRateLimitRule::class.java
}
