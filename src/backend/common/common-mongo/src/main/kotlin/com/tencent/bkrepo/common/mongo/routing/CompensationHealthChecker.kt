package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

/** M2 补偿健康探针；M6 HTTP 层委托本类。 */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class CompensationHealthChecker(
    private val compensationService: MongoDualWriteCompensationService,
) {

    fun check(ruleName: String): CompensationHealthStatus {
        val pendingCount = compensationService.countPendingTasks(ruleName)
        val oldestPending = compensationService.findOldestPending(ruleName)
        val oldestPendingAgeSeconds = oldestPending?.let { task ->
            val createdAt = task.getString("createdAt") ?: return@let null
            val created = java.time.LocalDateTime.parse(createdAt)
            java.time.Duration.between(created, java.time.LocalDateTime.now()).seconds
        } ?: 0L
        val healthy = pendingCount < MAX_HEALTHY_PENDING &&
            oldestPendingAgeSeconds < MAX_HEALTHY_AGE_SECONDS
        return CompensationHealthStatus(
            ruleName = ruleName,
            pendingCount = pendingCount,
            oldestPendingAgeSeconds = oldestPendingAgeSeconds,
            healthy = healthy,
        )
    }

    fun stats(ruleName: String): CompensationStats =
        CompensationStats(
            ruleName = ruleName,
            pendingCount = compensationService.countPendingTasks(ruleName),
            health = check(ruleName),
        )

    data class CompensationHealthStatus(
        val ruleName: String,
        val pendingCount: Long,
        val oldestPendingAgeSeconds: Long,
        val healthy: Boolean,
    )

    data class CompensationStats(
        val ruleName: String,
        val pendingCount: Long,
        val health: CompensationHealthStatus,
    )

    companion object {
        private const val MAX_HEALTHY_PENDING = 500L
        private const val MAX_HEALTHY_AGE_SECONDS = 1800L
    }
}
