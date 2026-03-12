package com.tencent.bkrepo.common.ratelimiter.service.evict

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResLimitInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * 驱逐限流服务公共基类
 *
 * 不做实际令牌桶/计数器限流，只维护驱逐规则并提供 [findEvictRule] 查询。
 * resource 匹配优先级：/{project}/{repo} > /{project} > /user/{userId} > /ip/{ip}
 */
abstract class AbstractEvictRateLimiterService(
    taskScheduler: ThreadPoolTaskScheduler,
    rateLimiterProperties: RateLimiterProperties,
    rateLimiterMetrics: RateLimiterMetrics,
    redisTemplate: RedisTemplate<String, String>,
    rateLimiterConfigService: RateLimiterConfigService,
) : AbstractRateLimiterService(
    taskScheduler,
    rateLimiterProperties,
    rateLimiterMetrics,
    redisTemplate,
    rateLimiterConfigService,
) {
    /** key 前缀，由子类提供，例如 "UploadEvict" / "DownloadEvict" */
    protected abstract val keyPrefix: String

    override fun generateKey(resource: String, resourceLimit: ResourceLimit) =
        com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX + "$keyPrefix:$resource"

    override fun buildResource(request: HttpServletRequest) = ""

    override fun buildExtraResource(request: HttpServletRequest) = emptyList<String>()

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?) = 1L

    /**
     * 根据请求身份信息查找匹配的驱逐规则
     */
    fun findEvictRule(
        userId: String,
        clientIp: String,
        projectId: String?,
        repoName: String?,
    ): ResLimitInfo? {
        if (rateLimitRule == null || rateLimitRule!!.isEmpty()) return null
        val candidates = buildList {
            if (!projectId.isNullOrEmpty() && !repoName.isNullOrEmpty()) add("/$projectId/$repoName")
            if (!projectId.isNullOrEmpty()) add("/$projectId")
            add("/user/$userId")
            add("/ip/$clientIp")
        }
        return rateLimitRule!!.getRateLimitRule(
            ResInfo(resource = candidates.first(), extraResource = candidates.drop(1))
        )
    }
}
