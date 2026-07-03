package com.tencent.bkrepo.job.batch.task.sync

import com.mongodb.MongoException
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.changestream.FullDocument
import com.mongodb.client.model.changestream.OperationType
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.mongo.routing.MigrationPhaseMapping
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.model.TMigrationSyncState
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.NodeReconciliationHelper
import com.tencent.bkrepo.common.lock.service.LockOperation
import com.tencent.bkrepo.job.batch.base.BaseService
import org.bson.BsonDocument
import org.bson.BsonTimestamp
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 按项目将 node_* 数据从 Default 实例迁移到 Heavy 实例的 Job。
 *
 * 状态机流转：
 *   INIT → INITIAL_SYNC → CATCH_UP → VERIFY → READY
 *   READY → (运维配置路由) → DUAL_WRITE → ROUTED → CLEANUP_READY → CLEANED
 *   任意阶段失败 → INIT_FAILED / REBUILD_REQUIRED
 *
 * 无路由配置时 Job 的 run() 立即返回。
 */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class NodeProjectSyncJob(
    @Qualifier("mongoTemplate")
    private val defaultMongoTemplate: MongoTemplate,
    private val registry: MongoRoutingRegistry,
    private val syncStateDao: MigrationSyncStateDao? = null,
    redisTemplate: RedisTemplate<String, String>,
    lockOperation: LockOperation,
) : BaseService(redisTemplate, lockOperation) {

    // ---------------------------------------------------------------
    // 领域模型
    // ---------------------------------------------------------------

    enum class SyncState {
        INIT,
        INIT_FAILED,
        DBA_DUMPING,
        JOB_GAP,
        INITIAL_SYNC,
        CATCH_UP,
        VERIFY,
        REBUILD_REQUIRED,
        READY,
        DUAL_WRITE,
        ROUTED,
        CLEANUP_READY,
        CLEANED,
    }

    data class SyncStateDoc(
        val id: String,
        val projectId: String,
        val targetInstance: String,
        val state: SyncState,
        /**
         * 断点续传：当前正在处理的分片索引。
         * 该分片之前（0 until currentShardIdx）的所有分片已全量同步完毕。
         * [lastSyncedId] 为该分片内最后一个已成功 upsert 的文档 _id。
         */
        val currentShardIdx: Int = 0,
        /** 当前分片（[currentShardIdx]）内最后已处理的文档 _id，null 表示从头开始 */
        val lastSyncedId: String?,
        val lastError: String?,
        val updatedAt: LocalDateTime,
        /** Change Stream 断点 token（JSON 字符串），CATCH_UP 阶段持久化 */
        val resumeToken: String?,
        /** INITIAL_SYNC 开始前的服务端时间戳（Unix 秒），CATCH_UP 起始位置 */
        val scanStartTimestamp: Long?,
        /** CATCH_UP 最后处理事件的 clusterTime（秒），VERIFY 门禁 lag 校验 */
        val lastEventClusterTimeSecs: Long? = null,
        /** DBA_DUMP 模式：运维完成 dump/restore 后置 true */
        val dbaDumpCompleted: Boolean = false,
    )

    // ---------------------------------------------------------------
    // CATCH_UP 后台线程管理：防止同一项目并发启动多个 Change Stream
    // ---------------------------------------------------------------
    private val catchUpThreads = ConcurrentHashMap<String, Thread>()

    // ---------------------------------------------------------------
    // 定时调度
    // ---------------------------------------------------------------

    @Scheduled(fixedDelayString = "\${bkrepo.mongo.routing.node-sync.refresh-ms:300000}")
    fun run() {
        loadAllSyncStates().forEach { state ->
            refreshData("node-sync-${state.projectId}") {
                try {
                    advance(state)
                } catch (e: Exception) {
                    logger.error(
                        "NodeProjectSyncJob failed for project[${state.projectId}]: ${e.message}", e
                    )
                    updateState(state.projectId, SyncState.INIT_FAILED, error = e.message)
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // 状态机推进
    // ---------------------------------------------------------------

    private fun advance(state: SyncStateDoc) {
        when (state.state) {
            SyncState.INIT -> doInit(state)
            SyncState.DBA_DUMPING -> doDbaDumpWait(state)
            SyncState.JOB_GAP -> doInitialSync(state, gapFillOnly = true)
            SyncState.INITIAL_SYNC -> doInitialSync(state, gapFillOnly = false)
            SyncState.CATCH_UP -> {
                if (catchUpThreads[state.projectId]?.isAlive == true) {
                    logger.debug("Project[${state.projectId}] CATCH_UP thread already running.")
                } else {
                    startCatchUpThread(state)
                }
            }
            SyncState.VERIFY -> doVerify(state)
            SyncState.READY -> logger.debug(
                "Project[${state.projectId}] state=${state.state}; waiting for migration API orchestration."
            )
            SyncState.DUAL_WRITE -> {
                stopCatchUp(state.projectId)
                logger.debug(
                    "Project[${state.projectId}] state=DUAL_WRITE; CATCH_UP stopped, awaiting route API."
                )
            }
            SyncState.ROUTED -> logger.debug(
                "Project[${state.projectId}] state=${state.state}; waiting for migration API orchestration."
            )
            SyncState.CLEANUP_READY -> doCleanup(state)
            SyncState.CLEANED ->
                logger.debug("Project[${state.projectId}] state=${state.state}; no action needed.")
            SyncState.INIT_FAILED, SyncState.REBUILD_REQUIRED ->
                logger.warn(
                    "Project[${state.projectId}] state=${state.state}; manual intervention required."
                )
        }
    }

    // ---------------------------------------------------------------
    // INIT：连通性 + 索引校验
    // ---------------------------------------------------------------

    private fun doInit(state: SyncStateDoc) {
        val targetTemplate = registry.primaryTemplateByInstance(NODE_RULE, state.targetInstance)
            ?: run {
                updateState(state.projectId, SyncState.INIT_FAILED, "Instance template not found")
                return
            }
        try {
            targetTemplate.db.runCommand(Document("ping", 1))
        } catch (e: Exception) {
            updateState(
                state.projectId, SyncState.INIT_FAILED,
                "Instance[${state.targetInstance}] not reachable: ${e.message}"
            )
            return
        }

        // §25.3.4 / §20a: writeConcern majority 和副本集健康校验
        if (!verifyWriteConcern(state.projectId, targetTemplate)) return

        if (!verifyIndexConsistency(targetTemplate)) {
            updateState(
                state.projectId, SyncState.INIT_FAILED,
                "Index inconsistency detected; create missing indexes on target first"
            )
            return
        }
        logger.info("Project[${state.projectId}] INIT checks passed; strategy=${syncStrategy()}.")
        if (!captureChangeStreamCheckpoint(state)) return
        when (syncStrategy()) {
            STRATEGY_DUMP, STRATEGY_DUMP_THEN_JOB -> {
                logDumpInstructions(state.projectId)
                updateState(state.projectId, SyncState.DBA_DUMPING)
            }
            else -> updateState(state.projectId, SyncState.INITIAL_SYNC)
        }
    }

    /**
     * §1.6.2 ① CS_START：在 DUMP / JOB 扫数前捕获 Change Stream resumeToken。
     */
    private fun captureChangeStreamCheckpoint(state: SyncStateDoc): Boolean {
        val current = syncStates[state.projectId] ?: state
        if (current.resumeToken != null && current.scanStartTimestamp != null) {
            return true
        }
        val scanStartTs = System.currentTimeMillis() / 1000L
        val pipeline = buildChangeStreamPipeline(state.projectId)
        return try {
            defaultMongoTemplate.db.watch(pipeline)
                .fullDocument(FullDocument.UPDATE_LOOKUP)
                .maxAwaitTime(CHANGE_STREAM_MAX_AWAIT_MS, TimeUnit.MILLISECONDS)
                .cursor().use { cursor ->
                    cursor.tryNext()
                    cursor.resumeToken?.let { updateResumeToken(state.projectId, it.toJson()) }
                }
            updateScanStartTimestamp(state.projectId, scanStartTs)
            logger.info(
                "Project[${state.projectId}] CS_START checkpoint captured at ts=$scanStartTs",
            )
            true
        } catch (e: Exception) {
            logger.error("Project[${state.projectId}] CS_START failed: ${e.message}", e)
            updateState(state.projectId, SyncState.INIT_FAILED, "CS_START failed: ${e.message}")
            false
        }
    }

    private fun logDumpInstructions(projectId: String) {
        logger.info(
            "Project[$projectId] awaiting DBA dump/restore. Per shard: " +
                "mongodump --query='{{\"projectId\":\"$projectId\"}}' -c node_<N> ... ; " +
                "then POST /api/migration/dump-complete",
        )
    }

    /** DBA_DUMP / DUMP_THEN_JOB：等待运维完成 mongodump/restore 并调用 dump-complete API。 */
    private fun doDbaDumpWait(state: SyncStateDoc) {
        if (!state.dbaDumpCompleted) {
            logger.debug(
                "Project[${state.projectId}] DBA_DUMPING: waiting for dump-complete API.",
            )
            return
        }
        when (syncStrategy()) {
            STRATEGY_DUMP -> {
                logger.info("Project[${state.projectId}] dump complete → CATCH_UP.")
                updateState(state.projectId, SyncState.CATCH_UP)
            }
            STRATEGY_DUMP_THEN_JOB -> {
                logger.info("Project[${state.projectId}] dump complete → JOB_GAP.")
                updateState(state.projectId, SyncState.JOB_GAP)
            }
            else -> updateState(state.projectId, SyncState.VERIFY)
        }
    }

    /**
     * 校验目标实例的 writeConcern 和副本集配置（§20a / §25.3.4）。
     *
     * 检查项：
     * - 副本集 ≥ 3 个健康节点
     * - writeConcern: majority 可达（通过 probe insert + read 验证）
     * - Default 和 Heavy 大版本一致
     *
     * 任一检查不通过则设置 INIT_FAILED 并返回 false。
     */
    private fun verifyWriteConcern(projectId: String, targetTemplate: MongoTemplate): Boolean {
        // 1. 检查 Heavy 副本集健康状态
        try {
            val rsStatus = targetTemplate.db.runCommand(Document("replSetGetStatus", 1))
            val members = rsStatus.getList("members", Document::class.java) ?: emptyList<Document>()
            val healthyMembers = members.filter {
                it.getInteger("health") == 1 && listOf("PRIMARY", "SECONDARY").contains(it.getString("stateStr"))
            }
            if (healthyMembers.size < 3) {
                logger.error(
                    "Project[$projectId] Heavy replica set has only {} healthy members (need ≥ 3): {}",
                    healthyMembers.size,
                    members.map { "${it.getString("name")}:${it.getString("stateStr")}" }
                )
                updateState(projectId, SyncState.INIT_FAILED,
                    "Heavy replica set has ${healthyMembers.size} healthy members (need ≥ 3)")
                return false
            }
            logger.info("Project[$projectId] Heavy replica set healthy: {} members", healthyMembers.size)
        } catch (e: Exception) {
            // 单节点 MongoDB（开发环境）没有 replSetGetStatus，跳过检查
            if (e.message?.contains("not running with --replSet", ignoreCase = true) == true ||
                e.message?.contains("no such command", ignoreCase = true) == true) {
                logger.warn("Project[$projectId] target is standalone MongoDB, skipping replSet check.")
            } else {
                logger.error("Project[$projectId] replSetGetStatus failed: {}", e.message, e)
            }
        }

        // 2. probe insert + read 验证 writeConcern: majority 可达
        val probeCollection = "write_concern_probe"
        val probeId = "${projectId}_${System.currentTimeMillis()}"
        try {
            targetTemplate.insert(Document("_id", probeId).append("ts", System.currentTimeMillis()), probeCollection)
            val probeDoc = targetTemplate.findById(probeId, Document::class.java, probeCollection)
            if (probeDoc == null) {
                logger.error("Project[$projectId] writeConcern probe: inserted doc not readable")
                updateState(projectId, SyncState.INIT_FAILED,
                    "writeConcern majority probe failed: document inserted but not readable")
                return false
            }
            targetTemplate.remove(Document("_id", probeId), probeCollection)
            logger.info("Project[$projectId] writeConcern probe passed.")
        } catch (e: Exception) {
            logger.error("Project[$projectId] writeConcern probe failed: {}", e.message, e)
            updateState(projectId, SyncState.INIT_FAILED,
                "writeConcern majority probe failed: ${e.message}")
            runCatching { targetTemplate.remove(Document("_id", probeId), probeCollection) }
            return false
        }

        // 3. MongoDB 版本一致性检查（推荐 6.0+）
        try {
            val defaultVersion = defaultMongoTemplate.db.runCommand(Document("buildInfo", 1))
                .getString("version") ?: "unknown"
            val heavyVersion = targetTemplate.db.runCommand(Document("buildInfo", 1))
                .getString("version") ?: "unknown"
            val defaultMajor = defaultVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
            val heavyMajor = heavyVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
            if (defaultMajor < 4 || heavyMajor < 4) {
                logger.error(
                    "Project[$projectId] MongoDB version too old: " +
                        "Default=$defaultVersion, Heavy=$heavyVersion (need ≥ 4.4)"
                )
                updateState(projectId, SyncState.INIT_FAILED,
                    "MongoDB version too old: Default=$defaultVersion, Heavy=$heavyVersion")
                return false
            }
            if (defaultMajor != heavyMajor) {
                logger.warn(
                    "Project[$projectId] MongoDB version mismatch: Default=$defaultVersion, Heavy=$heavyVersion. " +
                        "Behavior differences may occur."
                )
                // 版本不一致不阻断 INIT，仅告警
            }
            logger.info("Project[$projectId] MongoDB version: Default=$defaultVersion, Heavy=$heavyVersion")
        } catch (e: Exception) {
            logger.warn("Project[$projectId] version check failed (non-critical): {}", e.message)
        }

        return true
    }

    // ---------------------------------------------------------------
    // INITIAL_SYNC：全量扫 Default 从库，upsert 写入 Heavy 主库
    // ---------------------------------------------------------------

    private fun doInitialSync(state: SyncStateDoc, gapFillOnly: Boolean) {
        val targetTemplate = registry.primaryTemplateByInstance(NODE_RULE, state.targetInstance)
            ?: run {
                updateState(state.projectId, SyncState.INIT_FAILED, "Instance template not found")
                return
            }
        // 在首次扫描前记录服务端时间戳作为 CATCH_UP 起始位置（防止遗漏增量）
        // 重试时不覆盖已有时间戳
        val scanStartTs = state.scanStartTimestamp ?: (System.currentTimeMillis() / 1000L)
        if (state.scanStartTimestamp == null) updateScanStartTimestamp(state.projectId, scanStartTs)

        val projectId = state.projectId
        var totalSynced = 0L
        val modifiedSince = if (gapFillOnly && state.scanStartTimestamp != null) {
            java.util.Date(state.scanStartTimestamp * 1000L)
        } else {
            null
        }

        // 断点续传：跳过已完整同步的分片，从 currentShardIdx 继续
        for (shardIdx in state.currentShardIdx until SHARDING_COUNT) {
            val collectionName = "$NODE_COLLECTION_PREFIX$shardIdx"
            // 只有当前断点分片使用上次保存的 lastSyncedId；后续新分片从 MIN_OBJECT_ID 开始
            var pageLastId = if (shardIdx == state.currentShardIdx && state.lastSyncedId != null) {
                ObjectId(state.lastSyncedId)
            } else {
                ObjectId(MIN_OBJECT_ID)
            }
            do {
                var criteria = Criteria.where("projectId").`is`(projectId).and(ID).gt(pageLastId)
                if (modifiedSince != null) {
                    criteria = criteria.and("lastModifiedDate").gte(modifiedSince)
                }
                val query = Query(criteria)
                    .limit(BATCH_SIZE).with(Sort.by(ID).ascending())
                val docs = defaultMongoTemplate.find(query, Document::class.java, collectionName)
                if (docs.isEmpty()) break

                docs.forEach { doc ->
                    val docId = doc["_id"] as ObjectId
                    upsertWithRetry(targetTemplate, docId, doc, collectionName, projectId)
                }

                pageLastId = docs.last()["_id"] as ObjectId
                totalSynced += docs.size
                // 记录当前分片和位置，支持断点续传
                updateProgress(projectId, shardIdx, pageLastId.toHexString())
            } while (docs.size == BATCH_SIZE)

            // 当前分片扫描完成，推进 currentShardIdx 到下一分片并清除 lastSyncedId
            updateProgress(projectId, shardIdx + 1, null)
        }

        logger.info(
            "Project[$projectId] ${if (gapFillOnly) "JOB_GAP" else "JOB_FULL"} done " +
                "(total=$totalSynced); moving to CATCH_UP.",
        )
        updateState(projectId, SyncState.CATCH_UP)
    }

    /**
     * 带重试的 upsert：最多 [UPSERT_MAX_RETRY] 次，超限后写入 [SYNC_FAILED_COLLECTION]。
     */
    private fun upsertWithRetry(
        targetTemplate: MongoTemplate,
        docId: ObjectId,
        doc: Document,
        collectionName: String,
        projectId: String,
    ) {
        val upsertQuery = Query(Criteria.where(ID).`is`(docId))
        val update = Update().also { upd ->
            doc.entries.forEach { (k, v) -> if (k != "_id") upd.set(k, v) }
        }
        var lastError: String? = null
        repeat(UPSERT_MAX_RETRY) { attempt ->
            runCatching { targetTemplate.upsert(upsertQuery, update, collectionName) }
                .onSuccess { return }
                .onFailure { e ->
                    lastError = e.message
                    if (attempt < UPSERT_MAX_RETRY - 1) {
                        Thread.sleep(UPSERT_RETRY_DELAY_MS)
                    }
                }
        }
        // 重试耗尽，写入 sync_failed 表供人工排查
        recordSyncFailed(projectId, collectionName, docId.toHexString(), lastError)
    }

    private fun recordSyncFailed(
        projectId: String,
        collectionName: String,
        docId: String,
        error: String?,
    ) {
        logger.error(
            "INITIAL_SYNC upsert failed after $UPSERT_MAX_RETRY retries: " +
                "project=$projectId col=$collectionName docId=$docId error=$error"
        )
        runCatching {
            defaultMongoTemplate.insert(
                Document().apply {
                    put("projectId", projectId)
                    put("collectionName", collectionName)
                    put("docId", docId)
                    put("error", error)
                    put("createdAt", LocalDateTime.now().toString())
                },
                SYNC_FAILED_COLLECTION,
            )
        }.onFailure {
            logger.error("Failed to record sync failure for doc[$docId]: ${it.message}")
        }
    }

    // ---------------------------------------------------------------
    // CATCH_UP：Change Stream 追增量，追上后转 VERIFY
    // ---------------------------------------------------------------

    private fun startCatchUpThread(state: SyncStateDoc) {
        val thread = Thread(
            { processCatchUp(state) },
            "node-catchup-${state.projectId}"
        ).apply { isDaemon = true }
        catchUpThreads[state.projectId] = thread
        thread.start()
    }

    private fun stopCatchUp(projectId: String) {
        catchUpThreads.remove(projectId)?.interrupt()
    }

    private fun processCatchUp(state: SyncStateDoc) {
        try {
            doProcessCatchUp(state)
        } finally {
            catchUpThreads.remove(state.projectId)
        }
    }

    private fun doProcessCatchUp(state: SyncStateDoc) {
        val targetTemplate = registry.primaryTemplateByInstance(NODE_RULE, state.targetInstance)
            ?: run {
                updateState(
                    state.projectId, SyncState.INIT_FAILED, "Instance template not found"
                )
                return
            }

        val pipeline = buildChangeStreamPipeline(state.projectId)
        val changeStream = defaultMongoTemplate.db.watch(pipeline)
            .fullDocument(FullDocument.UPDATE_LOOKUP)
            .maxAwaitTime(CHANGE_STREAM_MAX_AWAIT_MS, TimeUnit.MILLISECONDS)

        // 设置断点续传位置：优先 resumeToken，其次 scanStartTimestamp
        val resumeToken = state.resumeToken?.let {
            runCatching { BsonDocument.parse(it) }.getOrNull()
        }
        when {
            resumeToken != null -> changeStream.resumeAfter(resumeToken)
            state.scanStartTimestamp != null ->
                changeStream.startAtOperationTime(BsonTimestamp(state.scanStartTimestamp.toInt(), 0))
        }

        var latestToken: BsonDocument? = null
        var eventCount = 0
        var lastEventTime = System.currentTimeMillis()
        var lastClusterTimeSecs = state.lastEventClusterTimeSecs
        val windowEnd = System.currentTimeMillis() + CATCHUP_WINDOW_MS

        try {
            changeStream.cursor().use { cursor ->
                while (System.currentTimeMillis() < windowEnd) {
                    // tryNext() 在 maxAwaitTime 窗口内阻塞等待服务端返回；无事件则返回 null
                    val event = cursor.tryNext()
                    if (shouldStopCatchUp(event, lastEventTime)) break
                    if (event == null) continue
                    latestToken = event.resumeToken
                    lastEventTime = System.currentTimeMillis()
                    NodeReconciliationHelper.clusterTimeSeconds(event.clusterTime)?.let {
                        lastClusterTimeSecs = it
                    }
                    val col = event.namespace?.collectionName ?: continue
                    processChangeStreamEvent(event, col, state.projectId, targetTemplate)
                    eventCount++
                }
            }
        } catch (e: MongoException) {
            if (isOplogWindowExpired(e)) {
                logger.warn(
                    "Project[${state.projectId}] oplog window expired (code=${e.code}) → REBUILD_REQUIRED"
                )
                updateState(state.projectId, SyncState.REBUILD_REQUIRED, "Oplog window expired")
                return
            }
            logger.error(
                "Change stream error for project[${state.projectId}]: ${e.message}", e
            )
            return
        } catch (e: Exception) {
            logger.error(
                "Unexpected error during CATCH_UP for project[${state.projectId}]: ${e.message}", e
            )
            return
        }

        if (latestToken != null) {
            updateResumeToken(state.projectId, latestToken!!.toJson())
        }
        if (lastClusterTimeSecs != null) {
            updateLastEventClusterTime(state.projectId, lastClusterTimeSecs!!)
        }

        val quietDuration = System.currentTimeMillis() - lastEventTime
        if (quietDuration >= LAG_QUIET_THRESHOLD_MS) {
            logger.info(
                "Project[${state.projectId}] CATCH_UP quiet for ${quietDuration}ms " +
                    "after $eventCount events → VERIFY"
            )
            updateState(state.projectId, SyncState.VERIFY)
        } else {
            logger.debug(
                "Project[${state.projectId}] CATCH_UP processed $eventCount events, " +
                    "window elapsed, will continue next cycle."
            )
        }
    }

    private fun shouldStopCatchUp(
        event: com.mongodb.client.model.changestream.ChangeStreamDocument<Document>?,
        lastEventTime: Long,
    ): Boolean {
        return event == null && System.currentTimeMillis() - lastEventTime >= LAG_QUIET_THRESHOLD_MS
    }

    private fun processChangeStreamEvent(
        event: com.mongodb.client.model.changestream.ChangeStreamDocument<Document>,
        collectionName: String,
        projectId: String,
        targetTemplate: MongoTemplate,
    ) {
        when (event.operationType) {
            OperationType.INSERT, OperationType.UPDATE, OperationType.REPLACE -> {
                val doc = event.fullDocument ?: return
                if (doc["projectId"] as? String != projectId) return
                val id = doc["_id"] ?: return
                val upsertQuery = Query(Criteria.where(ID).`is`(id))
                val update = Update()
                doc.entries.forEach { (k, v) -> if (k != "_id") update.set(k, v) }
                targetTemplate.upsert(upsertQuery, update, collectionName)
            }
            OperationType.DELETE -> {
                // delete 事件无 fullDocument，通过检查 Heavy 中是否存在来判断归属
                val id = event.documentKey?.get("_id") ?: return
                val query = Query(Criteria.where(ID).`is`(id))
                if (targetTemplate.exists(query, collectionName)) {
                    targetTemplate.remove(query, collectionName)
                }
            }
            else -> {}
        }
    }

    private fun buildChangeStreamPipeline(projectId: String): List<Bson> = listOf(
        Aggregates.match(
            Filters.and(
                // 仅监听 node_* 集合
                Filters.regex("ns.coll", "^node_\\d+$"),
                Filters.or(
                    // insert/update/replace 按 projectId 过滤
                    Filters.and(
                        Filters.ne("operationType", "delete"),
                        Filters.eq("fullDocument.projectId", projectId)
                    ),
                    // delete 事件无 fullDocument，全部透传后在应用层判断归属
                    Filters.eq("operationType", "delete")
                )
            )
        )
    )

    private fun isOplogWindowExpired(e: MongoException): Boolean {
        // code 136 = CursorNotFound, 286 = ChangeStreamHistoryLost
        return e.code == 136 || e.code == 286 ||
            e.message?.contains("CursorNotFound", ignoreCase = true) == true ||
            e.message?.contains("ChangeStreamHistoryLost", ignoreCase = true) == true
    }

    // ---------------------------------------------------------------
    // VERIFY：count 对比 + 抽样校验
    // ---------------------------------------------------------------

    private fun doVerify(state: SyncStateDoc) {
        val targetTemplate = registry.primaryTemplateByInstance(NODE_RULE, state.targetInstance)
            ?: run {
                updateState(state.projectId, SyncState.INIT_FAILED, "Instance template not found")
                return
            }
        val projectId = state.projectId

        if (hasSyncFailures(projectId)) {
            logger.info("Project[$projectId] VERIFY blocked: sync_failed queue not empty")
            return
        }

        if (syncStrategy() !in DUMP_STRATEGIES) {
            val lag = NodeReconciliationHelper.catchUpLagSeconds(
                defaultMongoTemplate,
                state.lastEventClusterTimeSecs,
            )
            if (lag != null && lag > NodeReconciliationHelper.CATCH_UP_LAG_THRESHOLD_SEC) {
                logger.info(
                    "Project[$projectId] VERIFY blocked: catch-up lag ${lag}s > " +
                        "${NodeReconciliationHelper.CATCH_UP_LAG_THRESHOLD_SEC}s"
                )
                return
            }
        }

        val countMismatches = NodeReconciliationHelper.countMismatches(
            projectId, defaultMongoTemplate, targetTemplate, SHARDING_COUNT,
        )
        val idMismatches = NodeReconciliationHelper.latestIdSetMismatches(
            projectId, defaultMongoTemplate, targetTemplate, VERIFY_SAMPLE_SIZE, SHARDING_COUNT,
        )
        val mismatchedShards = (countMismatches.map { it.shardIdx } + idMismatches).distinct()

        if (mismatchedShards.isNotEmpty()) {
            countMismatches.forEach {
                logger.warn(
                    "Project[$projectId] ${it.collectionName} count mismatch: " +
                        "src=${it.defaultCount} dst=${it.heavyCount}"
                )
            }
            when {
                mismatchedShards.size <= VERIFY_REPAIR_SHARD_THRESHOLD -> {
                    logger.warn(
                        "Project[$projectId] VERIFY: ${mismatchedShards.size} shard(s) mismatched, repairing."
                    )
                    repairShards(projectId, mismatchedShards, targetTemplate)
                    updateState(
                        projectId, SyncState.VERIFY,
                        error = "repaired shards $mismatchedShards; re-verifying",
                    )
                }
                else -> {
                    updateState(
                        projectId, SyncState.REBUILD_REQUIRED,
                        error = "count mismatch on ${mismatchedShards.size} shards",
                    )
                }
            }
            return
        }

        if (!NodeReconciliationHelper.lastModifiedAligned(
                projectId, defaultMongoTemplate, targetTemplate,
            )
        ) {
            logger.warn("Project[$projectId] VERIFY blocked: lastModifiedDate not aligned")
            NodeReconciliationHelper.persistLog(
                defaultMongoTemplate, projectId, "VERIFY", false, "lastModifiedDate mismatch",
            )
            return
        }

        if (NodeReconciliationHelper.segmentedSampleMismatch(
                projectId, defaultMongoTemplate, targetTemplate,
            )
        ) {
            logger.warn("Project[$projectId] VERIFY blocked: segmented sample mismatch")
            NodeReconciliationHelper.persistLog(
                defaultMongoTemplate, projectId, "VERIFY", false, "segmented sample mismatch",
            )
            return
        }

        val shardCol = NodeReconciliationHelper.shardCollection(projectId)
        val dChecksum = NodeReconciliationHelper.checksumSnapshot(
            defaultMongoTemplate, shardCol, projectId,
        )
        val hChecksum = NodeReconciliationHelper.checksumSnapshot(
            targetTemplate, shardCol, projectId,
        )
        if (!NodeReconciliationHelper.checksumsEqual(dChecksum, hChecksum)) {
            logger.warn(
                "Project[$projectId] VERIFY blocked: checksum mismatch default=$dChecksum heavy=$hChecksum"
            )
            NodeReconciliationHelper.persistLog(
                defaultMongoTemplate, projectId, "VERIFY", false, "checksum mismatch",
            )
            return
        }

        logger.info("Project[$projectId] VERIFY passed all gates; waiting for operator to call POST /migration/ready.")
        NodeReconciliationHelper.persistLog(
            defaultMongoTemplate, projectId, "VERIFY", true, "all gates passed; awaiting ready API",
        )
        // 不自动推进到 READY：READY 由运维通过 POST /migration/ready 人工确认（保留决策窗口，§9.3）
    }

    /**
     * 对指定分片执行全量 upsert 修复：将 Default 中该项目的所有文档重新 upsert 到 Heavy。
     */
    private fun repairShards(
        projectId: String,
        shardIndices: List<Int>,
        targetTemplate: MongoTemplate,
    ) {
        for (shardIdx in shardIndices) {
            val col = "$NODE_COLLECTION_PREFIX$shardIdx"
            var pageLastId = ObjectId(MIN_OBJECT_ID)
            do {
                val query = Query(
                    Criteria.where("projectId").`is`(projectId).and(ID).gt(pageLastId)
                ).limit(BATCH_SIZE).with(Sort.by(ID).ascending())
                val docs = defaultMongoTemplate.find(query, Document::class.java, col)
                if (docs.isEmpty()) break
                docs.forEach { doc ->
                    val docId = doc["_id"] as ObjectId
                    upsertWithRetry(targetTemplate, docId, doc, col, projectId)
                }
                pageLastId = docs.last()["_id"] as ObjectId
            } while (docs.size == BATCH_SIZE)
        }
    }

    // ---------------------------------------------------------------
    // CLEANUP_READY → CLEANED：分批删除 Default 上目标项目数据
    // ---------------------------------------------------------------

    private fun doCleanup(state: SyncStateDoc) {
        val projectId = state.projectId
        logger.info("Project[$projectId] starting CLEANUP: deleting from Default instance.")
        var totalDeleted = 0L

        for (shardIdx in 0 until SHARDING_COUNT) {
            val col = "$NODE_COLLECTION_PREFIX$shardIdx"
            val criteria = Criteria.where("projectId").`is`(projectId)
            do {
                // 分批查出 _id，再按 _id 删除，避免全表删除锁
                val ids = defaultMongoTemplate.find(
                    Query(criteria).limit(CLEANUP_BATCH_SIZE),
                    Document::class.java,
                    col,
                ).map { it["_id"] }
                if (ids.isEmpty()) break

                val result = defaultMongoTemplate.remove(
                    Query(Criteria.where(ID).`in`(ids)),
                    col,
                )
                totalDeleted += result.deletedCount
                // 每批删除后短暂休眠，降低对 Default 主库的压力
                Thread.sleep(CLEANUP_SLEEP_MS)
            } while (ids.size == CLEANUP_BATCH_SIZE)
        }

        logger.info("Project[$projectId] CLEANUP completed (total deleted=$totalDeleted) → CLEANED.")
        updateState(projectId, SyncState.CLEANED)
    }

    // ---------------------------------------------------------------
    // 索引一致性校验
    // ---------------------------------------------------------------

    private fun verifyIndexConsistency(targetTemplate: MongoTemplate): Boolean {
        for (shardIdx in 0 until SHARDING_COUNT) {
            val col = "$NODE_COLLECTION_PREFIX$shardIdx"
            if (!defaultMongoTemplate.collectionExists(col) &&
                !targetTemplate.collectionExists(col)
            ) {
                continue
            }
            val srcIndexes = defaultMongoTemplate.db
                .getCollection(col).listIndexes().map { it["name"].toString() }.toSet()
            val dstIndexes = runCatching {
                targetTemplate.db.getCollection(col).listIndexes()
                    .map { it["name"].toString() }.toSet()
            }.getOrDefault(emptySet())
            val missing = srcIndexes - dstIndexes
            if (missing.isNotEmpty()) {
                logger.error("Collection[$col] missing indexes on target: $missing")
                return false
            }
        }
        return true
    }

    // ---------------------------------------------------------------
    // 状态持久化（内存 + 本地文件断点）
    // ---------------------------------------------------------------

    /** 内存中的同步状态映射: projectId → SyncStateDoc（internal 用于测试注入初始状态） */
    internal val syncStates = ConcurrentHashMap<String, SyncStateDoc>()

    private fun updateState(projectId: String, state: SyncState, error: String? = null) {
        val existing = syncStates[projectId] ?: return
        syncStates[projectId] = existing.copy(
            state = state,
            lastError = error,
            updatedAt = LocalDateTime.now(),
        )
        persistState(projectId)
    }

    /**
     * 更新断点续传进度：[currentShardIdx] 为当前处理的分片索引，[lastSyncedId] 为该分片内的最后位置。
     * 当分片扫描完成推进到下一分片时，[lastSyncedId] 传 null 以清除当前位置。
     */
    private fun updateProgress(projectId: String, currentShardIdx: Int, lastSyncedId: String?) {
        val existing = syncStates[projectId] ?: return
        syncStates[projectId] = existing.copy(
            currentShardIdx = currentShardIdx,
            lastSyncedId = lastSyncedId,
            updatedAt = LocalDateTime.now(),
        )
        persistState(projectId)
    }

    private fun updateResumeToken(projectId: String, tokenJson: String) {
        val existing = syncStates[projectId] ?: return
        syncStates[projectId] = existing.copy(
            resumeToken = tokenJson,
            updatedAt = LocalDateTime.now(),
        )
        persistState(projectId)
    }

    private fun updateScanStartTimestamp(projectId: String, timestampSeconds: Long) {
        val existing = syncStates[projectId] ?: return
        syncStates[projectId] = existing.copy(
            scanStartTimestamp = timestampSeconds,
            updatedAt = LocalDateTime.now(),
        )
        persistState(projectId)
    }

    private fun loadAllSyncStates(): List<SyncStateDoc> {
        val dbProjectIds = mutableSetOf<String>()
        syncStateDao?.findByRuleName(NODE_RULE)?.forEach { doc ->
            val jobStateName = MigrationPhaseMapping.phaseToJobStateName(doc.phase) ?: return@forEach
            val jobState = runCatching { SyncState.valueOf(jobStateName) }.getOrNull() ?: return@forEach
            syncStates[doc.projectId] = SyncStateDoc(
                    id = doc.id ?: doc.projectId,
                    projectId = doc.projectId,
                    targetInstance = doc.targetInstance,
                    state = jobState,
                    currentShardIdx = doc.currentShardIdx,
                    lastSyncedId = doc.lastSyncedId,
                    lastError = doc.lastError,
                    updatedAt = doc.updatedAt,
                    resumeToken = doc.resumeToken,
                    scanStartTimestamp = doc.scanStartTimestamp,
                    lastEventClusterTimeSecs = doc.lastEventClusterTimeSecs,
                    dbaDumpCompleted = doc.dbaDumpCompleted,
                )
            dbProjectIds.add(doc.projectId)
        }
        // ponytail: 清理 DB 中已不存在的项目，防止 Pod 内存残留
        syncStates.keys.removeAll { it !in dbProjectIds }
        return syncStates.values.toList()
    }

    private fun updateLastEventClusterTime(projectId: String, clusterTimeSecs: Long) {
        val existing = syncStates[projectId] ?: return
        syncStates[projectId] = existing.copy(
            lastEventClusterTimeSecs = clusterTimeSecs,
            updatedAt = LocalDateTime.now(),
        )
        persistState(projectId)
    }

    private fun persistState(projectId: String) {
        val repo = syncStateDao ?: return
        val doc = syncStates[projectId] ?: return
        repo.upsert(
            TMigrationSyncState(
                id = doc.projectId,
                projectId = doc.projectId,
                ruleName = NODE_RULE,
                targetInstance = doc.targetInstance,
                phase = MigrationPhaseMapping.jobStateNameToPhase(doc.state.name),
                currentShardIdx = doc.currentShardIdx,
                lastSyncedId = doc.lastSyncedId,
                lastError = doc.lastError,
                updatedAt = doc.updatedAt,
                resumeToken = doc.resumeToken,
                scanStartTimestamp = doc.scanStartTimestamp,
                lastEventClusterTimeSecs = doc.lastEventClusterTimeSecs,
                dbaDumpCompleted = doc.dbaDumpCompleted,
            ),
        )
    }

    private fun syncStrategy(): String = registry.historicalSyncStrategy(NODE_RULE)

    private fun hasSyncFailures(projectId: String): Boolean =
        defaultMongoTemplate.count(
            Query(Criteria.where("projectId").`is`(projectId)),
            SYNC_FAILED_COLLECTION,
        ) > 0

    companion object {
        private val logger = LoggerFactory.getLogger(NodeProjectSyncJob::class.java)
        private const val NODE_RULE = "node"
        private const val NODE_COLLECTION_PREFIX = "node_"
        /** 同步失败文档记录表，供人工排查 */
        private const val SYNC_FAILED_COLLECTION = "node_project_sync_failed"
        private const val SHARDING_COUNT = 256
        private const val BATCH_SIZE = 500
        private const val CLEANUP_BATCH_SIZE = 500
        private const val CLEANUP_SLEEP_MS = 100L
        private const val CHANGE_STREAM_MAX_AWAIT_MS = 1_000L
        /** 每次 CATCH_UP 调度最多处理的时间窗口 */
        private const val CATCHUP_WINDOW_MS = 2 * 60 * 1000L
        /** 连续无事件超过此时长视为增量已追上 */
        private const val LAG_QUIET_THRESHOLD_MS = 30_000L
        /** upsert 单文档最大重试次数，超限后写入 sync_failed 表 */
        private const val UPSERT_MAX_RETRY = 3
        /** 重试间隔 */
        private const val UPSERT_RETRY_DELAY_MS = 200L
        /**
         * VERIFY 阶段：不一致分片数 ≤ 此阈值时执行定向修复；超出则转 REBUILD_REQUIRED。
         */
        private const val VERIFY_REPAIR_SHARD_THRESHOLD = 10

        /**
         * VERIFY 阶段内容抽样：取每个分片最新的 N 条文档比对 _id 集合，
         * 用于在 count 相等时发现内容不一致（如丢失的更新）。
         */
        private const val VERIFY_SAMPLE_SIZE = 200
        private const val STRATEGY_DUMP = "DUMP"
        private const val STRATEGY_DUMP_THEN_JOB = "DUMP_THEN_JOB"
        private val DUMP_STRATEGIES = setOf(STRATEGY_DUMP, STRATEGY_DUMP_THEN_JOB, "DBA_DUMP")
    }
}
