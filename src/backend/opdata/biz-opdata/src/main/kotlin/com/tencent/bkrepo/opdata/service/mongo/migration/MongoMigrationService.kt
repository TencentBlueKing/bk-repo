package com.tencent.bkrepo.opdata.service.mongo.migration

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.dao.RoutingConfigDao
import com.tencent.bkrepo.common.mongo.model.TMigrationSyncState
import com.tencent.bkrepo.common.mongo.routing.CompensationHealthChecker
import com.tencent.bkrepo.common.mongo.routing.DualWriteSidecarVerifier
import com.tencent.bkrepo.common.mongo.routing.MigrationGate
import com.tencent.bkrepo.common.mongo.routing.MigrationInitValidator
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteCompensationService
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationBindingRequest
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationProjectRequest
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationStatusListResponse
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationStatusResponse
import com.tencent.bkrepo.opdata.api.mongo.migration.RoutingConfigRequest
import com.tencent.bkrepo.opdata.api.mongo.migration.RoutingConfigResponse
import com.tencent.bkrepo.opdata.routing.RoutingReadinessAggregator
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class MongoMigrationService(
    private val registry: MongoRoutingRegistry,
    private val properties: MongoMultiInstanceProperties,
    private val syncStateDao: MigrationSyncStateDao,
    private val routingConfigDao: RoutingConfigDao,
    private val mongoTemplate: MongoTemplate,
    private val initValidator: MigrationInitValidator? = null,
    private val migrationGate: MigrationGate? = null,
    private val sidecarVerifier: DualWriteSidecarVerifier? = null,
    private val compensationService: MongoDualWriteCompensationService? = null,
    private val compensationHealthChecker: CompensationHealthChecker? = null,
    private val readinessAggregator: RoutingReadinessAggregator? = null,
) {

    fun binding(request: MigrationBindingRequest) {
        assertProjectRuleReadiness(request.ruleName)
        assertNoZombieOverdue(request.ruleName)
        if (request.ruleName in PROJECT_ROUTING_RULES) {
            assertBlockNodeBindingConsistency(request.projectId, request.targetInstance)
        }
        if (!request.businessId.isNullOrBlank()) {
            bindBusinessGroup(request)
            return
        }
        bindSingleProject(request)
    }

    private fun bindSingleProject(request: MigrationBindingRequest) {
        if (request.ruleName in registry.listOffloadRuleNames()) {
            bindOffloadRule(request)
            return
        }
        val init = initValidator?.validate(request.ruleName, request.projectId)
        if (init != null && !init.passed) {
            syncStateDao.upsert(
                newState(request, MigrationPhase.INIT_FAILED, init.checks.joinToString(";") { it.reason.orEmpty() }),
            )
            throw badRequest("INIT validation failed: ${init.checks.filter { !it.passed }}")
        }
        syncStateDao.upsert(newState(request, MigrationPhase.PENDING))
    }

    private fun bindOffloadRule(request: MigrationBindingRequest) {
        val ruleName = request.ruleName
        syncStateDao.upsert(
            newState(request.copy(projectId = ruleName), MigrationPhase.PENDING).copy(
                id = ruleName,
                projectId = ruleName,
            ),
        )
    }

    private fun bindBusinessGroup(request: MigrationBindingRequest) {
        val businessId = request.businessId!!
        val projectIds = request.groupProjectIds?.filter { it.isNotBlank() }.orEmpty()
        if (projectIds.isEmpty()) {
            throw badRequest("groupProjectIds required when businessId is set")
        }
        for (projectId in projectIds) {
            val init = initValidator?.validate(request.ruleName, projectId)
            if (init != null && !init.passed) {
                throw badRequest("INIT validation failed for $projectId: ${init.checks.filter { !it.passed }}")
            }
        }
        for (projectId in projectIds) {
            syncStateDao.upsert(
                newState(request.copy(projectId = projectId), MigrationPhase.PENDING),
            )
        }
    }

    fun start(request: MigrationProjectRequest) {
        assertProjectRuleReadiness(request.ruleName)
        assertNoZombieOverdue(request.ruleName)
        if (request.ruleName in registry.listOffloadRuleNames()) {
            startOffloadRule(request)
            return
        }
        val state = requireState(request)
        if (state.phase != MigrationPhase.PENDING && state.phase != MigrationPhase.INIT_FAILED) {
            throw badRequest("Invalid phase for start: ${state.phase}")
        }
        assertConcurrentDualWriteNotExceeded(request)
        val init = initValidator?.validate(request.ruleName, request.projectId)
        if (init != null && !init.passed) {
            syncStateDao.updatePhase(request.projectId, MigrationPhase.INIT_FAILED, init.checks.toString())
            throw badRequest("INIT validation failed")
        }
        syncStateDao.updatePhase(request.projectId, MigrationPhase.INITIAL_SYNC)
    }

    private fun startOffloadRule(request: MigrationProjectRequest) {
        val ruleName = request.ruleName
        val state = syncStateDao.findByProjectId(ruleName)
            ?: throw badRequest("No migration state for offload rule $ruleName")
        if (state.phase != MigrationPhase.PENDING && state.phase != MigrationPhase.INIT_FAILED) {
            throw badRequest("Invalid phase for start: ${state.phase}")
        }
        assertConcurrentDualWriteNotExceeded(
            MigrationProjectRequest(ruleName = ruleName, projectId = ruleName),
        )
        syncStateDao.updatePhase(ruleName, MigrationPhase.INITIAL_SYNC)
    }

    fun route(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (state.phase != MigrationPhase.DUAL_WRITE) {
            throw badRequest("Project must be DUAL_WRITE, current=${state.phase}")
        }
        // 门禁 #1：sync_failed 必须清零
        if (hasSyncFailures(request.ruleName, request.projectId)) {
            throw badRequest("Route gate blocked: sync_failed not empty for ${request.projectId}")
        }
        // 门禁 #6：模式二切流前复检 G-34；G-39 node/block-node 绑定一致
        if (request.ruleName in PROJECT_ROUTING_RULES) {
            assertProjectRuleReadiness(request.ruleName)
            assertBlockNodeBindingConsistency(request.projectId)
        }
        // 主动触发一次对账，确保门禁检查数据新鲜（而非依赖历史缓存）
        verifyProject(request.ruleName, request.projectId)
        val gate = migrationGate ?: throw badRequest("MigrationGate not available")
        val pending = compensationService?.countPendingTasks(request.ruleName, request.projectId) ?: 0L
        // 门禁 #3：旁路对账连续 3 轮 pass（spec §25.3.2 E-05）
        val sidecarOk = sidecarVerifier?.isRecentVerificationPassed(request.projectId) ?: true
        val result = gate.canSwitchToRouted(
            compensationQueueEmpty = pending == 0L,
            sidecarPassed = sidecarOk,
        )
        if (!result.passed) {
            throw badRequest("Route gate blocked: ${result.failedChecks}")
        }
        // 门禁 §3.5.1：切流前同 rule 下无其他项目处于 INITIAL_SYNC 或 DUAL_WRITE
        val otherMigratingProjects = syncStateDao.findByRuleName(request.ruleName)
            .filter { it.projectId != request.projectId && it.phase in CONCURRENT_DUAL_WRITE_PHASES }
        if (otherMigratingProjects.isNotEmpty()) {
            throw badRequest(
                "Route gate blocked: other projects still migrating (INITIAL_SYNC/DUAL_WRITE): " +
                    otherMigratingProjects.map { it.projectId },
            )
        }
        syncStateDao.updatePhase(request.projectId, MigrationPhase.ROUTED)
    }

    fun cleanup(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (state.phase != MigrationPhase.ROUTED) {
            throw badRequest("Project must be ROUTED, current=${state.phase}")
        }
        syncStateDao.updatePhase(request.projectId, MigrationPhase.CLEANUP_READY)
        // 模式一（集合族迁移）：直接清理 Default 月集合，避免异步 Job 等待，完成后置 CLEANED
        // 模式二（node / block-node 路由）：由 *ProjectSyncJob 异步清理 Default 上 projectId 数据并置 CLEANED
        if (request.ruleName in registry.listOffloadRuleNames()) {
            cleanupDefaultOplogCollections(request)
        }
    }

    /**
     * 模式一集合族迁移：清理 Default 上已迁出的月集合（如 artifact_oplog_*），完成后置 CLEANED。
     */
    private fun cleanupDefaultOplogCollections(request: MigrationProjectRequest) {
        if (!registry.isRoutingEnabled(request.ruleName)) return
        val prefix = properties.rules?.get(request.ruleName)?.collectionPrefix?.takeIf { it.isNotBlank() } ?: return
        val collections = mongoTemplate.db.listCollectionNames()
            .asSequence()
            .filter { it.startsWith(prefix) }
            .toList()
        if (collections.isEmpty()) {
            syncStateDao.updatePhase(request.projectId, MigrationPhase.CLEANED)
            return
        }
        collections.forEach { col ->
            mongoTemplate.dropCollection(col)
            logger.info("CLEANUP dropped Default collection [$col] for rule [${request.ruleName}]")
        }
        syncStateDao.updatePhase(request.projectId, MigrationPhase.CLEANED)
    }

    fun rollback(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (state.phase == MigrationPhase.CLEANED) {
            throw conflict("Default data already cleaned, manual full reverse dump required")
        }
        compensationService?.deletePendingByRoutingKey(request.ruleName, request.projectId)
        syncStateDao.updatePhase(request.projectId, MigrationPhase.ROLLBACK)
    }

    fun status(ruleName: String, projectId: String?): MigrationStatusListResponse {
        val states = if (projectId != null) {
            listOfNotNull(syncStateDao.findByProjectId(projectId))
        } else {
            syncStateDao.findByRuleName(ruleName)
        }
        return MigrationStatusListResponse(states.map { it.toResponse() })
    }

    fun readiness() = readinessAggregator?.aggregate()
        ?: throw badRequest("RoutingReadinessAggregator not available")

    fun compensationStats(ruleName: String) =
        compensationHealthChecker?.stats(ruleName)
            ?: throw badRequest("CompensationHealthChecker not available")

    fun compensationHealth(ruleName: String) =
        compensationHealthChecker?.check(ruleName)
            ?: throw badRequest("CompensationHealthChecker not available")

    fun getConfig(): RoutingConfigResponse {
        val cfg = routingConfigDao.get()
        return RoutingConfigResponse(
            maxConcurrentDualWrite = cfg.maxConcurrentDualWrite,
            freezeDdl = cfg.freezeDdl,
            configVersion = cfg.configVersion,
        )
    }

    fun updateConfig(request: RoutingConfigRequest): RoutingConfigResponse {
        val current = routingConfigDao.get()
        val updated = current.copy(
            maxConcurrentDualWrite = request.maxConcurrentDualWrite ?: current.maxConcurrentDualWrite,
            freezeDdl = request.freezeDdl ?: current.freezeDdl,
            configVersion = current.configVersion + 1,
        )
        routingConfigDao.save(updated)
        return getConfig()
    }

    fun verifyAll() {
        sidecarVerifier?.verify() ?: throw badRequest("SidecarVerifier not available")
    }

    fun verifyProject(ruleName: String, projectId: String) {
        val verifier = sidecarVerifier ?: throw badRequest("SidecarVerifier not available")
        val projectsByInstance = registry.allConfiguredProjectsByInstance(ruleName)
        for ((instanceName, projects) in projectsByInstance) {
            if (projectId in projects) {
                val heavyTemplate = registry.primaryTemplateByInstance(ruleName, instanceName)
                    ?: throw badRequest("No template for instance $instanceName")
                verifier.verifySingle(projectId, heavyTemplate)
                return
            }
        }
        throw badRequest("Project $projectId not found in any configured instance for rule $ruleName")
    }

    private fun badRequest(message: String): Nothing =
        throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, message)

    private fun conflict(message: String): Nothing =
        throw ErrorCodeException(
            CommonMessageCode.PARAMETER_INVALID,
            message,
            status = com.tencent.bkrepo.common.api.constant.HttpStatus.CONFLICT,
        )

    private fun requireState(request: MigrationProjectRequest) =
        syncStateDao.findByProjectId(request.projectId)
            ?: throw badRequest("No migration state for project ${request.projectId}")

    private fun newState(
        request: MigrationBindingRequest,
        phase: MigrationPhase,
        error: String? = null,
    ) = TMigrationSyncState(
        id = request.projectId,
        projectId = request.projectId,
        ruleName = request.ruleName,
        targetInstance = request.targetInstance,
        phase = phase,
        lastError = error,
        strategy = request.historicalSyncStrategy.name,
        updatedAt = LocalDateTime.now(),
    )

    private fun assertNoZombieOverdue(ruleName: String) {
        val gate = migrationGate ?: return
        val overdue = registry.getRoutedProjectIds(ruleName).filter {
            gate.isZombieReplicaOverdue(ruleName, it)
        }
        if (overdue.isNotEmpty()) {
            throw badRequest("ZOMBIE_OVERDUE: projects $overdue exceed max-zombie-hours, cleanup required")
        }
    }

    /**
     * §3.5.1 / §3.20 DB Gate：同时处于 INITIAL_SYNC / DUAL_WRITE 的项目数不得超过 max-concurrent-dual-write。
     */
    private fun assertConcurrentDualWriteNotExceeded(request: MigrationProjectRequest) {
        val limit = routingConfigDao.get().maxConcurrentDualWrite
        val concurrent = syncStateDao.findByRuleName(request.ruleName)
            .count { it.projectId != request.projectId && it.phase in CONCURRENT_DUAL_WRITE_PHASES }
        if (concurrent >= limit) {
            throw badRequest(
                "max-concurrent-dual-write exceeded: $concurrent already in INITIAL_SYNC/DUAL_WRITE, limit=$limit",
            )
        }
    }

    private fun assertProjectRuleReadiness(ruleName: String) {
        if (ruleName !in PROJECT_ROUTING_RULES) return
        val ready = readinessAggregator?.aggregate()?.ready == true
        if (!ready) {
            throw conflict("ROUTING_READINESS_BLOCKED: G-34 not passed. " +
                "Check GET /routing/readiness for details")
        }
    }

    /** G-39：node 与 block-node 的 project-routing 须指向同一 Heavy 实例。 */
    private fun assertBlockNodeBindingConsistency(projectId: String, expectedInstance: String? = null) {
        if (!registry.isRoutingEnabled(BLOCK_NODE_RULE) && !registry.isRoutingEnabled(NODE_RULE)) return
        val nodeInstance = properties.rules?.get(NODE_RULE)?.projectRouting?.get(projectId)
        val blockInstance = properties.rules?.get(BLOCK_NODE_RULE)?.projectRouting?.get(projectId)
        if (nodeInstance != null && blockInstance != null && nodeInstance != blockInstance) {
            throw badRequest(
                "G-39: project '$projectId' node→$nodeInstance but block-node→$blockInstance",
            )
        }
        expectedInstance?.let { expected ->
            if (nodeInstance != null && nodeInstance != expected) {
                throw badRequest(
                    "G-39: project '$projectId' node→$nodeInstance but binding target is $expected",
                )
            }
            if (blockInstance != null && blockInstance != expected) {
                throw badRequest(
                    "G-39: project '$projectId' block-node→$blockInstance but binding target is $expected",
                )
            }
        }
    }

    private fun TMigrationSyncState.toResponse(): MigrationStatusResponse {
        val pending = compensationService?.countPendingTasks(ruleName, projectId)
        return MigrationStatusResponse(
            projectId = projectId,
            ruleName = ruleName,
            phase = phase,
            targetInstance = targetInstance,
            lastError = lastError,
            updatedAt = updatedAt?.toString(),
            syncFailedCount = countSyncFailed(ruleName, projectId),
            compensationPendingCount = pending,
        )
    }

    /** sync_failed 队列是否已清零（§9.5：清零后方可推进到 DUAL_WRITE） */
    private fun hasSyncFailures(ruleName: String, projectId: String): Boolean =
        countSyncFailed(ruleName, projectId) > 0L

    private fun countSyncFailed(ruleName: String, projectId: String): Long = runCatching {
        mongoTemplate.count(
            Query(Criteria.where("projectId").`is`(projectId)),
            syncFailedCollection(ruleName),
        )
    }.getOrDefault(0L)

    private fun syncFailedCollection(ruleName: String): String = when (ruleName) {
        BLOCK_NODE_RULE -> BLOCK_NODE_SYNC_FAILED_COLLECTION
        in registry.listOffloadRuleNames() -> OPLOG_SYNC_FAILED_COLLECTION
        else -> NODE_SYNC_FAILED_COLLECTION
    }

    /**
     * ponytail: 手动触发全部 Offload 规则的历史数据同步（替代原 @Scheduled 定时轮询）。
     */
    fun syncHistoricalData() {
        registry.listOffloadRuleNames().forEach { ruleName -> syncHistoricalDataByRule(ruleName) }
    }

    fun syncHistoricalData(ruleName: String) {
        if (ruleName !in registry.listOffloadRuleNames()) {
            throw badRequest("Unknown or non-offload rule: $ruleName")
        }
        syncHistoricalDataByRule(ruleName)
    }

    private fun syncHistoricalDataByRule(ruleName: String) {
        if (!registry.historicalSyncStrategy(ruleName).equals("JOB_ONLY", ignoreCase = true)) return
        val targetInstance = registry.allPrimaryTemplates(ruleName).keys.firstOrNull() ?: return
        val existing = syncStateDao.findByProjectId(ruleName)
        if (existing?.phase in SKIP_HISTORICAL_SYNC_PHASES) return
        syncStateDao.upsert(
            TMigrationSyncState(
                id = ruleName,
                projectId = ruleName,
                ruleName = ruleName,
                targetInstance = existing?.targetInstance ?: targetInstance,
                phase = MigrationPhase.INITIAL_SYNC,
                currentShardIdx = existing?.currentShardIdx ?: 0,
                lastSyncedId = existing?.lastSyncedId,
                syncCycleCount = existing?.syncCycleCount ?: 0,
                strategy = "JOB_ONLY",
                updatedAt = LocalDateTime.now(),
            ),
        )
        logger.info("Offload rule[$ruleName] enqueued INITIAL_SYNC for MigrationSyncJob")
    }

    companion object {
        /** §3.5.1：计入 max-concurrent-dual-write 的迁移中阶段 */
        private val CONCURRENT_DUAL_WRITE_PHASES =
            setOf(MigrationPhase.INITIAL_SYNC, MigrationPhase.DUAL_WRITE)
        private val SKIP_HISTORICAL_SYNC_PHASES = setOf(
            MigrationPhase.DUAL_WRITE,
            MigrationPhase.ROUTED,
            MigrationPhase.CLEANED,
        )
        private const val NODE_RULE = "node"
        private const val BLOCK_NODE_RULE = "block-node"
        private val PROJECT_ROUTING_RULES = setOf(NODE_RULE, BLOCK_NODE_RULE)
        private const val NODE_SYNC_FAILED_COLLECTION = "node_project_sync_failed"
        private const val BLOCK_NODE_SYNC_FAILED_COLLECTION = "block_node_project_sync_failed"
        private const val OPLOG_SYNC_FAILED_COLLECTION = "oplog_sync_failed"
        private val logger = LoggerFactory.getLogger(MongoMigrationService::class.java)
    }
}