package com.tencent.bkrepo.job.batch.task.sync

import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.MigrationSyncStateRepository
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 模式一 oplog ROUTED 后清理 Default 上的 `artifact_oplog_*` 月集合（M6 CLEANUP）。
 */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class OplogDefaultCleanupJob(
    private val defaultMongoTemplate: MongoTemplate,
    private val registry: MongoRoutingRegistry,
    private val syncStateRepository: MigrationSyncStateRepository,
    @Autowired(required = false)
    private val properties: MongoMultiInstanceProperties? = null,
) {

    @Scheduled(fixedDelay = SCHEDULE_INTERVAL_MS)
    fun cleanup() {
        val ruleName = findOplogRuleName() ?: return
        val state = syncStateRepository.findByProjectId(ruleName) ?: return
        if (state.phase != MigrationPhase.ROUTED && state.phase != MigrationPhase.CLEANED) return
        if (!registry.isRoutingEnabled(ruleName)) return

        val collections = defaultMongoTemplate.db.listCollectionNames()
            .asSequence()
            .filter { it.startsWith(OPLOG_PREFIX) }
            .toList()
        if (collections.isEmpty()) return

        collections.forEach { col ->
            defaultMongoTemplate.dropCollection(col)
            logger.info("OplogDefaultCleanupJob dropped Default collection [{}]", col)
        }
        if (state.phase == MigrationPhase.ROUTED) {
            syncStateRepository.updatePhase(ruleName, MigrationPhase.CLEANED)
        }
    }

    private fun findOplogRuleName(): String? =
        properties?.rules?.entries?.firstOrNull {
            it.value.collectionPrefix.startsWith(OPLOG_PREFIX)
        }?.key

    companion object {
        private const val OPLOG_PREFIX = "artifact_oplog_"
        private const val SCHEDULE_INTERVAL_MS = 300_000L
        private val logger = LoggerFactory.getLogger(OplogDefaultCleanupJob::class.java)
    }
}
