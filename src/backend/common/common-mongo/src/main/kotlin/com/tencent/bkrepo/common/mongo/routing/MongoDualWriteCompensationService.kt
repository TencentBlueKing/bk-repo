package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.RouteTarget
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.query.BasicQuery
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class MongoDualWriteCompensationService(
    private val mongoTemplate: MongoTemplate,
    private val mongoConverter: MongoConverter,
    private val routingRegistry: MongoRoutingRegistry,
    private val properties: MongoMultiInstanceProperties,
    @Autowired(required = false)
    private val postCheck: CompensationPostCheck? = null,
    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("compensationMongoTemplate")
    private val compensationMongoTemplate: MongoTemplate? = null,
) {
    /** 补偿队列存储模板：优先独立实例（§25.2.4） */
    private val queueTemplate: MongoTemplate = compensationMongoTemplate ?: mongoTemplate
    /** 回退文件写入器，在 MongoDB 不可写时使用 */
    private val fallbackWriter = CompensationFallbackWriter()

    /** 当前 Pod 标识，用于分布式锁 */
    private val podId: String = runCatching {
        "${InetAddress.getLocalHost().hostName}-${Thread.currentThread().id}"
    }.getOrDefault("unknown")

    fun enqueueInsert(route: WriteRoute, collectionName: String, entity: Any) {
        val entityDoc = Document().also { mongoConverter.write(entity, it) }
        enqueue(
            route = route,
            collectionName = collectionName,
            operationType = OP_INSERT,
            entityClass = entity.javaClass.name,
            entityDocument = entityDoc,
            primaryKey = entityDoc.getObjectId("_id")?.toString(),
        )
    }

    fun enqueueSave(route: WriteRoute, collectionName: String, entity: Any) {
        val entityDoc = Document().also { mongoConverter.write(entity, it) }
        enqueue(
            route = route,
            collectionName = collectionName,
            operationType = OP_SAVE,
            entityClass = entity.javaClass.name,
            entityDocument = entityDoc,
            primaryKey = entityDoc.getObjectId("_id")?.toString(),
        )
    }

    fun enqueueRemove(route: WriteRoute, collectionName: String, entityClass: String, query: Query) {
        val queryDoc = Document(query.queryObject)
        enqueue(
            route = route,
            collectionName = collectionName,
            operationType = OP_REMOVE,
            entityClass = entityClass,
            queryDocument = queryDoc,
            primaryKey = queryDoc.getObjectId("_id")?.toString(),
        )
    }

    fun enqueueUpdateFirst(route: WriteRoute, collectionName: String, query: Query, update: Update) {
        val queryDoc = Document(query.queryObject)
        val updateDoc = Document(update.updateObject)
        enqueue(
            route = route,
            collectionName = collectionName,
            operationType = OP_UPDATE_FIRST,
            queryDocument = queryDoc,
            updateDocument = updateDoc,
            primaryKey = queryDoc.getObjectId("_id")?.toString(),
        )
    }

    fun enqueueUpdateMulti(route: WriteRoute, collectionName: String, query: Query, update: Update) {
        enqueue(
            route = route,
            collectionName = collectionName,
            operationType = OP_UPDATE_MULTI,
            queryDocument = Document(query.queryObject),
            updateDocument = Document(update.updateObject),
        )
    }

    fun enqueueUpsert(route: WriteRoute, collectionName: String, query: Query, update: Update) {
        val queryDoc = Document(query.queryObject)
        enqueue(
            route = route,
            collectionName = collectionName,
            operationType = OP_UPSERT,
            queryDocument = queryDoc,
            updateDocument = Document(update.updateObject),
            primaryKey = queryDoc.getObjectId("_id")?.toString(),
        )
    }

    fun enqueueFindAndModify(
        route: WriteRoute,
        collectionName: String,
        query: Query,
        update: Update,
        options: FindAndModifyOptions,
        entityClass: String,
    ) {
        val queryDoc = Document(query.queryObject)
        enqueue(
            route = route,
            collectionName = collectionName,
            operationType = OP_FIND_AND_MODIFY,
            entityClass = entityClass,
            queryDocument = queryDoc,
            updateDocument = Document(update.updateObject),
            optionsDocument = Document().apply {
                put("returnNew", options.isReturnNew)
                put("upsert", options.isUpsert)
                put("remove", options.isRemove)
            },
            primaryKey = queryDoc.getObjectId("_id")?.toString(),
        )
    }

    fun hasPendingTasks(ruleName: String, routingKey: String? = null): Boolean {
        val criteria = Criteria.where(FIELD_RULE_NAME).`is`(ruleName)
            .and(FIELD_STATUS).ne(STATUS_DONE)
        if (routingKey != null) {
            criteria.and(FIELD_ROUTING_KEY).`is`(routingKey)
        }
        return queueTemplate.exists(Query(criteria), COLLECTION_NAME)
    }

    fun countPendingTasks(ruleName: String, routingKey: String? = null): Long {
        val criteria = Criteria.where(FIELD_RULE_NAME).`is`(ruleName)
            .and(FIELD_STATUS).`is`(STATUS_PENDING)
        if (routingKey != null) {
            criteria.and(FIELD_ROUTING_KEY).`is`(routingKey)
        }
        return queueTemplate.count(Query(criteria), COLLECTION_NAME)
    }

    /** G-29：回滚时按 projectId 清理 PENDING 补偿任务 */
    fun deletePendingByRoutingKey(ruleName: String, routingKey: String): Long {
        val result = queueTemplate.remove(
            Query(
                Criteria.where(FIELD_RULE_NAME).`is`(ruleName)
                    .and(FIELD_ROUTING_KEY).`is`(routingKey)
                    .and(FIELD_STATUS).`is`(STATUS_PENDING),
            ),
            COLLECTION_NAME,
        )
        return result.deletedCount
    }

    /**
     * 查找最老的PENDING任务，用于健康检查。
     */
    fun findOldestPending(ruleName: String): Document? {
        return queueTemplate.findOne(
            Query(Criteria.where(FIELD_RULE_NAME).`is`(ruleName)
                .and(FIELD_STATUS).`is`(STATUS_PENDING))
                .with(org.springframework.data.domain.Sort.by(FIELD_CREATED_AT).ascending()),
            Document::class.java,
            COLLECTION_NAME,
        )
    }

    @Scheduled(
        fixedDelay = CONSUME_INTERVAL_MS,
        initialDelay = CONSUME_INTERVAL_MS,
    )
    fun consume() {
        // 使用 claimTask 分布式锁认领任务
        val tasks = claimTasks(BATCH_SIZE)
        tasks.forEach { task -> replay(task) }
    }

    // ─── 分布式锁消费 (§25.2.5 E-16) ───────────────────────────

    /**
     * 原子认领任务：findAndModify PENDING → PROCESSING。
     * 同一 _id 仅一个 Pod 能成功认领，防止多 Pod 并发消费。
     */
    fun claimTasks(limit: Int = BATCH_SIZE): List<Document> {
        val claimed = mutableListOf<Document>()
        for (i in 0 until limit) {
            val task = queueTemplate.findAndModify(
                Query(Criteria.where(FIELD_STATUS).`is`(STATUS_PENDING))
                    .with(org.springframework.data.domain.Sort.by(FIELD_CREATED_AT).ascending()),
                Update()
                    .set(FIELD_STATUS, STATUS_PROCESSING)
                    .set(FIELD_CLAIMED_BY, podId)
                    .set(FIELD_CLAIMED_AT, LocalDateTime.now())
                    .set(FIELD_UPDATED_AT, LocalDateTime.now()),
                FindAndModifyOptions.options().returnNew(true),
                Document::class.java,
                COLLECTION_NAME,
            ) ?: break // 无更多 PENDING 任务
            claimed.add(task)
        }
        if (claimed.isNotEmpty()) {
            logger.debug("Pod[$podId] claimed {} tasks", claimed.size)
        }
        return claimed
    }

    /**
     * 将超时的 PROCESSING 任务重置为 PENDING（防止 Pod 崩溃卡死）。
     */
    @Scheduled(fixedDelay = PROCESSING_TIMEOUT_CHECK_MS)
    fun resetStaleProcessing() {
        val staleAt = LocalDateTime.now().minusSeconds(PROCESSING_TIMEOUT_SECONDS)
        val result = queueTemplate.updateMulti(
            Query(
                Criteria.where(FIELD_STATUS).`is`(STATUS_PROCESSING)
                    .and(FIELD_CLAIMED_AT).lt(staleAt.toString())
            ),
            Update()
                .set(FIELD_STATUS, STATUS_PENDING)
                .unset(FIELD_CLAIMED_BY)
                .unset(FIELD_CLAIMED_AT)
                .set(FIELD_UPDATED_AT, LocalDateTime.now()),
            COLLECTION_NAME,
        )
        if (result.modifiedCount > 0) {
            logger.warn("Reset {} stale PROCESSING tasks to PENDING", result.modifiedCount)
        }
    }

    // ─── private ────────────────────────────────────────────────

    /**
     * 入队补偿任务，支持 replaceOrAdd 去重和 fallback 兜底。
     *
     * @param primaryKey 主路径 _id 字符串，用于 replaceOrAdd 去重
     */
    private fun enqueue(
        route: WriteRoute,
        collectionName: String,
        operationType: String,
        entityClass: String? = null,
        entityDocument: Document? = null,
        queryDocument: Document? = null,
        updateDocument: Document? = null,
        optionsDocument: Document? = null,
        primaryKey: String? = null,
    ) {
        val target = route.secondaryTarget ?: return
        val doc = Document().apply {
            put(FIELD_RULE_NAME, route.ruleName)
            put(FIELD_ROUTING_KEY, route.routingKey)
            put(FIELD_COLLECTION_NAME, collectionName)
            put(FIELD_OPERATION_TYPE, operationType)
            put(FIELD_TARGET_USE_DEFAULT, target.useDefault)
            put(FIELD_TARGET_INSTANCE, target.instanceName)
            put(FIELD_ENTITY_CLASS, entityClass)
            put(FIELD_ENTITY, entityDocument)
            put(FIELD_QUERY, queryDocument)
            put(FIELD_UPDATE, updateDocument)
            put(FIELD_OPTIONS, optionsDocument)
            put(FIELD_PRIMARY_KEY, primaryKey)
            put(FIELD_RETRY_COUNT, 0)
            put(FIELD_STATUS, STATUS_PENDING)
            put(FIELD_ENQUEUED_AT, System.nanoTime())
            put(FIELD_CREATED_AT, LocalDateTime.now())
            put(FIELD_UPDATED_AT, LocalDateTime.now())
        }

        try {
            if (!canEnqueueNewTask(primaryKey, route)) {
                logger.error(
                    "CRITICAL: compensation hard limit reached, fallback for [{}]",
                    collectionName,
                )
                fallbackWriter.write(docToSnapshot(doc, route))
                return
            }
            // replaceOrAdd：同一 primaryKey 只保留最新任务
            if (primaryKey != null) {
                replaceOrAdd(primaryKey, route.ruleName, route.routingKey, doc)
            } else {
                queueTemplate.insert(doc, COLLECTION_NAME)
            }
        } catch (e: Exception) {
            logger.error("CRITICAL: compensation enqueue failed [{}]: {}", collectionName, e.message)
            // 入队失败兜底：写本地文件
            fallbackWriter.write(docToSnapshot(doc, route))
        }
    }

    /**
     * replaceOrAdd：按 primaryKey 去重，仅保留最新任务。
     * CAS：仅当旧任务 status=PENDING 时才替换，否则追加新任务。
     */
    private fun replaceOrAdd(primaryKey: String, ruleName: String?, routingKey: String?, newDoc: Document) {
        val query = Query(
            Criteria.where(FIELD_PRIMARY_KEY).`is`(primaryKey)
                .and(FIELD_RULE_NAME).`is`(ruleName)
        )
        val oldDoc = queueTemplate.findOne(query, Document::class.java, COLLECTION_NAME)

        if (oldDoc == null) {
            queueTemplate.insert(newDoc, COLLECTION_NAME)
            return
        }

        if (oldDoc.getString(FIELD_STATUS) != STATUS_PENDING) {
            queueTemplate.insert(newDoc, COLLECTION_NAME)
            return
        }
        val merged = mergeCompensationDocs(oldDoc, newDoc)
        val replaced = queueTemplate.findAndReplace(
            Query(
                Criteria.where("_id").`is`(oldDoc.getObjectId("_id"))
                    .and(FIELD_STATUS).`is`(STATUS_PENDING),
            ),
            merged.apply {
                put("_id", oldDoc.getObjectId("_id"))
                put(FIELD_ENQUEUED_AT, System.nanoTime())
            },
            COLLECTION_NAME,
        )
        if (replaced == null) {
            queueTemplate.insert(newDoc, COLLECTION_NAME)
        }
    }

    private fun mergeCompensationDocs(oldDoc: Document, newDoc: Document): Document {
        val merged = Document(newDoc)
        val oldUpdate = oldDoc.get(FIELD_UPDATE, Document::class.java) ?: return merged
        val newUpdate = merged.get(FIELD_UPDATE, Document::class.java)
            ?: Document().also { merged.put(FIELD_UPDATE, it) }
        mergeIncOperations(oldUpdate, newUpdate)
        return merged
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeIncOperations(oldUpdate: Document, newUpdate: Document) {
        val oldInc = oldUpdate["\$inc"] as? Document ?: return
        val newInc = (newUpdate["\$inc"] as? Document) ?: Document().also { newUpdate["\$inc"] = it }
        oldInc.forEach { (field, delta) ->
            val base = (newInc[field] as? Number)?.toDouble() ?: 0.0
            val add = (delta as? Number)?.toDouble() ?: 0.0
            newInc[field] = base + add
        }
    }

    private fun canEnqueueNewTask(primaryKey: String?, route: WriteRoute): Boolean {
        val limits = properties.compensation
        val depth = queueTemplate.count(
            Query(Criteria.where(FIELD_STATUS).`is`(STATUS_PENDING)),
            COLLECTION_NAME,
        )
        if (depth >= limits.hardLimit) {
            if (primaryKey != null && hasPendingForKey(primaryKey, route)) {
                return true
            }
            return false
        }
        if (depth >= limits.softLimit) {
            logger.warn("Compensation queue depth {} exceeds soft limit {}", depth, limits.softLimit)
        }
        return true
    }

    private fun hasPendingForKey(primaryKey: String, route: WriteRoute): Boolean {
        val query = Query(
            Criteria.where(FIELD_PRIMARY_KEY).`is`(primaryKey)
                .and(FIELD_RULE_NAME).`is`(route.ruleName)
                .and(FIELD_STATUS).`is`(STATUS_PENDING),
        )
        return queueTemplate.exists(query, COLLECTION_NAME)
    }

    private fun docToSnapshot(doc: Document, route: WriteRoute): CompensationTaskSnapshot {
        val target = route.secondaryTarget ?: RouteTarget()
        return CompensationTaskSnapshot(
            ruleName = route.ruleName,
            routingKey = route.routingKey,
            collectionName = doc.getString(FIELD_COLLECTION_NAME) ?: "",
            operationType = doc.getString(FIELD_OPERATION_TYPE) ?: "",
            targetUseDefault = target.useDefault,
            targetInstance = target.instanceName,
            entityClass = doc.getString(FIELD_ENTITY_CLASS),
            entityDocument = doc.get(FIELD_ENTITY, Document::class.java)?.toMap(),
            queryDocument = doc.get(FIELD_QUERY, Document::class.java)?.toMap(),
            updateDocument = doc.get(FIELD_UPDATE, Document::class.java)?.toMap(),
            optionsDocument = doc.get(FIELD_OPTIONS, Document::class.java)?.toMap(),
            createdAt = LocalDateTime.now().toString(),
        )
    }

    private fun replay(task: Document) {
        val id = task.getObjectId("_id") ?: return
        try {
            val template = resolveTargetTemplate(task) ?: return markFailed(id, "target template not found")
            val collectionName = task.getString(FIELD_COLLECTION_NAME)
            when (task.getString(FIELD_OPERATION_TYPE)) {
                OP_INSERT -> {
                    val entity = readEntity(task) ?: return markFailed(id, "entity missing")
                    template.insert(entity, collectionName)
                }

                OP_SAVE -> {
                    val entity = readEntity(task) ?: return markFailed(id, "entity missing")
                    template.save(entity, collectionName)
                }

                OP_REMOVE -> {
                    val query = BasicQuery(task.get(FIELD_QUERY, Document::class.java))
                    val entityClass = resolveEntityClass(task) ?: return markFailed(id, "entity class missing")
                    template.remove(query, entityClass, collectionName)
                }

                OP_UPDATE_FIRST -> {
                    template.updateFirst(
                        BasicQuery(task.get(FIELD_QUERY, Document::class.java)),
                        protectLastModified(Update.fromDocument(task.get(FIELD_UPDATE, Document::class.java))),
                        collectionName,
                    )
                }

                OP_UPDATE_MULTI -> {
                    template.updateMulti(
                        BasicQuery(task.get(FIELD_QUERY, Document::class.java)),
                        protectLastModified(Update.fromDocument(task.get(FIELD_UPDATE, Document::class.java))),
                        collectionName,
                    )
                }

                OP_UPSERT -> {
                    template.upsert(
                        BasicQuery(task.get(FIELD_QUERY, Document::class.java)),
                        protectLastModified(Update.fromDocument(task.get(FIELD_UPDATE, Document::class.java))),
                        collectionName,
                    )
                }

                OP_FIND_AND_MODIFY -> {
                    val entityClass = resolveEntityClass(task) ?: return markFailed(id, "entity class missing")
                    val optionsDoc = task.get(FIELD_OPTIONS, Document::class.java) ?: Document()
                    val options = FindAndModifyOptions.options()
                        .returnNew(optionsDoc.getBoolean("returnNew", false))
                        .upsert(optionsDoc.getBoolean("upsert", false))
                    if (optionsDoc.getBoolean("remove", false)) {
                        options.remove(true)
                    }
                    template.findAndModify(
                        BasicQuery(task.get(FIELD_QUERY, Document::class.java)),
                        protectLastModified(Update.fromDocument(task.get(FIELD_UPDATE, Document::class.java))),
                        options,
                        entityClass,
                        collectionName,
                    )
                }
            }
            queueTemplate.updateFirst(
                Query(Criteria.where("_id").`is`(id)),
                Update()
                    .set(FIELD_STATUS, STATUS_DONE)
                    .set(FIELD_UPDATED_AT, LocalDateTime.now()),
                COLLECTION_NAME,
            )
            postCheck?.postReplayCheck(task)
        } catch (exception: Exception) {
            val retryCount = task.getInteger(FIELD_RETRY_COUNT, 0) + 1
            if (retryCount >= MAX_RETRY) {
                logger.error("Mongo dual-write compensation failed permanently: {}", task, exception)
                queueTemplate.updateFirst(
                    Query(Criteria.where("_id").`is`(id)),
                    Update()
                        .set(FIELD_STATUS, STATUS_FAILED)
                        .set(FIELD_RETRY_COUNT, retryCount)
                        .set(FIELD_FAILURE_REASON, exception.message?.take(500))
                        .set(FIELD_UPDATED_AT, LocalDateTime.now()),
                    COLLECTION_NAME,
                )
            } else {
                logger.warn("Mongo dual-write compensation retry {}/{}", retryCount, MAX_RETRY, exception)
                queueTemplate.updateFirst(
                    Query(Criteria.where("_id").`is`(id)),
                    Update()
                        .set(FIELD_STATUS, STATUS_PENDING)  // 重置为PENDING供下次消费
                        .unset(FIELD_CLAIMED_BY)
                        .unset(FIELD_CLAIMED_AT)
                        .set(FIELD_RETRY_COUNT, retryCount)
                        .set(FIELD_UPDATED_AT, LocalDateTime.now()),
                    COLLECTION_NAME,
                )
            }
        }
    }

    private fun readEntity(task: Document): Any? {
        val entityClass = resolveEntityClass(task) ?: return null
        val entityDoc = task.get(FIELD_ENTITY, Document::class.java) ?: return null
        return mongoConverter.read(entityClass, entityDoc)
    }

    /**
     * 补偿回放时保护 lastModifiedDate（§25.2.1 / §3.15.7）。
     *
     * 补偿任务从入队到回放可能有几分钟到几小时延迟，期间副本可能已被其他写入更新。
     * 若回放时直接用旧 update doc 中的 $set.lastModifiedDate，会用旧时间覆盖新时间，
     * 造成 lastModifiedDate 回退。这里改用 $max，仅当旧时间晚于副本现有时间时才更新。
     *
     * 处理规则：
     * - $set.lastModifiedDate / lastModifiedTime 字段 → 改为 $max
     * - 顶层 lastModifiedDate（无 $操作符的全文档替换）→ 不修改（语义是全量替换）
     */
    private fun protectLastModified(update: Update): Update {
        val updateDoc = update.updateObject
        val setDoc = updateDoc["\$set"] as? Document ?: return update
        val protectedFields = listOf("lastModifiedDate", "lastModifiedTime")
        val maxDoc = (updateDoc["\$max"] as? Document) ?: Document()
        var changed = false
        protectedFields.forEach { field ->
            val value = setDoc.remove(field)
            if (value != null) {
                // 仅当 $max 中尚无该字段时才追加，避免覆盖已有 $max 表达式
                if (!maxDoc.containsKey(field)) {
                    maxDoc[field] = value
                    changed = true
                }
            }
        }
        if (!changed) return update
        if (setDoc.isEmpty()) {
            updateDoc.remove("\$set")
        }
        updateDoc["\$max"] = maxDoc
        return Update.fromDocument(updateDoc)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveEntityClass(task: Document): Class<Any>? =
        runCatching { Class.forName(task.getString(FIELD_ENTITY_CLASS)) as Class<Any> }.getOrNull()

    private fun resolveTargetTemplate(task: Document): MongoTemplate? {
        return if (task.getBoolean(FIELD_TARGET_USE_DEFAULT, false)) {
            mongoTemplate
        } else {
            val ruleName = task.getString(FIELD_RULE_NAME) ?: return null
            val instanceName = task.getString(FIELD_TARGET_INSTANCE) ?: return null
            routingRegistry.primaryTemplateByInstance(ruleName, instanceName)
        }
    }

    private fun markFailed(id: Any, reason: String) {
        queueTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(id)),
            Update()
                .set(FIELD_STATUS, STATUS_FAILED)
                .set(FIELD_UPDATED_AT, LocalDateTime.now())
                .set(FIELD_FAILURE_REASON, reason),
            COLLECTION_NAME,
        )
    }

    // ─── Document.toMap 辅助扩展 ─────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun Document.toMap(): Map<String, Any?> = this.map { it.key to it.value }.toMap()

    companion object {
        const val COLLECTION_NAME = "mongo_dual_write_compensation"

        private const val FIELD_RULE_NAME = "ruleName"
        private const val FIELD_ROUTING_KEY = "routingKey"
        private const val FIELD_COLLECTION_NAME = "collectionName"
        private const val FIELD_OPERATION_TYPE = "operationType"
        private const val FIELD_TARGET_USE_DEFAULT = "targetUseDefault"
        private const val FIELD_TARGET_INSTANCE = "targetInstance"
        private const val FIELD_ENTITY_CLASS = "entityClass"
        private const val FIELD_ENTITY = "entity"
        private const val FIELD_QUERY = "query"
        private const val FIELD_UPDATE = "update"
        private const val FIELD_OPTIONS = "options"
        private const val FIELD_PRIMARY_KEY = "primaryKey"
        private const val FIELD_RETRY_COUNT = "retryCount"
        private const val FIELD_STATUS = "status"
        private const val FIELD_ENQUEUED_AT = "enqueuedAt"
        private const val FIELD_CLAIMED_BY = "claimedBy"
        private const val FIELD_CLAIMED_AT = "claimedAt"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_FAILURE_REASON = "failureReason"

        private const val OP_INSERT = "INSERT"
        private const val OP_SAVE = "SAVE"
        private const val OP_REMOVE = "REMOVE"
        private const val OP_UPDATE_FIRST = "UPDATE_FIRST"
        private const val OP_UPDATE_MULTI = "UPDATE_MULTI"
        private const val OP_UPSERT = "UPSERT"
        private const val OP_FIND_AND_MODIFY = "FIND_AND_MODIFY"

        private const val STATUS_PENDING = "PENDING"
        private const val STATUS_PROCESSING = "PROCESSING"
        private const val STATUS_DONE = "DONE"
        private const val STATUS_FAILED = "FAILED"

        private const val MAX_RETRY = 3
        private const val BATCH_SIZE = 200
        private const val CONSUME_INTERVAL_MS = 60_000L
        private const val PROCESSING_TIMEOUT_SECONDS = 300L
        private const val PROCESSING_TIMEOUT_CHECK_MS = 120_000L

        private val logger = LoggerFactory.getLogger(MongoDualWriteCompensationService::class.java)
    }
}
