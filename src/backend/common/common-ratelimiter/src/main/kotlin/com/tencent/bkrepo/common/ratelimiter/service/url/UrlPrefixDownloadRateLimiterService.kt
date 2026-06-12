package com.tencent.bkrepo.common.ratelimiter.service.url

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlPrefixDownloadRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import com.tencent.bkrepo.common.ratelimiter.utils.ResourcePathUtils
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * 下载请求(GET) URL 前缀限频实现，支持前缀匹配。
 */
class UrlPrefixDownloadRateLimiterService(
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

    override fun ignoreRequest(request: HttpServletRequest): Boolean {
        return request.method !in DOWNLOAD_REQUEST_METHOD
    }

    override fun buildResource(request: HttpServletRequest): String {
        return ResourcePathUtils.normalizeUri(request.requestURI)
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> {
        return emptyList()
    }

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long {
        return 1
    }

    override fun getLimitDimensions(): List<String> {
        return listOf(LimitDimension.URL_PREFIX_DOWNLOAD_RATE.name)
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return UrlPrefixDownloadRateLimitRule::class.java
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        val prefix = ResourcePathUtils.normalizeUri(resourceLimit.resource)
        return KEY_PREFIX + "UrlPrefixDownloadRate:$prefix"
    }
}
