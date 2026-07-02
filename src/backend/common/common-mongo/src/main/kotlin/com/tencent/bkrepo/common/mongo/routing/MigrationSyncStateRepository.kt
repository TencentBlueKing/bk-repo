package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class MigrationSyncStateRepository(
    private val mongoTemplate: MongoTemplate,
) {

    fun findByProjectId(projectId: String): MigrationSyncStateDoc? =
        mongoTemplate.findById(projectId, MigrationSyncStateDoc::class.java, COLLECTION)

    fun findAll(): List<MigrationSyncStateDoc> =
        mongoTemplate.findAll(MigrationSyncStateDoc::class.java, COLLECTION)

    fun findByRuleName(ruleName: String): List<MigrationSyncStateDoc> =
        mongoTemplate.find(
            Query(Criteria.where(FIELD_RULE_NAME).`is`(ruleName)),
            MigrationSyncStateDoc::class.java,
            COLLECTION,
        )

    fun upsert(doc: MigrationSyncStateDoc) {
        val query = Query(Criteria.where("_id").`is`(doc.id))
        val update = Update()
            .set(FIELD_PROJECT_ID, doc.projectId)
            .set(FIELD_RULE_NAME, doc.ruleName)
            .set(FIELD_TARGET_INSTANCE, doc.targetInstance)
            .set(FIELD_PHASE, doc.phase.name)
            .set(FIELD_CURRENT_SHARD_IDX, doc.currentShardIdx)
            .set(FIELD_LAST_SYNCED_ID, doc.lastSyncedId)
            .set(FIELD_LAST_ERROR, doc.lastError)
            .set(FIELD_UPDATED_AT, doc.updatedAt)
            .set(FIELD_RESUME_TOKEN, doc.resumeToken)
            .set(FIELD_SCAN_START_TIMESTAMP, doc.scanStartTimestamp)
            .set(FIELD_LAST_EVENT_CLUSTER_TIME, doc.lastEventClusterTimeSecs)
            .set(FIELD_DBA_DUMP_COMPLETED, doc.dbaDumpCompleted)
        mongoTemplate.upsert(query, update, COLLECTION)
    }

    fun markDumpComplete(projectId: String) {
        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(projectId)),
            Update()
                .set(FIELD_DBA_DUMP_COMPLETED, true)
                .set(FIELD_UPDATED_AT, LocalDateTime.now()),
            COLLECTION,
        )
    }

    fun updatePhase(projectId: String, phase: MigrationPhase, error: String? = null) {
        mongoTemplate.updateFirst(
            Query(Criteria.where("_id").`is`(projectId)),
            Update()
                .set(FIELD_PHASE, phase.name)
                .set(FIELD_LAST_ERROR, error)
                .set(FIELD_UPDATED_AT, LocalDateTime.now()),
            COLLECTION,
        )
    }

    @Document(collection = COLLECTION)
    data class MigrationSyncStateDoc(
        @Id
        val id: String,
        val projectId: String,
        val ruleName: String,
        val targetInstance: String,
        val phase: MigrationPhase,
        val currentShardIdx: Int = 0,
        val lastSyncedId: String? = null,
        val lastError: String? = null,
        val updatedAt: LocalDateTime = LocalDateTime.now(),
        val resumeToken: String? = null,
        val scanStartTimestamp: Long? = null,
        val lastEventClusterTimeSecs: Long? = null,
        val dbaDumpCompleted: Boolean = false,
    )

    companion object {
        const val COLLECTION = "mongo_migration_sync_state"
        const val FIELD_PROJECT_ID = "projectId"
        const val FIELD_RULE_NAME = "ruleName"
        const val FIELD_TARGET_INSTANCE = "targetInstance"
        const val FIELD_PHASE = "phase"
        const val FIELD_CURRENT_SHARD_IDX = "currentShardIdx"
        const val FIELD_LAST_SYNCED_ID = "lastSyncedId"
        const val FIELD_LAST_ERROR = "lastError"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_RESUME_TOKEN = "resumeToken"
        const val FIELD_SCAN_START_TIMESTAMP = "scanStartTimestamp"
        const val FIELD_LAST_EVENT_CLUSTER_TIME = "lastEventClusterTimeSecs"
        const val FIELD_DBA_DUMP_COMPLETED = "dbaDumpCompleted"
    }
}
