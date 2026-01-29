package com.tencent.bkrepo.common.ratelimiter.model

/**
 * 限流统计数据响应
 */
data class RateLimiterStatistics(
    // 限流维度
    val dimension: String,
    // 总请求数
    val totalRequests: Long,
    // 通过请求数
    val passedRequests: Long,
    // 限流请求数
    val limitedRequests: Long,
    // 异常请求数
    val exceptionRequests: Long,
    // 通过率（百分比）
    val passRate: Double,
    // 限流率（百分比）
    val limitRate: Double,
    // 当前限流规则数量
    val ruleCount: Int,
)

/**
 * 按资源分组的限流统计
 */
data class ResourceRateLimiterStatistics(
    // 资源标识
    val resource: String,
    // 限流维度
    val dimension: String,
    // 总请求数
    val totalRequests: Long,
    // 通过请求数
    val passedRequests: Long,
    // 限流请求数
    val limitedRequests: Long,
    // 通过率（百分比）
    val passRate: Double,
    // 配置的限流阈值
    val configuredLimit: Long,
    // 限流周期（秒）
    val duration: Long
)

/**
 * 限流算法使用统计
 */
data class AlgorithmUsageStatistics(
    // 算法类型
    val algorithm: String,
    // 使用次数
    val usageCount: Long,
    // 使用占比（百分比）
    val usagePercentage: Double
)




