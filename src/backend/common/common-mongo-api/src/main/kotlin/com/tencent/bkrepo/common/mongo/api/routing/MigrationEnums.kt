package com.tencent.bkrepo.common.mongo.api.routing

/** 与主方案 §1.6 / §3.9.1 状态机对齐 */
enum class MigrationPhase {
    PENDING, INITIAL_SYNC,
    DUAL_WRITE, ROUTED,
    CLEANUP_READY, CLEANED, ROLLBACK, INIT_FAILED,
}

enum class HistoricalSyncStrategy {
    NONE, JOB_ONLY,
}

/** 实例档位：DEFAULT 主实例；OFFLOAD 模式一集合族专属实例；HEAVY 模式二项目路由实例 */
enum class InstanceTier {
    DEFAULT, OFFLOAD, HEAVY,
}

enum class BindingType {
    DEDICATED, BUSINESS_GROUP,
}

/** 规则级路由状态 */
enum class RuleRoutingState {
    OFF, DUAL_WRITE, ROUTED,
}