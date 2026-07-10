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

    fun findByRuleAndProject(ruleName: String, projectId: String): TMigrationSyncState? =
        findById(resolveId(ruleName, projectId))

    /** 模式一 offload：id 与 projectId 相同；模式二：id = ruleName:projectId */
    fun findByProjectId(projectId: String): TMigrationSyncState? = findById(projectId)

    fun findAllByProjectId(projectId: String): List<TMigrationSyncState> =
        find(Query(TMigrationSyncState::projectId.isEqualTo(projectId)))

    fun findByRuleName(ruleName: String): List<TMigrationSyncState> =
        find(Query(TMigrationSyncState::ruleName.isEqualTo(ruleName)))

    fun findByPhases(phases: Collection<MigrationPhase>): List<TMigrationSyncState> =
        find(Query(Criteria.where(TMigrationSyncState::phase.name).`in`(phases)))

    fun upsert(state: TMigrationSyncState) {
        val id = state.id ?: resolveId(state.ruleName, state.projectId)
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
            .set(TMigrationSyncState::strategy.name, state.strategy)
            .set(TMigrationSyncState::syncCycleCount.name, state.syncCycleCount)
        upsert(query, update)
    }

    fun updatePhase(ruleName: String, projectId: String, phase: MigrationPhase, error: String? = null) {
        if (projectId.isBlank()) {
            return
        }
        updateFirst(
            Query(Criteria.where(ID).isEqualTo(resolveId(ruleName, projectId))),
            Update()
                .set(TMigrationSyncState::phase.name, phase)
                .set(TMigrationSyncState::lastError.name, error)
                .set(TMigrationSyncState::updatedAt.name, LocalDateTime.now()),
        )
    }

    companion object {
        fun resolveId(ruleName: String, projectId: String): String =
            if (projectId == ruleName) projectId else "$ruleName:$projectId"
    }
}
