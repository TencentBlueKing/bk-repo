package com.tencent.bkrepo.common.metrics.constant

/**
 * 限流器监控指标常量
 */

// ==================== Tags（标签） ====================

const val TAG_STATUS = "status"
const val TAG_NAME = "name"
const val TAG_DIMENSION = "dimension"
const val TAG_ALGORITHM = "algorithm"
const val TAG_RESOURCE = "resource"
const val TAG_REASON = "reason"

// ==================== 基础限流指标 ====================

/**
 * 限流总请求数
 */
const val RATE_LIMITER_TOTAL_COUNT = "rate.limiter.total.count"
const val RATE_LIMITER_TOTAL_COUNT_DESC = "总请求数"

/**
 * 限流通过请求数
 */
const val RATE_LIMITER_PASSED_COUNT = "rate.limiter.passed.count"
const val RATE_LIMITER_PASSED_COUNT_DESC = "通过请求数"

/**
 * 限流拦截请求数
 */
const val RATE_LIMITER_LIMITED_COUNT = "rate.limiter.limited.count"
const val RATE_LIMITER_LIMITED_COUNT_DESC = "限流请求数"

/**
 * 限流异常请求数
 */
const val RATE_LIMITER_EXCEPTION_COUNT = "rate.limiter.exception.count"
const val RATE_LIMITER_EXCEPTION_COUNT_DESC = "异常请求数"

// ==================== 汇总级别指标 ====================

/**
 * 总体限流通过数
 */
const val RATE_LIMITER_OVERALL_PASS_COUNT = "rate.limiter.overall.pass.count"
const val RATE_LIMITER_OVERALL_PASS_COUNT_DESC = "总体限流通过数"

/**
 * 总体限流拒绝数
 */
const val RATE_LIMITER_OVERALL_LIMIT_COUNT = "rate.limiter.overall.limit.count"
const val RATE_LIMITER_OVERALL_LIMIT_COUNT_DESC = "总体限流拒绝数"

/**
 * 总体限流通过率
 */
const val RATE_LIMITER_OVERALL_PASS_RATE = "rate.limiter.overall.pass.rate"
const val RATE_LIMITER_OVERALL_PASS_RATE_DESC = "总体限流通过率"

// ==================== 维度级别指标 ====================

/**
 * 维度级别通过数
 */
const val RATE_LIMITER_DIMENSION_PASSED_COUNT = "rate.limiter.dimension.passed.count"
const val RATE_LIMITER_DIMENSION_PASSED_COUNT_DESC = "维度级别通过数"

/**
 * 维度级别限流数
 */
const val RATE_LIMITER_DIMENSION_LIMITED_COUNT = "rate.limiter.dimension.limited.count"
const val RATE_LIMITER_DIMENSION_LIMITED_COUNT_DESC = "维度级别限流数"

/**
 * 维度级别总请求数
 */
const val RATE_LIMITER_DIMENSION_TOTAL_COUNT = "rate.limiter.dimension.total.count"
const val RATE_LIMITER_DIMENSION_TOTAL_COUNT_DESC = "维度级别总请求数"

/**
 * 维度级别通过率
 */
const val RATE_LIMITER_DIMENSION_PASS_RATE = "rate.limiter.dimension.pass.rate"
const val RATE_LIMITER_DIMENSION_PASS_RATE_DESC = "维度级别通过率"

// ==================== 资源级别指标 ====================

/**
 * 资源级别通过数
 */
const val RATE_LIMITER_RESOURCE_PASSED_COUNT = "rate.limiter.resource.passed.count"
const val RATE_LIMITER_RESOURCE_PASSED_COUNT_DESC = "资源级别限流通过数"

/**
 * 资源级别拒绝数
 */
const val RATE_LIMITER_RESOURCE_LIMITED_COUNT = "rate.limiter.resource.limited.count"
const val RATE_LIMITER_RESOURCE_LIMITED_COUNT_DESC = "资源级别限流拒绝数"

/**
 * 资源级别通过率
 */
const val RATE_LIMITER_RESOURCE_PASS_RATE = "rate.limiter.resource.pass.rate"
const val RATE_LIMITER_RESOURCE_PASS_RATE_DESC = "资源级别限流通过率"

/**
 * 各维度限流资源数量
 */
const val RATE_LIMITER_RESOURCE_COUNT = "rate.limiter.resource.count"
const val RATE_LIMITER_RESOURCE_COUNT_DESC = "各维度限流资源数量"

// ==================== 运营指标 ====================

/**
 * 被拒绝的许可数分布
 */
const val RATE_LIMITER_REJECT_PERMITS = "rate.limiter.reject.permits"
const val RATE_LIMITER_REJECT_PERMITS_DESC = "被拒绝的许可数分布"

/**
 * 通过的许可数分布
 */
const val RATE_LIMITER_PASSED_PERMITS = "rate.limiter.passed.permits"
const val RATE_LIMITER_PASSED_PERMITS_DESC = "通过的许可数分布"

/**
 * 限流检查耗时
 */
const val RATE_LIMITER_CHECK_DURATION = "rate.limiter.check.duration"
const val RATE_LIMITER_CHECK_DURATION_DESC = "限流检查耗时"


/**
 * 限流降级次数
 */
const val RATE_LIMITER_DEGRADED_COUNT = "rate.limiter.degraded.count"
const val RATE_LIMITER_DEGRADED_COUNT_DESC = "限流降级次数"

// ==================== 连接数限制指标 ====================

/**
 * 连接被拒绝次数
 */
const val CONNECTION_REJECTED_COUNT = "service.instance.connections.rejected.count"
const val CONNECTION_REJECTED_COUNT_DESC = "连接被拒绝次数"

/**
 * 连接被接受次数
 */
const val CONNECTION_ACCEPTED_COUNT = "service.instance.connections.accepted.count"
const val CONNECTION_ACCEPTED_COUNT_DESC = "连接被接受次数"

/**
 * 连接处理时长
 */
const val CONNECTION_DURATION = "service.instance.connections.duration"
const val CONNECTION_DURATION_DESC = "连接处理时长"

/**
 * 当前活跃连接数
 */
const val CONNECTION_ACTIVE = "service.instance.connections.active"
const val CONNECTION_ACTIVE_DESC = "当前活跃连接数"

/**
 * 最大连接数配置
 */
const val CONNECTION_MAX = "service.instance.connections.max"
const val CONNECTION_MAX_DESC = "最大连接数配置"

/**
 * 连接使用率
 */
const val CONNECTION_USAGE = "service.instance.connections.usage"
const val CONNECTION_USAGE_DESC = "连接使用率"

