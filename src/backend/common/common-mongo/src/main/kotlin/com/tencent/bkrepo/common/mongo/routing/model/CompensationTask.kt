package com.tencent.bkrepo.common.mongo.routing.model

import org.bson.Document
import java.time.LocalDateTime

/**
 * 双写补偿任务（写入 mongo_dual_write_compensation）。
 *
 * ponytail: 代替 MongoDualWriteCompensationService.enqueue 中 17 个 put() 的手写 Document。
 * 字段名与 Mongo 文档 key 对齐（通过 [toDocument] 序列化），方便维护和 IDE 重构。
 * replay / claim 等消费路径仍读 Document（MongoTemplate 原生返回），不走本类反序列化。
 */
data class CompensationTask(
    val ruleName: String?,
    val routingKey: String?,
    val collectionName: String,
    val operationType: String,
    val targetUseDefault: Boolean,
    val targetInstance: String?,
    val entityClass: String?,
    val entity: Document?,
    val query: Document?,
    val update: Document?,
    val options: Document?,
    val primaryKey: String?,
    val retryCount: Int = 0,
    val status: String = STATUS_PENDING,
    val nextRetryAt: LocalDateTime? = null,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun toDocument(): Document = Document().apply {
        put(FIELD_RULE_NAME, ruleName)
        put(FIELD_ROUTING_KEY, routingKey)
        put(FIELD_COLLECTION_NAME, collectionName)
        put(FIELD_OPERATION_TYPE, operationType)
        put(FIELD_TARGET_USE_DEFAULT, targetUseDefault)
        put(FIELD_TARGET_INSTANCE, targetInstance)
        put(FIELD_ENTITY_CLASS, entityClass)
        put(FIELD_ENTITY, entity)
        put(FIELD_QUERY, query)
        put(FIELD_UPDATE, update)
        put(FIELD_OPTIONS, options)
        put(FIELD_PRIMARY_KEY, primaryKey)
        put(FIELD_RETRY_COUNT, retryCount)
        put(FIELD_STATUS, status)
        put(FIELD_NEXT_RETRY_AT, nextRetryAt)
        put(FIELD_ENQUEUED_AT, enqueuedAt)
        put(FIELD_CREATED_AT, createdAt)
        put(FIELD_UPDATED_AT, updatedAt)
    }

    companion object {
        const val COLLECTION_NAME = "mongo_dual_write_compensation"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"

        const val FIELD_RULE_NAME = "ruleName"
        const val FIELD_ROUTING_KEY = "routingKey"
        const val FIELD_COLLECTION_NAME = "collectionName"
        const val FIELD_OPERATION_TYPE = "operationType"
        const val FIELD_TARGET_USE_DEFAULT = "targetUseDefault"
        const val FIELD_TARGET_INSTANCE = "targetInstance"
        const val FIELD_ENTITY_CLASS = "entityClass"
        const val FIELD_ENTITY = "entity"
        const val FIELD_QUERY = "query"
        const val FIELD_UPDATE = "update"
        const val FIELD_OPTIONS = "options"
        const val FIELD_PRIMARY_KEY = "primaryKey"
        const val FIELD_RETRY_COUNT = "retryCount"
        const val FIELD_STATUS = "status"
        const val FIELD_ENQUEUED_AT = "enqueuedAt"
        const val FIELD_CLAIMED_BY = "claimedBy"
        const val FIELD_CLAIMED_AT = "claimedAt"
        const val FIELD_NEXT_RETRY_AT = "nextRetryAt"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_FAILURE_REASON = "failureReason"

        const val OP_INSERT = "INSERT"
        const val OP_SAVE = "SAVE"
        const val OP_REMOVE = "REMOVE"
        const val OP_UPDATE_FIRST = "UPDATE_FIRST"
        const val OP_UPDATE_MULTI = "UPDATE_MULTI"
        const val OP_UPSERT = "UPSERT"
        const val OP_FIND_AND_MODIFY = "FIND_AND_MODIFY"

        const val MAX_RETRY = 3
    }
}