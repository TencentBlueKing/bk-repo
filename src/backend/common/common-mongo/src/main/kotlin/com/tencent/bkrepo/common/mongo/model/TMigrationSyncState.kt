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
    var resumeToken: String? = null,
    var scanStartTimestamp: Long? = null,
    var lastEventClusterTimeSecs: Long? = null,
    var dbaDumpCompleted: Boolean = false,
)
