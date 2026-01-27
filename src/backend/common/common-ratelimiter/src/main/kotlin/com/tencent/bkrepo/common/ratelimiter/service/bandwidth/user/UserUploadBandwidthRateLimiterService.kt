package com.tencent.bkrepo.common.ratelimiter.service.bandwidth.user

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.bandwidth.user.UserUploadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.AbstractBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import com.tencent.bkrepo.common.ratelimiter.utils.RateLimiterBuilder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户上传带宽限流器实现，针对user、user+project、user+repo进行限流
 */
open class UserUploadBandwidthRateLimiterService(
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

    override fun initCompanionRateLimitRule() {
        Companion.rateLimiterCache = rateLimiterCache
        Companion.rateLimitRule = rateLimitRule!!
        Companion.redisTemplate = redisTemplate
    }

    override fun buildResource(request: HttpServletRequest): String {
        val (projectId, repoName) = getRepoInfoFromAttribute(request)
        val userId = HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER
        return if (repoName.isNullOrEmpty()) {
            "$userId:/$projectId/"
        } else {
            "$userId:/$projectId/$repoName/"
        }
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> {
        val (projectId, repoName) = getRepoInfoFromAttribute(request)
        val userId = HttpContextHolder.getRequestOrNull()?.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER
        val result = mutableListOf<String>()
        if (!repoName.isNullOrEmpty()) {
            result.add("$userId:/$projectId/")
        }
        result.add("$userId:")
        return result
    }

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long {
        return applyPermits ?: throw com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException(
            "apply permits is null"
        )
    }

    override fun getLimitDimensions(): List<String> {
        return listOf(
            LimitDimension.USER_UPLOAD_BANDWIDTH.name
        )
    }

    override fun ignoreRequest(request: HttpServletRequest): Boolean {
        return request.method !in UPLOAD_REQUEST_METHOD
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return UserUploadBandwidthRateLimitRule::class.java
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "UserUploadBandwidth:$resource"
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

