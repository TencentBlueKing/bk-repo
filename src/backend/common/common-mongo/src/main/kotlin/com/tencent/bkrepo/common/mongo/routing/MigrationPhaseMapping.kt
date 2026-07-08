package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase

/** Job SyncState ↔ M0 MigrationPhase 映射 */
object MigrationPhaseMapping {

    fun phaseToJobStateName(phase: MigrationPhase): String? = when (phase) {
        MigrationPhase.PENDING, MigrationPhase.INIT_FAILED, MigrationPhase.ROLLBACK -> null
        MigrationPhase.INITIAL_SYNC -> "INITIAL_SYNC"
        MigrationPhase.DUAL_WRITE -> "DUAL_WRITE"
        MigrationPhase.ROUTED -> "ROUTED"
        MigrationPhase.CLEANUP_READY -> "CLEANUP_READY"
        MigrationPhase.CLEANED -> "CLEANED"
    }

    fun jobStateNameToPhase(stateName: String): MigrationPhase = when (stateName) {
        "INITIAL_SYNC" -> MigrationPhase.INITIAL_SYNC
        "DUAL_WRITE" -> MigrationPhase.DUAL_WRITE
        "ROUTED" -> MigrationPhase.ROUTED
        "CLEANUP_READY" -> MigrationPhase.CLEANUP_READY
        "CLEANED" -> MigrationPhase.CLEANED
        "INIT_FAILED" -> MigrationPhase.INIT_FAILED
        else -> MigrationPhase.PENDING
    }
}
