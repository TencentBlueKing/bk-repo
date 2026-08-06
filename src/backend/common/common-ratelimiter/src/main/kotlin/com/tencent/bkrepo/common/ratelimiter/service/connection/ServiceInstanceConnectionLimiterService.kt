package com.tencent.bkrepo.common.ratelimiter.service.connection

import com.tencent.bkrepo.common.ratelimiter.algorithm.RateLimiter
import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.Algorithms
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.connection.ServiceInstanceConnectionRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
import com.tencent.bkrepo.common.ratelimiter.service.ConcurrencyLimitService
import com.tencent.bkrepo.common.ratelimiter.service.user.RateLimiterConfigService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.atomic.AtomicInteger

/**
 * 服务实例连接数限流器
 * 基于 AbstractRateLimiterService 实现，支持从 ResourceLimit 配置中读取限流规则
 */
class ServiceInstanceConnectionLimiterService(
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
), ConcurrencyLimitService {

    @Value("\${spring.application.name}")
    private val serviceName: String = ""

    @Value("\${spring.cloud.client.ip-address}")
    private val host: String = "127.0.0.1"

    // 本地计数器，用于监控指标
    private val localConnectionCount = AtomicInteger(0)

    private val instanceId: String by lazy {
        "$serviceName:$host"
    }

    override fun getAlgorithmOfRateLimiter(resource: String, resourceLimit: ResourceLimit): RateLimiter {
        return super.getAlgorithmOfRateLimiter(resource, resourceLimit.copy(algo = Algorithms.SEMAPHORE.name))
    }

    override fun buildResource(request: HttpServletRequest): String {
        return instanceId
    }

    override fun buildExtraResource(request: HttpServletRequest): List<String> {
        return emptyList()
    }

    override fun getApplyPermits(request: HttpServletRequest, applyPermits: Long?): Long {
        return 1L
    }

    override fun getLimitDimensions(): List<String> {
        return listOf(LimitDimension.SERVICE_INSTANCE_CONNECTION.name)
    }

    override fun getRateLimitRuleClass(): Class<out RateLimitRule> {
        return ServiceInstanceConnectionRateLimitRule::class.java
    }

    override fun generateKey(resource: String, resourceLimit: ResourceLimit): String {
        return KEY_PREFIX + "Connection:$resource"
    }

    override fun limit(request: HttpServletRequest, applyPermits: Long?) {
        if (!rateLimiterProperties.enabled) return
        if (rateLimitRule == null || rateLimitRule!!.isEmpty()) return
        try {
            localConnectionCount.incrementAndGet()
            request.setAttribute(ACTIVE_ATTR, true)
            request.setAttribute(START_TIME_ATTR, System.nanoTime())
            limitAndGetAcquiredLimiter(request, applyPermits)
                ?.let { request.setAttribute(ACQUIRED_LIMITER_ATTR, it) }
            rateLimiterMetrics.recordConnectionAccepted()
        } catch (e: Exception) {
            localConnectionCount.decrementAndGet()
            rateLimiterMetrics.recordConnectionRejected()
            throw e
        }
    }

    override fun finish(request: HttpServletRequest) {
        if (request.getAttribute(ACTIVE_ATTR) == null) return
        localConnectionCount.decrementAndGet()
        (request.getAttribute(ACQUIRED_LIMITER_ATTR) as? RateLimiter)?.release()
        val startTimeNanos = request.getAttribute(START_TIME_ATTR) as? Long
        request.removeAttribute(ACTIVE_ATTR)
        request.removeAttribute(ACQUIRED_LIMITER_ATTR)
        request.removeAttribute(START_TIME_ATTR)
        startTimeNanos?.let { rateLimiterMetrics.recordConnectionDuration(it) }
    }

    fun getCurrentConnections(): Int {
        return localConnectionCount.get()
    }

    fun getMaxConnectionsConfig(): Int {
        val resInfo = ResInfo(instanceId, emptyList())
        val resourceLimit = (rateLimitRule as? ServiceInstanceConnectionRateLimitRule)
            ?.getRateLimitRule(resInfo)
            ?.resourceLimit
        return resourceLimit?.limit?.toInt() ?: Int.MAX_VALUE
    }

    fun getConnectionUsageRate(): Double {
        val current = getCurrentConnections()
        val max = getMaxConnectionsConfig()
        if (max == Int.MAX_VALUE) {
            return 0.0
        }
        return current.toDouble() / max
    }

    companion object {
        const val ACQUIRED_LIMITER_ATTR = "RATE_LIMIT_INSTANCE_LIMITER"
        const val ACTIVE_ATTR = "RATE_LIMIT_INSTANCE_ACTIVE"
        const val START_TIME_ATTR = "RATE_LIMIT_INSTANCE_START_TIME"
    }
}



