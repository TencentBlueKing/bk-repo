package com.tencent.bkrepo.common.mongo.api.routing

import java.time.LocalDateTime

/** M0 迁移同步状态契约（对应 [MongoRoutingCollections.MIGRATION_STATE]） */
data class MigrationSyncState(
    val projectId: String,
    val ruleName: String,
    val phase: MigrationPhase,
    val targetInstance: String,
    val currentShardIdx: Int = 0,
    val lastSyncedId: String? = null,
    val lastError: String? = null,
    val updatedAt: LocalDateTime? = null,
    val strategy: String? = null,
)

/** 历史同步失败记录契约（对应 [MongoRoutingCollections.SYNC_FAILED]） */
data class SyncFailedRecord(
    val ruleName: String,
    val ownerId: String,
    val collectionName: String,
    val docId: String,
    val error: String?,
    val createdAt: LocalDateTime? = null,
)

/** 双写补偿任务契约（对应 [MongoRoutingCollections.COMPENSATION]） */
data class CompensationTask(
    val primaryKey: String?,
    val ruleName: String?,
    val routingKey: String?,
    val collectionName: String,
    val operationType: String,
    val status: String,
    val retryCount: Int = 0,
    val nextRetryAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)
