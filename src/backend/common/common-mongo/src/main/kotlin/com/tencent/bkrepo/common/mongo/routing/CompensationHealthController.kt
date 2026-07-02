package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 补偿队列健康检查 API（§25.2.4 E-02）。
 *
 * 暴露补偿队列的运行状态，供 Prometheus 采集和告警规则使用。
 * 返回 JSON 包含各规则的 PENDING 任务数、最老任务年龄、P99 消费延迟等。
 */
@RestController
@RequestMapping("/api/compensation")
@ConditionalOnBean(MongoRoutingRegistry::class)
class CompensationHealthController(
    private val healthChecker: CompensationHealthChecker,
) {

    @GetMapping("/health/{ruleName}")
    fun health(@PathVariable ruleName: String): CompensationHealthResponse {
        val status = healthChecker.check(ruleName)
        return CompensationHealthResponse(
            ruleName = status.ruleName,
            pendingCount = status.pendingCount,
            oldestPendingAgeSeconds = status.oldestPendingAgeSeconds,
            healthy = status.healthy,
        )
    }

    @GetMapping("/health")
    fun allHealth(): List<CompensationHealthResponse> =
        listOf("node", "artifact-oplog").map { health(it) }

    data class CompensationHealthResponse(
        val ruleName: String,
        val pendingCount: Long,
        val oldestPendingAgeSeconds: Long,
        val healthy: Boolean,
    )
}