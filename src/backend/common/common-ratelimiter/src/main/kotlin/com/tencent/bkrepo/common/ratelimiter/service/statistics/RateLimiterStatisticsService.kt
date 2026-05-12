package com.tencent.bkrepo.common.ratelimiter.service.statistics

import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_EXCEPTION_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_LIMITED_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_PASSED_COUNT
import com.tencent.bkrepo.common.metrics.constant.RATE_LIMITER_TOTAL_COUNT
import com.tencent.bkrepo.common.metrics.constant.TAG_DIMENSION
import com.tencent.bkrepo.common.metrics.constant.TAG_NAME
import com.tencent.bkrepo.common.ratelimiter.model.RateLimiterStatistics
import com.tencent.bkrepo.common.ratelimiter.model.ResourceRateLimiterStatistics
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.search.Search
import org.springframework.stereotype.Service

/**
 * 限流统计服务
 * 提供限流运营数据查询能力
 */
@Service
class RateLimiterStatisticsService(
    private val registry: MeterRegistry
) {

    /**
     * 获取整体限流统计数据
     */
    fun getOverallStatistics(dimension: String? = null): RateLimiterStatistics {
        val totalCounter = Search.`in`(registry)
            .name(RATE_LIMITER_TOTAL_COUNT)
            .apply { dimension?.let { tag(TAG_DIMENSION, it) } }
            .counters()

        val passedCounter = Search.`in`(registry)
            .name(RATE_LIMITER_PASSED_COUNT)
            .apply { dimension?.let { tag(TAG_DIMENSION, it) } }
            .counters()

        val limitedCounter = Search.`in`(registry)
            .name(RATE_LIMITER_LIMITED_COUNT)
            .apply { dimension?.let { tag(TAG_DIMENSION, it) } }
            .counters()

        val exceptionCounter = Search.`in`(registry)
            .name(RATE_LIMITER_EXCEPTION_COUNT)
            .apply { dimension?.let { tag(TAG_DIMENSION, it) } }
            .counters()

        val total = totalCounter.sumOf { it.count() }.toLong()
        val passed = passedCounter.sumOf { it.count() }.toLong()
        val limited = limitedCounter.sumOf { it.count() }.toLong()
        val exception = exceptionCounter.sumOf { it.count() }.toLong()

        val passRate = if (total > 0) (passed.toDouble() / total * 100) else 0.0
        val limitRate = if (total > 0) (limited.toDouble() / total * 100) else 0.0

        return RateLimiterStatistics(
            dimension = dimension ?: "ALL",
            totalRequests = total,
            passedRequests = passed,
            limitedRequests = limited,
            exceptionRequests = exception,
            passRate = passRate,
            limitRate = limitRate,
            ruleCount = 0, // 需要从规则配置中获取
        )
    }

    /**
     * 获取按资源分组的限流统计
     */
    fun getResourceStatistics(dimension: String? = null): List<ResourceRateLimiterStatistics> {
        val totalCounters = Search.`in`(registry)
            .name(RATE_LIMITER_TOTAL_COUNT)
            .apply { dimension?.let { tag(TAG_DIMENSION, it) } }
            .counters()

        return totalCounters.map { counter ->
            val resourceTag = counter.id.getTag(TAG_NAME) ?: "unknown"
            val dimTag = counter.id.getTag(TAG_DIMENSION) ?: "unknown"

            val passed = Search.`in`(registry)
                .name(RATE_LIMITER_PASSED_COUNT)
                .tag(TAG_NAME, resourceTag)
                .counters()
                .sumOf { it.count() }
                .toLong()

            val limited = Search.`in`(registry)
                .name(RATE_LIMITER_LIMITED_COUNT)
                .tag(TAG_NAME, resourceTag)
                .counters()
                .sumOf { it.count() }
                .toLong()

            val total = counter.count().toLong()
            val passRate = if (total > 0) (passed.toDouble() / total * 100) else 0.0

            ResourceRateLimiterStatistics(
                resource = resourceTag,
                dimension = dimTag,
                totalRequests = total,
                passedRequests = passed,
                limitedRequests = limited,
                passRate = passRate,
                configuredLimit = -1, // 需要从配置中获取
                duration = -1 // 需要从配置中获取
            )
        }
    }

    /**
     * 获取Top N 最频繁限流的资源
     */
    fun getTopLimitedResources(limit: Int = 10): List<ResourceRateLimiterStatistics> {
        return getResourceStatistics()
            .sortedByDescending { it.limitedRequests }
            .take(limit)
    }

    /**
     * 获取Top N 请求量最大的资源
     */
    fun getTopRequestedResources(limit: Int = 10): List<ResourceRateLimiterStatistics> {
        return getResourceStatistics()
            .sortedByDescending { it.totalRequests }
            .take(limit)
    }
}



