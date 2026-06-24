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

data class InitValidationCheck(
    val name: String,
    val passed: Boolean,
    val reason: String? = null,
)

data class InitValidationResult(
    val passed: Boolean,
    val checks: List<InitValidationCheck>,
)

enum class DiskUsageLevel {
    NORMAL, WARN, BLOCK_WRITE, BLOCK_MIGRATION,
}
