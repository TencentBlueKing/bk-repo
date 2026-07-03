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
            val created = task.createdAt() ?: return@let null
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

        /**
         * 从补偿任务读取 createdAt 为 LocalDateTime。
         * Spring Data Mongo 存 LocalDateTime 通常落为 BSON Date，读回为 java.util.Date；
         * 注册了 JSR-310 转换器时读回为 LocalDateTime。两种都处理。
         */
        private fun org.bson.Document.createdAt(): java.time.LocalDateTime? {
            return when (val raw = this["createdAt"]) {
                is java.time.LocalDateTime -> raw
                is java.util.Date -> java.time.LocalDateTime.ofInstant(
                    raw.toInstant(), java.time.ZoneId.systemDefault(),
                )
                else -> null
            }
        }
    }
}
