package com.tencent.bkrepo.common.mongo.model

import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("mongo_migration_sync_state")
data class TMigrationSyncState(
    @Id
    var id: String? = null,
    var projectId: String,
    var ruleName: String,
    var targetInstance: String,
    var phase: MigrationPhase,
    var currentShardIdx: Int = 0,
    var lastSyncedId: String? = null,
    var lastError: String? = null,
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    /** 历史同步策略（NONE / JOB_ONLY），由 POST /migration/binding 写入 */
    var strategy: String? = null,
    /** sync_failed 循环计数，>= MAX_SYNC_CYCLES 时降级 INIT_FAILED */
    var syncCycleCount: Int = 0,
)
