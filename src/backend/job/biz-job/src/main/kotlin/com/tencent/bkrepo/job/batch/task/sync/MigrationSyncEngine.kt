package com.tencent.bkrepo.job.batch.task.sync

import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.model.TMigrationSyncState
import com.tencent.bkrepo.common.mongo.routing.MigrationPhaseMapping
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

class MigrationSyncEngine(
    private val defaultMongoTemplate: MongoTemplate,
    private val registry: MongoRoutingRegistry,
    private val syncStateDao: MigrationSyncStateDao?,
    private val strategies: Map<String, MigrationScanStrategy>,
) {

    internal val tasks = ConcurrentHashMap<String, MigrationSyncTask>()

    fun loadActiveTasks(): List<MigrationSyncTask> {
        val loadedKeys = mutableSetOf<String>()
        syncStateDao?.findByPhases(ACTIVE_PHASES)?.forEach { doc ->
            val strategy = strategies[doc.ruleName] ?: return@forEach
            val jobStateName = MigrationPhaseMapping.phaseToJobStateName(doc.phase) ?: return@forEach
            val jobState = runCatching { MigrationSyncJobState.valueOf(jobStateName) }.getOrNull()
                ?: return@forEach
            val key = taskKey(doc.ruleName, doc.projectId)
            tasks[key] = MigrationSyncTask(
                stateKey = stateKey(strategy, doc.projectId),
                projectId = doc.projectId,
                ruleName = doc.ruleName,
                targetInstance = doc.targetInstance,
                state = jobState,
                currentShardIdx = doc.currentShardIdx,
                lastSyncedId = doc.lastSyncedId,
                syncCycleCount = doc.syncCycleCount,
                lastError = doc.lastError,
                updatedAt = doc.updatedAt,
            )
            loadedKeys.add(key)
        }
        if (syncStateDao != null) {
            tasks.keys.removeAll { it !in loadedKeys }
        }
        return tasks.values.toList()
    }

    fun advance(task: MigrationSyncTask) {
        val strategy = strategies[task.ruleName] ?: return
        when (task.state) {
            MigrationSyncJobState.INITIAL_SYNC -> doInitialSync(task, strategy)
            MigrationSyncJobState.DUAL_WRITE -> logger.debug(
                "${task.ruleName}[${task.stateKey}] state=DUAL_WRITE; awaiting route API.",
            )
            MigrationSyncJobState.ROUTED -> logger.debug(
                "${task.ruleName}[${task.stateKey}] state=ROUTED; awaiting migration API orchestration.",
            )
            MigrationSyncJobState.CLEANUP_READY -> {
                if (strategy.supportsCleanup) {
                    doCleanup(task, strategy)
                } else {
                    logger.debug(
                        "${task.ruleName}[${task.stateKey}] state=CLEANUP_READY; " +
                            "cleanup handled by MongoMigrationService.",
                    )
                }
            }
            MigrationSyncJobState.CLEANED ->
                logger.debug("${task.ruleName}[${task.stateKey}] state=CLEANED; no action needed.")
            MigrationSyncJobState.INIT_FAILED ->
                logger.warn(
                    "${task.ruleName}[${task.stateKey}] state=INIT_FAILED; manual intervention required.",
                )
        }
    }

    fun updateState(task: MigrationSyncTask, state: MigrationSyncJobState, error: String? = null) {
        val key = taskKey(task.ruleName, task.projectId)
        val existing = tasks[key] ?: return
        tasks[key] = existing.copy(
            state = state,
            lastError = error,
            updatedAt = LocalDateTime.now(),
        )
        persist(key)
    }

    private fun doInitialSync(task: MigrationSyncTask, strategy: MigrationScanStrategy) {
        val targetTemplate = registry.primaryTemplateByInstance(task.ruleName, task.targetInstance)
            ?: run {
                updateState(task, MigrationSyncJobState.INIT_FAILED, "Instance template not found")
                return
            }

        val collections = strategy.shardCollections(task)
        if (collections.isEmpty()) {
            updateState(task, MigrationSyncJobState.INIT_FAILED, "No collections to scan")
            return
        }

        val ownerKey = taskKey(task.ruleName, task.projectId)
        var totalSynced = 0L

        for (shardIdx in task.currentShardIdx until collections.size) {
            val collectionName = collections[shardIdx]
            var pageLastId = if (shardIdx == task.currentShardIdx && task.lastSyncedId != null) {
                ObjectId(task.lastSyncedId)
            } else {
                ObjectId(MIN_OBJECT_ID)
            }
            do {
                val query = strategy.buildPageQuery(task, collectionName, pageLastId)
                    .limit(BATCH_SIZE)
                    .with(Sort.by(ID).ascending())
                val docs = defaultMongoTemplate.find(query, Document::class.java, collectionName)
                if (docs.isEmpty()) break

                docs.forEach { doc ->
                    val docId = doc["_id"] as ObjectId
                    insertIfAbsentWithRetry(
                        targetTemplate, docId, doc, collectionName, task, strategy,
                    )
                }

                pageLastId = docs.last()["_id"] as ObjectId
                totalSynced += docs.size
                updateProgress(ownerKey, shardIdx, pageLastId.toHexString())
            } while (docs.size == BATCH_SIZE)
            updateProgress(ownerKey, shardIdx + 1, null)
        }

        logger.info(
            "${task.ruleName}[${task.stateKey}] INITIAL_SYNC done (total=$totalSynced); " +
                "sync_failed=${hasSyncFailures(task, strategy)}.",
        )
        if (hasSyncFailures(task, strategy)) {
            val nextCycle = task.syncCycleCount + 1
            if (nextCycle >= MAX_SYNC_CYCLES) {
                logger.error(
                    "${task.ruleName}[${task.stateKey}] sync_failed persists after " +
                        "$MAX_SYNC_CYCLES cycles, degrading to INIT_FAILED.",
                )
                updateState(
                    task, MigrationSyncJobState.INIT_FAILED,
                    "sync_failed not cleared after $MAX_SYNC_CYCLES cycles",
                )
                return
            }
            logger.warn(
                "${task.ruleName}[${task.stateKey}] sync_failed not empty " +
                    "(cycle $nextCycle/$MAX_SYNC_CYCLES), will retry.",
            )
            updateCycleCount(ownerKey, nextCycle)
            return
        }
        updateState(task, MigrationSyncJobState.DUAL_WRITE)
    }

    private fun insertIfAbsentWithRetry(
        targetTemplate: MongoTemplate,
        docId: ObjectId,
        doc: Document,
        collectionName: String,
        task: MigrationSyncTask,
        strategy: MigrationScanStrategy,
    ) {
        val insertQuery = Query(Criteria.where(ID).`is`(docId))
        val update = Update().also { upd ->
            doc.entries.forEach { (k, v) -> if (k != "_id") upd.setOnInsert(k, v) }
        }
        var lastError: String? = null
        repeat(UPSERT_MAX_RETRY) { attempt ->
            runCatching { targetTemplate.upsert(insertQuery, update, collectionName) }
                .onSuccess { return }
                .onFailure { e ->
                    lastError = e.message
                    if (attempt < UPSERT_MAX_RETRY - 1) {
                        Thread.sleep(UPSERT_RETRY_DELAY_MS)
                    }
                }
        }
        recordSyncFailed(strategy, task, collectionName, docId.toHexString(), lastError)
    }

    private fun recordSyncFailed(
        strategy: MigrationScanStrategy,
        task: MigrationSyncTask,
        collectionName: String,
        docId: String,
        error: String?,
    ) {
        val ownerId = strategy.syncFailedOwnerId(task)
        logger.error(
            "INITIAL_SYNC upsert failed after $UPSERT_MAX_RETRY retries: " +
                "rule=${task.ruleName} owner=$ownerId col=$collectionName docId=$docId error=$error",
        )
        runCatching {
            val query = Query(Criteria.where("docId").`is`(docId))
            val update = Update()
                .setOnInsert("projectId", ownerId)
                .setOnInsert("collectionName", collectionName)
                .setOnInsert("docId", docId)
                .setOnInsert("createdAt", LocalDateTime.now().toString())
                .set("error", error)
            if (!strategy.supportsCleanup) {
                update.setOnInsert("ruleName", task.ruleName)
            }
            defaultMongoTemplate.upsert(query, update, strategy.syncFailedCollection)
        }.onFailure {
            logger.error("Failed to record sync failure for doc[$docId]: ${it.message}")
        }
    }

    private fun doCleanup(task: MigrationSyncTask, strategy: MigrationScanStrategy) {
        logger.info("${task.ruleName}[${task.stateKey}] starting CLEANUP: deleting from Default instance.")
        var totalDeleted = 0L
        val criteria = strategy.cleanupCriteria(task)

        for (collectionName in strategy.shardCollections(task)) {
            do {
                val ids = defaultMongoTemplate.find(
                    Query(criteria).limit(CLEANUP_BATCH_SIZE),
                    Document::class.java,
                    collectionName,
                ).map { it["_id"] }
                if (ids.isEmpty()) break

                val result = defaultMongoTemplate.remove(
                    Query(Criteria.where(ID).`in`(ids)),
                    collectionName,
                )
                totalDeleted += result.deletedCount
                Thread.sleep(CLEANUP_SLEEP_MS)
            } while (ids.size == CLEANUP_BATCH_SIZE)
        }

        logger.info(
            "${task.ruleName}[${task.stateKey}] CLEANUP completed (total deleted=$totalDeleted) → CLEANED.",
        )
        updateState(task, MigrationSyncJobState.CLEANED)
    }

    private fun updateProgress(ownerKey: String, currentShardIdx: Int, lastSyncedId: String?) {
        val existing = tasks[ownerKey] ?: return
        tasks[ownerKey] = existing.copy(
            currentShardIdx = currentShardIdx,
            lastSyncedId = lastSyncedId,
            updatedAt = LocalDateTime.now(),
        )
        persist(ownerKey)
    }

    private fun updateCycleCount(ownerKey: String, count: Int) {
        val existing = tasks[ownerKey] ?: return
        tasks[ownerKey] = existing.copy(syncCycleCount = count, updatedAt = LocalDateTime.now())
        persist(ownerKey)
    }

    private fun persist(ownerKey: String) {
        val repo = syncStateDao ?: return
        val doc = tasks[ownerKey] ?: return
        val id = if (doc.projectId == doc.ruleName) doc.ruleName else doc.projectId
        repo.upsert(
            TMigrationSyncState(
                id = id,
                projectId = doc.projectId,
                ruleName = doc.ruleName,
                targetInstance = doc.targetInstance,
                phase = MigrationPhaseMapping.jobStateNameToPhase(doc.state.name),
                currentShardIdx = doc.currentShardIdx,
                lastSyncedId = doc.lastSyncedId,
                syncCycleCount = doc.syncCycleCount,
                lastError = doc.lastError,
                updatedAt = doc.updatedAt,
            ),
        )
    }

    private fun hasSyncFailures(task: MigrationSyncTask, strategy: MigrationScanStrategy): Boolean =
        defaultMongoTemplate.count(
            Query(Criteria.where("projectId").`is`(strategy.syncFailedOwnerId(task))),
            strategy.syncFailedCollection,
        ) > 0

    private fun stateKey(strategy: MigrationScanStrategy, projectId: String): String =
        if (strategy.supportsCleanup) projectId else strategy.ruleName

    private fun taskKey(ruleName: String, projectId: String): String = "$ruleName:$projectId"

    companion object {
        private val logger = LoggerFactory.getLogger(MigrationSyncEngine::class.java)
        private val ACTIVE_PHASES = setOf(MigrationPhase.INITIAL_SYNC, MigrationPhase.CLEANUP_READY)
        private const val BATCH_SIZE = 500
        private const val CLEANUP_BATCH_SIZE = 500
        private const val CLEANUP_SLEEP_MS = 100L
        private const val UPSERT_MAX_RETRY = 3
        private const val UPSERT_RETRY_DELAY_MS = 200L
        // sync_failed 最大重试轮数，超出后降级 INIT_FAILED 而非死循环
        private const val MAX_SYNC_CYCLES = 3
    }
}
