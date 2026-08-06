package com.tencent.bkrepo.common.ratelimiter.service.url

import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.exception.AcquireLockFailedException
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.url.UrlPrefixDownloadBandwidthRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractBandwidthRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import com.tencent.bkrepo.common.ratelimiter.utils.RateLimiterBuilder
import com.tencent.bkrepo.common.ratelimiter.utils.ResourcePathUtils
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ConcurrentHashMap

/**
 * URL前缀下载限速服务。
 * - 使用 pathLengthCheck=false 的规则进行前缀匹配，/proj/repo/bigfiles/ 命中其所有子路径。
 * - generateKey 使用 resourceLimit.resource（规则前缀）作为 key，确保前缀下所有请求共享一个带宽桶。
 */
class UrlPrefixDownloadBandwidthRateLimiterService(
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
        return ResourcePathUtils.normalizeUri(request.requestURI)
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> = emptyList()

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long {
        return applyPermits ?: throw AcquireLockFailedException("apply permits is null")
    }

    override fun getLimitDimensions(): List<String> {
        return listOf(LimitDimension.URL_PREFIX_DOWNLOAD_BANDWIDTH.name)
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return UrlPrefixDownloadBandwidthRateLimitRule::class.java
    }

    override fun ignoreRequest(request: HttpServletRequest): Boolean {
        return request.method !in DOWNLOAD_REQUEST_METHOD
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "UrlPrefixDownloadBandwidth:${resourceLimit.resource}"
    }

    override fun initCompanionRateLimitRule() {
        Companion.rateLimiterCache = rateLimiterCache
        Companion.rateLimitRule = rateLimitRule!!
    }

    companion object {
        private lateinit var rateLimiterCache: ConcurrentHashMap<String, RateLimiter>
        private lateinit var rateLimitRule: RateLimitRule

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
