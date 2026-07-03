package com.tencent.bkrepo.common.mongo.dao

import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.common.mongo.model.TMigrationSyncState
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class MigrationSyncStateDao : SimpleMongoDao<TMigrationSyncState>() {

    fun findByProjectId(projectId: String): TMigrationSyncState? = findById(projectId)

    fun findByRuleName(ruleName: String): List<TMigrationSyncState> =
        find(Query(TMigrationSyncState::ruleName.isEqualTo(ruleName)))

    fun upsert(state: TMigrationSyncState) {
        val id = state.id ?: state.projectId
        val query = Query(Criteria.where(ID).isEqualTo(id))
        val update = Update()
            .set(TMigrationSyncState::projectId.name, state.projectId)
            .set(TMigrationSyncState::ruleName.name, state.ruleName)
            .set(TMigrationSyncState::targetInstance.name, state.targetInstance)
            .set(TMigrationSyncState::phase.name, state.phase)
            .set(TMigrationSyncState::currentShardIdx.name, state.currentShardIdx)
            .set(TMigrationSyncState::lastSyncedId.name, state.lastSyncedId)
            .set(TMigrationSyncState::lastError.name, state.lastError)
            .set(TMigrationSyncState::updatedAt.name, state.updatedAt)
            .set(TMigrationSyncState::resumeToken.name, state.resumeToken)
            .set(TMigrationSyncState::scanStartTimestamp.name, state.scanStartTimestamp)
            .set(TMigrationSyncState::lastEventClusterTimeSecs.name, state.lastEventClusterTimeSecs)
            .set(TMigrationSyncState::dbaDumpCompleted.name, state.dbaDumpCompleted)
        upsert(query, update)
    }

    fun markDumpComplete(projectId: String) {
        if (projectId.isBlank()) {
            return
        }
        updateFirst(
            Query(Criteria.where(ID).isEqualTo(projectId)),
            Update()
                .set(TMigrationSyncState::dbaDumpCompleted.name, true)
                .set(TMigrationSyncState::updatedAt.name, LocalDateTime.now()),
        )
    }

    fun updatePhase(projectId: String, phase: MigrationPhase, error: String? = null) {
        if (projectId.isBlank()) {
            return
        }
        updateFirst(
            Query(Criteria.where(ID).isEqualTo(projectId)),
            Update()
                .set(TMigrationSyncState::phase.name, phase)
                .set(TMigrationSyncState::lastError.name, error)
                .set(TMigrationSyncState::updatedAt.name, LocalDateTime.now()),
        )
    }
}
