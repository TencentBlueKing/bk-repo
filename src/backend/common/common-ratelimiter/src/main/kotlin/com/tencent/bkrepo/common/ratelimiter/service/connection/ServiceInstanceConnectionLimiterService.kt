package com.tencent.bkrepo.common.ratelimiter.service.connection

import com.tencent.bkrepo.common.ratelimiter.config.RateLimiterProperties
import com.tencent.bkrepo.common.ratelimiter.constant.KEY_PREFIX
import com.tencent.bkrepo.common.ratelimiter.enums.LimitDimension
import com.tencent.bkrepo.common.ratelimiter.metrics.RateLimiterMetrics
import com.tencent.bkrepo.common.ratelimiter.rule.RateLimitRule
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResInfo
import com.tencent.bkrepo.common.ratelimiter.rule.common.ResourceLimit
import com.tencent.bkrepo.common.ratelimiter.rule.connection.ServiceInstanceConnectionRateLimitRule
import com.tencent.bkrepo.common.ratelimiter.service.AbstractRateLimiterService
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
) {

    @Value("\${spring.application.name}")
    private val serviceName: String = ""

    @Value("\${spring.cloud.client.ip-address}")
    private val host: String = "127.0.0.1"

    @Value("\${server.port:8080}")
    private val serverPort: Int = 8080

    // 本地计数器，用于监控指标
    private val localConnectionCount = AtomicInteger(0)

    private val instanceId: String by lazy {
        "$serviceName:$host:$serverPort"
    }

    override fun buildResource(request: HttpServletRequest): String {
        return "/instance/$instanceId"
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
        try {
            localConnectionCount.incrementAndGet()
            super.limit(request, applyPermits)
            rateLimiterMetrics.recordConnectionAccepted()
        } catch (e: Exception) {
            localConnectionCount.decrementAndGet()
            rateLimiterMetrics.recordConnectionRejected()
            throw e
        }
    }

    fun finish(request: HttpServletRequest, exception: Exception?, startTimeNanos: Long?) {
        localConnectionCount.decrementAndGet()
        startTimeNanos?.let { rateLimiterMetrics.recordConnectionDuration(it) }
    }

    /**
     * 获取当前连接数（用于监控指标）
     */
    fun getCurrentConnections(): Int {
        return localConnectionCount.get()
    }

    /**
     * 获取最大连接数配置
     */
    fun getMaxConnectionsConfig(): Int {
        val resource = "/instance/$instanceId"
        val resInfo = ResInfo(resource, emptyList())
        val resourceLimit = (rateLimitRule as? ServiceInstanceConnectionRateLimitRule)
            ?.getRateLimitRule(resInfo)
            ?.resourceLimit
        return resourceLimit?.limit?.toInt() ?: Int.MAX_VALUE
    }

    /**
     * 获取连接使用率
     */
    fun getConnectionUsageRate(): Double {
        val current = getCurrentConnections()
        val max = getMaxConnectionsConfig()
        if (max == Int.MAX_VALUE) {
            return 0.0
        }
        return current.toDouble() / max
    }
}



