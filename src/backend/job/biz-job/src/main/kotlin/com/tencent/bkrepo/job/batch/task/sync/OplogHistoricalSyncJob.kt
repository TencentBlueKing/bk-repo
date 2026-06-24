package com.tencent.bkrepo.job.batch.task.sync

import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.routing.MigrationSyncStateRepository
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * oplog 历史数据同步（M6）；`historicalSyncStrategy != NONE` 时启用。
 * ponytail: 全量 upsert 扫描，无 Change Stream 追增量。
 */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class OplogHistoricalSyncJob(
    private val defaultMongoTemplate: MongoTemplate,
    private val registry: MongoRoutingRegistry,
    private val syncStateRepository: MigrationSyncStateRepository,
    @Autowired(required = false)
    private val properties: MongoMultiInstanceProperties? = null,
) {

    @Scheduled(fixedDelay = SCHEDULE_INTERVAL_MS)
    fun run() {
        val ruleName = findOplogRuleName() ?: return
        val strategy = properties?.rules?.get(ruleName)?.migration?.historicalSyncStrategy ?: "NONE"
        if (strategy.equals("NONE", ignoreCase = true)) return
        if (!registry.isRoutingEnabled(ruleName)) return

        val targetInstance = registry.allPrimaryTemplates(ruleName).keys.firstOrNull() ?: return
        val targetTemplate = registry.primaryTemplateByInstance(ruleName, targetInstance) ?: return
        val state = syncStateRepository.findByProjectId(ruleName) ?: run {
            syncStateRepository.upsert(
                MigrationSyncStateRepository.MigrationSyncStateDoc(
                    id = ruleName,
                    projectId = ruleName,
                    ruleName = ruleName,
                    targetInstance = targetInstance,
                    phase = MigrationPhase.JOB_FULL,
                ),
            )
            syncStateRepository.findByProjectId(ruleName)!!
        }

        if (state.phase == MigrationPhase.READY ||
            state.phase == MigrationPhase.DUAL_WRITE ||
            state.phase == MigrationPhase.ROUTED ||
            state.phase == MigrationPhase.CLEANED
        ) {
            return
        }

        val collections = defaultMongoTemplate.db.listCollectionNames()
            .asSequence()
            .filter { it.startsWith(OPLOG_PREFIX) }
            .sorted()
            .toList()
        if (collections.isEmpty()) return

        val startIdx = state.lastSyncedId?.let { collections.indexOf(it).coerceAtLeast(0) } ?: 0
        for (col in collections.drop(startIdx)) {
            syncCollection(col, targetTemplate)
            syncStateRepository.upsert(
                state.copy(
                    lastSyncedId = col,
                    phase = MigrationPhase.JOB_FULL,
                    updatedAt = LocalDateTime.now(),
                ),
            )
        }
        syncStateRepository.updatePhase(ruleName, MigrationPhase.VERIFY)
        logger.info("OplogHistoricalSyncJob: full sync done for rule[$ruleName] → VERIFY")
    }

    private fun syncCollection(collectionName: String, targetTemplate: MongoTemplate) {
        var pageLastId = ObjectId("000000000000000000000000")
        var batchSize: Int
        do {
            val query = Query(Criteria.where(ID).gt(pageLastId))
                .limit(BATCH_SIZE)
                .with(Sort.by(ID).ascending())
            val docs = defaultMongoTemplate.find(query, Document::class.java, collectionName)
            batchSize = docs.size
            if (docs.isEmpty()) break
            docs.forEach { doc ->
                val docId = doc["_id"] as ObjectId
                val upsertQuery = Query(Criteria.where(ID).`is`(docId))
                val update = Update().also { upd ->
                    doc.entries.forEach { (k, v) -> if (k != "_id") upd.set(k, v) }
                }
                targetTemplate.upsert(upsertQuery, update, collectionName)
            }
            pageLastId = docs.last()["_id"] as ObjectId
        } while (batchSize == BATCH_SIZE)
    }

    private fun findOplogRuleName(): String? =
        properties?.rules?.entries?.firstOrNull {
            it.value.collectionPrefix.startsWith(OPLOG_PREFIX)
        }?.key

    companion object {
        private val logger = LoggerFactory.getLogger(OplogHistoricalSyncJob::class.java)
        private const val OPLOG_PREFIX = "artifact_oplog_"
        private const val BATCH_SIZE = 500
        private const val SCHEDULE_INTERVAL_MS = 10 * 60 * 1000L
    }
}
