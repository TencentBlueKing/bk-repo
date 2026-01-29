package com.tencent.bkrepo.common.ratelimiter.service.bandwidth.user

import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.user.UserDownloadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import com.tencent.bkrepo.common.ratelimiter.utils.RateLimiterBuilder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户下载带宽限流器实现，针对user、user+project、user+repo进行限流
 */
class UserDownloadBandwidthRateLimiterService(
    taskScheduler: ThreadPoolTaskScheduler,
    rateLimiterProperties: RateLimiterProperties,
    rateLimiterMetrics: RateLimiterMetrics,
    redisTemplate: RedisTemplate<String, String>,
    rateLimiterConfigService: RateLimiterConfigService
) : UserUploadBandwidthRateLimiterService(
    taskScheduler,
    rateLimiterProperties,
    rateLimiterMetrics,
    redisTemplate,
    rateLimiterConfigService
) {

    override fun initCompanionRateLimitRule() {
        Companion.rateLimiterCache = rateLimiterCache
        Companion.rateLimitRule = rateLimitRule!!
        Companion.redisTemplate = redisTemplate
    }

    override fun getLimitDimensions(): List<String> {
        return listOf(
            LimitDimension.USER_DOWNLOAD_BANDWIDTH.name
        )
    }

    override fun ignoreRequest(request: HttpServletRequest): Boolean {
        return request.method !in DOWNLOAD_REQUEST_METHOD
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return UserDownloadBandwidthRateLimitRule::class.java
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "UserDownloadBandwidth:$resource"
    }

    companion object {
        lateinit var rateLimiterCache: ConcurrentHashMap<String, RateLimiter>
        lateinit var rateLimitRule: RateLimitRule
        lateinit var redisTemplate: RedisTemplate<String, String>

        fun getAlgorithmOfRateLimiter(
            limitKey: String,
            resourceLimit: ResourceLimit,
            redInfo: ResInfo? = null
        ): RateLimiter {
            return RateLimiterBuilder.getAlgorithmOfRateLimiter(
                limitKey, resourceLimit, redisTemplate, rateLimiterCache, redInfo, rateLimitRule
            )
        }
    }
}

