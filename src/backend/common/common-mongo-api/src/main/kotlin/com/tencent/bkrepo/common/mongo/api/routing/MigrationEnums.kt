package com.tencent.bkrepo.common.mongo.api.routing

/** 与主方案 §1.6 / §3.9.1 状态机对齐；READY 为 VERIFY 通过后的运维就绪态 */
enum class MigrationPhase {
    PENDING, CS_START, DUMPING, JOB_GAP, JOB_FULL,
    CATCH_UP, VERIFY, READY, DUAL_WRITE, ROUTED,
    CLEANUP_READY, CLEANED, ROLLBACK, INIT_FAILED, REBUILD_REQUIRED,
}

enum class HistoricalSyncStrategy {
    NONE, DUMP, DUMP_THEN_JOB, JOB_ONLY,
}

enum class InstanceTier {
    DEFAULT, OPLOG, HEAVY,
}

enum class BindingType {
    DEDICATED, BUSINESS_GROUP,
}
