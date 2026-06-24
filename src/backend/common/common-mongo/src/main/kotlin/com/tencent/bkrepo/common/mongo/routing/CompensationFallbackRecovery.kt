package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Path

/**
 * 补偿回退文件恢复器（§25.2.3 E-15）。
 *
 * 定期扫描本地回退目录，将回退文件中的补偿任务重新入队到 MongoDB，
 * 成功入队后删除本地文件，实现最终一致性。
 */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class CompensationFallbackRecovery(
    private val writer: CompensationFallbackWriter,
    private val mongoTemplate: MongoTemplate,
) {

    @Scheduled(fixedDelay = RECOVERY_INTERVAL_MS)
    fun recover() {
        val files = writer.list()
        if (files.isEmpty()) return

        logger.info("CompensationFallbackRecovery: scanning {} files", files.size)
        var recovered = 0
        var failed = 0

        files.forEach { path ->
            val snapshot = writer.readAndDelete(path)
            if (snapshot == null) {
                failed++
                return@forEach
            }
            try {
                enqueueToMongo(snapshot)
                logger.info("CompensationFallbackRecovery: restored {}", path.fileName)
                recovered++
            } catch (e: Exception) {
                logger.error("CompensationFallbackRecovery: failed to restore {}: {}", path.fileName, e.message)
                failed++
            }
        }

        if (recovered > 0 || failed > 0) {
            logger.info("CompensationFallbackRecovery: recovered={} failed={}", recovered, failed)
        }
    }

    private fun enqueueToMongo(snapshot: CompensationTaskSnapshot) {
        val doc = org.bson.Document().apply {
            put("ruleName", snapshot.ruleName)
            put("routingKey", snapshot.routingKey)
            put("collectionName", snapshot.collectionName)
            put("operationType", snapshot.operationType)
            put("targetUseDefault", snapshot.targetUseDefault)
            put("targetInstance", snapshot.targetInstance)
            put("entityClass", snapshot.entityClass)
            put("entity", snapshot.entityDocument)
            put("query", snapshot.queryDocument)
            put("update", snapshot.updateDocument)
            put("options", snapshot.optionsDocument)
            put("retryCount", 0)
            put("status", "PENDING")
            put("createdAt", snapshot.createdAt)
            put("updatedAt", java.time.LocalDateTime.now().toString())
            put("source", "fallback_recovery")
        }
        mongoTemplate.insert(doc, MongoDualWriteCompensationService.COLLECTION_NAME)
    }

    companion object {
        private const val RECOVERY_INTERVAL_MS = 60_000L
        private val logger = LoggerFactory.getLogger(CompensationFallbackRecovery::class.java)
    }
}