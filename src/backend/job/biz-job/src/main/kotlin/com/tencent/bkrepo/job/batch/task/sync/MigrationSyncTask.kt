package com.tencent.bkrepo.job.batch.task.sync

import java.time.LocalDateTime

enum class MigrationSyncJobState {
    INIT_FAILED,
    INITIAL_SYNC,
    DUAL_WRITE,
    ROUTED,
    CLEANUP_READY,
    CLEANED,
}

data class MigrationSyncTask(
    val stateKey: String,
    val projectId: String,
    val ruleName: String,
    val targetInstance: String,
    val state: MigrationSyncJobState,
    val currentShardIdx: Int = 0,
    val lastSyncedId: String? = null,
    val syncCycleCount: Int = 0,
    val lastError: String? = null,
    val updatedAt: LocalDateTime,
)
