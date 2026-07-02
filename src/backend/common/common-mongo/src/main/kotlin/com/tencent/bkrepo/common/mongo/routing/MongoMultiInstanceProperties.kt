package com.tencent.bkrepo.common.mongo.routing

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.data.mongodb.multi-instance")
class MongoMultiInstanceProperties {
    var rules: Map<String, RoutingRule> = emptyMap()
    /** 每次路由配置变更时递增，用于跨Pod一致性校验 */
    var configVersion: Long = 0
    /** 所有Pod必须达到的最小配置版本 */
    var minConfigVersion: Long = 0
    /** 同时进行双写的项目数上限（跨规则全局限制） */
    var maxConcurrentDualWrite: Int = 1

    class RoutingRule(
        var routingType: RoutingType = RoutingType.PROJECT,
        /**
         * 集合名前缀，用于自动匹配集合到该规则。
         * 如 "node_" 匹配所有 node_* 集合，"artifact_oplog_" 匹配所有 oplog 月表。
         */
        var collectionPrefix: String = "",
        /** 从 Query/Entity 提取路由键的字段名，routing-type=PROJECT 时生效 */
        var routingKeyField: String = "projectId",
        var routingEnabled: Boolean = false,
        var dualWrite: Boolean = false,
        /** per-rule 迁移配置 */
        var migration: MigrationConfig = MigrationConfig(),
        var instances: Map<String, InstanceConfig> = emptyMap(),
        /** projectId → 实例名 */
        var projectRouting: Map<String, String> = emptyMap(),
        /** 集合名 → 实例名（分片级路由，整个集合表迁移到 Heavy） */
        var shardRouting: Map<String, String> = emptyMap(),
        /** businessId → 实例名（Tier-Biz §3.5.2） */
        var businessRouting: Map<String, String> = emptyMap(),
        /** 分表数量，用于 §13.3 project/shard-routing 冲突校验 */
        var shardingCount: Int = 256,
    ) {
        data class InstanceConfig(
            val uri: String = "",
            val secondaryUri: String = "",
            val fallbackBeforeCleanup: Boolean = false,
            /** 实例级连接池覆盖 */
            val maxPoolSize: Int = 50,
            val minPoolSize: Int = 5,
        )
        data class MigrationConfig(
            val mode: String = "NONE",
            /** NONE / DUMP / DUMP_THEN_JOB / JOB_ONLY */
            val historicalSyncStrategy: String = "NONE",
            val syncJob: SyncJobConfig = SyncJobConfig(),
            val dbaDump: DbaDumpConfig = DbaDumpConfig(),
            val none: NoneConfig = NoneConfig(),
            /** 同时进行双写的项目数上限 */
            val maxConcurrentDualWrite: Int = 1,
            /** 僵尸副本在 Default 上存活超过该小时数则阻断迁移（G-17） */
            val maxZombieHours: Int = 72,
            /** 迁移期项目锁 */
            val projectLocks: ProjectLocksConfig = ProjectLocksConfig(),
        )
        data class SyncJobConfig(
            val batchSize: Int = 500,
            val parallelProjects: Int = 3,
            val changeStreamEnabled: Boolean = true,
            val retryCount: Int = 3,
        )
        data class DbaDumpConfig(
            val collections: List<DumpCollection> = emptyList(),
            val restoreOptions: RestoreOptions = RestoreOptions(),
        )
        data class DumpCollection(
            val name: String = "",
            val source: String = "default",
            val target: String = "",
            val query: String = "{}",
        )
        data class RestoreOptions(
            val numParallelCollections: Int = 1,
            val batchSize: Int = 1000,
        )
        data class NoneConfig(
            val maxDurationDays: Int = 30,
            val expirationAction: String = "BLOCK",
            val scatterQueryMerge: Boolean = true,
            val dedupKey: String = "_id",
        )
        data class ProjectLocksConfig(
            /** 迁移期间冻结GC（§3.18.2 file_reference GC 第〇层防护） */
            val freezeGc: Boolean = true,
            /** 迁移期间禁止DDL */
            val freezeDdl: Boolean = true,
            /** DDL冻结实例列表 */
            val freezeDdlInstances: List<String> = emptyList(),
            /**
             * ROUTED ~ CLEANUP 期间禁止物理删除 Default 上的 node 副本（§3.18.2）。
             * 保留 Default 副本以支持回滚。
             */
            val freezePhysicalDelete: Boolean = true,
            /**
             * DUAL_WRITE ~ CLEANUP 期间禁止 Default 侧 node 变更（§3.18.2）。
             * 防止旁路写入造成双写不一致。
             */
            val freezeDefaultNodeMutation: Boolean = true,
        )
    }

    enum class RoutingType {
        /** 按项目路由，routing-key-field 指定路由键字段名 */
        PROJECT,
        /** 按集合名路由（shard-routing 配置） */
        COLLECTION,
        /** 整体迁移，collectionPrefix 匹配即路由，无需路由键 */
        NONE
    }

    var scatterQuery: ScatterQueryConfig = ScatterQueryConfig()
    var compensation: CompensationConfig = CompensationConfig()
    var podRegistry: PodRegistryConfig = PodRegistryConfig()

    class PodRegistryConfig {
        var enabled: Boolean = true
        var heartbeatIntervalMs: Long = 30_000
        /** 超过该秒数未心跳视为离线，不参与 100% Pod 门禁 */
        var staleSeconds: Long = 120
    }

    class CompensationConfig {
        /** 独立补偿队列存储 URI（§25.2.4）；空则 fallback-to-default */
        var storageUri: String = ""
        var fallbackToDefault: Boolean = true
        /** 队列深度软限制，超限告警（§3.17.9） */
        var softLimit: Int = 5000
        /** 队列深度硬限制，超限拒绝新主键入队（§3.17.9） */
        var hardLimit: Int = 10000
        var oplog: OplogCompensationConfig = OplogCompensationConfig()
    }

    class OplogCompensationConfig {
        var intervalMs: Long = 1000
    }

    class ScatterQueryConfig {
        var timeoutSeconds: Long = 5
        var mode: String = "STRICT"
        /** §3.19.2 P0 清单已验收项 ID（如 A1、B1），全量后 G-34 通过 */
        var completedReadinessItems: Set<String> = emptySet()
        /** G-43：散发查询专用连接池上限 */
        var dedicatedMaxPoolSize: Int = 10
        var dedicatedMinPoolSize: Int = 2
        /** G-37：sha256→projectId 缓存 TTL（分钟） */
        var sha256CacheTtlMinutes: Long = 30
        var sha256CacheMaxSize: Int = 10_000
    }
}