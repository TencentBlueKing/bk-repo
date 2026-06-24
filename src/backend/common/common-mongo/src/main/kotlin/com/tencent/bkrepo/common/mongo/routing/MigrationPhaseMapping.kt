package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase

/** Job SyncState ↔ M0 MigrationPhase 映射 */
object MigrationPhaseMapping {

    fun phaseToJobStateName(phase: MigrationPhase): String? = when (phase) {
        MigrationPhase.PENDING, MigrationPhase.INIT_FAILED -> null
        MigrationPhase.CS_START -> "INIT"
        MigrationPhase.DUMPING -> "DBA_DUMPING"
        MigrationPhase.JOB_GAP -> "JOB_GAP"
        MigrationPhase.JOB_FULL -> "INITIAL_SYNC"
        MigrationPhase.CATCH_UP -> "CATCH_UP"
        MigrationPhase.VERIFY -> "VERIFY"
        MigrationPhase.READY -> "READY"
        MigrationPhase.DUAL_WRITE -> "DUAL_WRITE"
        MigrationPhase.ROUTED -> "ROUTED"
        MigrationPhase.CLEANUP_READY -> "CLEANUP_READY"
        MigrationPhase.CLEANED -> "CLEANED"
        MigrationPhase.ROLLBACK, MigrationPhase.REBUILD_REQUIRED -> null
    }

    fun jobStateNameToPhase(stateName: String): MigrationPhase = when (stateName) {
        "INIT" -> MigrationPhase.CS_START
        "INIT_FAILED" -> MigrationPhase.INIT_FAILED
        "DBA_DUMPING" -> MigrationPhase.DUMPING
        "JOB_GAP" -> MigrationPhase.JOB_GAP
        "INITIAL_SYNC" -> MigrationPhase.JOB_FULL
        "CATCH_UP" -> MigrationPhase.CATCH_UP
        "VERIFY" -> MigrationPhase.VERIFY
        "REBUILD_REQUIRED" -> MigrationPhase.REBUILD_REQUIRED
        "READY" -> MigrationPhase.READY
        "DUAL_WRITE" -> MigrationPhase.DUAL_WRITE
        "ROUTED" -> MigrationPhase.ROUTED
        "CLEANUP_READY" -> MigrationPhase.CLEANUP_READY
        "CLEANED" -> MigrationPhase.CLEANED
        else -> MigrationPhase.PENDING
    }
}
