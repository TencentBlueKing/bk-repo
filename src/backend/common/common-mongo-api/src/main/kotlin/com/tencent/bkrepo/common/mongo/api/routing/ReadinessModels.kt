package com.tencent.bkrepo.common.mongo.api.routing

data class ReadinessCheckItem(
    val id: String,
    val description: String,
    val passed: Boolean,
    val detail: String? = null,
)

data class RoutingReadinessResult(
    val ready: Boolean,
    val checks: List<ReadinessCheckItem>,
)

interface RoutingReadinessChecker {
    fun check(): RoutingReadinessResult
}

/**
 * 各服务实现的本地就绪探针接口。
 * 在 [common-mongo] 中由统一的 [RoutingReadinessController]（如存在）自动收集所有 bean 暴露为
 * GET /routing/readiness 端点。
 *
 * 每个服务只需注册一个或多个实现此接口的 @Component，无需重复编写 Controller。
 */
interface RoutingReadinessProbe {
    /** 探针标识，如 "auth-A1", "job-B" 等 */
    fun probeId(): String

    /** 返回本探针负责的全部检查项 */
    fun checkAll(): List<ReadinessCheckItem>
}

data class InitValidationCheck(
    val name: String,
    val passed: Boolean,
    val reason: String? = null,
)

data class InitValidationResult(
    val passed: Boolean,
    val checks: List<InitValidationCheck>,
)
