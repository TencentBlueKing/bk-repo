package com.tencent.bkrepo.opdata.service.mongo.migration

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.routing.CompensationHealthChecker
import com.tencent.bkrepo.common.mongo.routing.DualWriteSidecarVerifier
import com.tencent.bkrepo.common.mongo.routing.MigrationGate
import com.tencent.bkrepo.common.mongo.routing.MigrationInitValidator
import com.tencent.bkrepo.common.mongo.routing.NodeReconciliationHelper
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.model.TMigrationSyncState
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteCompensationService
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationBindingRequest
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationProjectRequest
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationStatusListResponse
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationStatusResponse
import com.tencent.bkrepo.opdata.config.client.ConfigClient
import com.tencent.bkrepo.opdata.routing.RoutingReadinessAggregator
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

@Service
class MongoMigrationService(
    private val registry: MongoRoutingRegistry,
    private val properties: MongoMultiInstanceProperties,
    private val syncStateDao: MigrationSyncStateDao,
    private val mongoTemplate: MongoTemplate,
    private val initValidator: MigrationInitValidator? = null,
    private val migrationGate: MigrationGate? = null,
    private val sidecarVerifier: DualWriteSidecarVerifier? = null,
    private val compensationService: MongoDualWriteCompensationService? = null,
    private val compensationHealthChecker: CompensationHealthChecker? = null,
    private val readinessAggregator: RoutingReadinessAggregator? = null,
    private val configClientProvider: ObjectProvider<ConfigClient>,
    @Value("\${spring.application.name:bkrepo-repository}")
    private val appName: String,
    @Value("\${spring.profiles.active:prod}")
    private val profile: String,
) {

    fun binding(request: MigrationBindingRequest) {
        assertNodeReadiness(request.ruleName)
        assertNoZombieOverdue(request.ruleName)
        if (!request.businessId.isNullOrBlank()) {
            bindBusinessGroup(request)
            return
        }
        bindSingleProject(request)
    }

    private fun bindSingleProject(request: MigrationBindingRequest) {
        val init = initValidator?.validate(request.ruleName, request.projectId)
        if (init != null && !init.passed) {
            syncStateDao.upsert(
                newState(request, MigrationPhase.INIT_FAILED, init.checks.joinToString(";") { it.reason.orEmpty() }),
            )
            throw badRequest("INIT validation failed: ${init.checks.filter { !it.passed }}")
        }
        syncStateDao.upsert(newState(request, MigrationPhase.PENDING))
        pushConsul(routingKey(request.ruleName, request.projectId), request.targetInstance)
        pushConsul(
            "spring.data.mongodb.multi-instance.rules.${request.ruleName}.migration.historical-sync-strategy",
            request.historicalSyncStrategy.name,
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
        pushConsul(
            "spring.data.mongodb.multi-instance.rules.${request.ruleName}.business-routing.$businessId",
            request.targetInstance,
        )
        for (projectId in projectIds) {
            syncStateDao.upsert(
                newState(request.copy(projectId = projectId), MigrationPhase.PENDING),
            )
            pushConsul(routingKey(request.ruleName, projectId), request.targetInstance)
        }
        pushConsul(
            "spring.data.mongodb.multi-instance.rules.${request.ruleName}.migration.historical-sync-strategy",
            request.historicalSyncStrategy.name,
        )
    }

    fun start(request: MigrationProjectRequest) {
        assertNodeReadiness(request.ruleName)
        assertNoZombieOverdue(request.ruleName)
        val state = requireState(request)
        if (state.phase != MigrationPhase.PENDING && state.phase != MigrationPhase.INIT_FAILED) {
            throw badRequest("Invalid phase for start: ${state.phase}")
        }
        val init = initValidator?.validate(request.ruleName, request.projectId)
        if (init != null && !init.passed) {
            syncStateDao.updatePhase(request.projectId, MigrationPhase.INIT_FAILED, init.checks.toString())
            throw badRequest("INIT validation failed")
        }
        val next = MigrationPhase.CS_START
        syncStateDao.updatePhase(request.projectId, next)
    }

    fun dumpComplete(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (state.phase != MigrationPhase.DUMPING) {
            throw badRequest("Project must be in DUMPING, current=${state.phase}")
        }
        syncStateDao.markDumpComplete(request.projectId)
    }

    fun ready(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (state.phase != MigrationPhase.VERIFY) {
            throw badRequest("Project must be in VERIFY, current=${state.phase}")
        }
        if (hasSyncFailures(request.projectId)) {
            throw badRequest("sync_failed queue not empty for project ${request.projectId}")
        }
        if (compensationService?.hasPendingTasks(request.ruleName, request.projectId) == true) {
            throw badRequest("Compensation queue not empty for project")
        }
        syncStateDao.updatePhase(request.projectId, MigrationPhase.READY)
    }

    fun enableDualWrite(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (phaseOrder(state.phase) < phaseOrder(MigrationPhase.READY)) {
            throw badRequest("Project not READY: ${state.phase}")
        }
        val gate = migrationGate ?: throw badRequest("MigrationGate not available")
        val pending = compensationService?.countPendingTasks(request.ruleName, request.projectId) ?: 0L
        val dualWriteCount = syncStateDao.findByRuleName(request.ruleName)
            .count { it.phase == MigrationPhase.DUAL_WRITE }
        val result = gate.canEnterDualWrite(
            compensationQueueEmpty = pending == 0L,
            sidecarPassed = true,
            currentDualWriteCount = dualWriteCount,
        )
        if (!result.passed) {
            throw badRequest("Dual-write gate blocked: ${result.failedChecks}")
        }
        val routingKey = routingKey(request.ruleName, request.projectId)
        pushConsul(routingKey, state.targetInstance)
        pushConsul(
            "spring.data.mongodb.multi-instance.rules.${request.ruleName}.dual-write",
            true,
        )
        bumpConfigVersion()
        syncStateDao.updatePhase(request.projectId, MigrationPhase.DUAL_WRITE)
    }

    fun route(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (state.phase != MigrationPhase.DUAL_WRITE) {
            throw badRequest("Project must be DUAL_WRITE, current=${state.phase}")
        }
        // 门禁 #1：sync_failed 必须清零
        if (hasSyncFailures(request.projectId)) {
            throw badRequest("Route gate blocked: sync_failed not empty for ${request.projectId}")
        }
        // 门禁 #6：模式二切流前复检 G-34 路由就绪
        if (request.ruleName == NODE_RULE) {
            assertNodeReadiness(request.ruleName)
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
        val otherDualWriteProjects = syncStateDao.findByRuleName(request.ruleName)
            .filter { it.projectId != request.projectId && it.phase == MigrationPhase.DUAL_WRITE }
        if (otherDualWriteProjects.isNotEmpty()) {
            throw badRequest(
                "Route gate blocked: other projects still in DUAL_WRITE: " +
                    otherDualWriteProjects.map { it.projectId },
            )
        }
        pushConsul("spring.data.mongodb.multi-instance.rules.${request.ruleName}.dual-write", false)
        bumpConfigVersion()
        syncStateDao.updatePhase(request.projectId, MigrationPhase.ROUTED)
    }

    fun cleanup(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (state.phase != MigrationPhase.ROUTED) {
            throw badRequest("Project must be ROUTED, current=${state.phase}")
        }
        syncStateDao.updatePhase(request.projectId, MigrationPhase.CLEANUP_READY)
        // 模式一（集合族迁移）：直接清理 Default 月集合，避免异步 Job 等待，完成后置 CLEANED
        // 模式二（node 路由）：由 NodeProjectSyncJob 异步清理 Default 上 projectId 数据并置 CLEANED
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
        val routingKey = routingKey(request.ruleName, request.projectId)
        pushConsul(routingKey, null)
        pushConsul("spring.data.mongodb.multi-instance.rules.${request.ruleName}.dual-write", false)
        bumpConfigVersion()
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

    private fun assertNodeReadiness(ruleName: String) {
        if (ruleName != NODE_RULE) return
        val ready = readinessAggregator?.aggregate()?.ready == true
        if (!ready) {
            throw conflict("ROUTING_READINESS_BLOCKED: G-34 not passed. " +
                "Check GET /routing/readiness for details")
        }
    }

    private fun routingKey(ruleName: String, projectId: String) =
        "spring.data.mongodb.multi-instance.rules.$ruleName.project-routing.$projectId"

    private fun pushConsul(key: String, value: Any?) {
        val client = configClientProvider.ifAvailable
            ?: throw badRequest("ConfigClient not configured")
        client.put(key, value, appName, profile)
    }

    private fun bumpConfigVersion() {
        val next = properties.configVersion + 1
        pushConsul("spring.data.mongodb.multi-instance.config-version", next)
    }

    private fun TMigrationSyncState.toResponse(): MigrationStatusResponse {
        val lagPhases = setOf(MigrationPhase.CATCH_UP, MigrationPhase.VERIFY)
        val catchUpLag = if (phase in lagPhases) {
            NodeReconciliationHelper.catchUpLagSeconds(mongoTemplate, lastEventClusterTimeSecs)
        } else {
            null
        }
        val pending = compensationService?.countPendingTasks(ruleName, projectId)
        return MigrationStatusResponse(
            projectId = projectId,
            ruleName = ruleName,
            phase = phase,
            targetInstance = targetInstance,
            lastError = lastError,
            updatedAt = updatedAt?.toString(),
            catchUpLagSeconds = catchUpLag,
            syncFailedCount = countSyncFailed(projectId),
            compensationPendingCount = pending,
        )
    }

    /** sync_failed 队列是否已清零（§9.5：清零后方可 READY） */
    private fun hasSyncFailures(projectId: String): Boolean =
        countSyncFailed(projectId) > 0L

    private fun countSyncFailed(projectId: String): Long = runCatching {
        mongoTemplate.count(
            Query(Criteria.where("projectId").`is`(projectId)),
            SYNC_FAILED_COLLECTION,
        )
    }.getOrDefault(0L)

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
        val strategy = properties.rules?.get(ruleName)?.migration?.historicalSyncStrategy ?: "NONE"
        if (strategy.equals("NONE", ignoreCase = true)) return
        val prefix = properties.rules?.get(ruleName)?.collectionPrefix?.takeIf { it.isNotBlank() } ?: return
        val targetInstance = registry.allPrimaryTemplates(ruleName).keys.firstOrNull() ?: return
        val targetTemplate = registry.primaryTemplateByInstance(ruleName, targetInstance) ?: return
        val state = syncStateDao.findByProjectId(ruleName) ?: run {
            syncStateDao.upsert(
                TMigrationSyncState(
                    id = ruleName, projectId = ruleName, ruleName = ruleName,
                    targetInstance = targetInstance, phase = MigrationPhase.JOB_FULL,
                ),
            )
            syncStateDao.findByProjectId(ruleName)!!
        }
        if (state.phase == MigrationPhase.READY ||
            state.phase == MigrationPhase.DUAL_WRITE ||
            state.phase == MigrationPhase.ROUTED ||
            state.phase == MigrationPhase.CLEANED
        ) {
            return
        }
        val collections = mongoTemplate.db.listCollectionNames()
            .asSequence()
            .filter { it.startsWith(prefix) }
            .sorted()
            .toList()
        if (collections.isEmpty()) return
        val startIdx = state.lastSyncedId?.let { collections.indexOf(it).coerceAtLeast(0) } ?: 0
        for (col in collections.drop(startIdx)) {
            syncCollectionToTarget(col, targetTemplate)
            syncStateDao.upsert(
                state.copy(
                    lastSyncedId = col,
                    phase = MigrationPhase.JOB_FULL,
                    updatedAt = LocalDateTime.now(),
                ),
            )
        }
        syncStateDao.updatePhase(ruleName, MigrationPhase.VERIFY)
        logger.info("Oplog historical sync: full sync done for rule[$ruleName] → VERIFY")
    }

    private fun syncCollectionToTarget(collectionName: String, targetTemplate: MongoTemplate) {
        var pageLastId = ObjectId("000000000000000000000000")
        var batchSize: Int
        do {
            val query = Query(Criteria.where(ID).gt(pageLastId))
                .limit(HISTORICAL_SYNC_BATCH_SIZE)
                .with(Sort.by(ID).ascending())
            val docs = mongoTemplate.find(query, Document::class.java, collectionName)
            batchSize = docs.size
            if (docs.isEmpty()) break
            docs.forEach { doc ->
                val docId = doc["_id"] as ObjectId
                val upsertQuery = Query(Criteria.where(ID).`is`(docId))
                val update = Update().also { upd ->
                    doc.entries.forEach { (k, v) -> if (k != "_id") upd.set(k, v) }
                }
                targetTemplate.upsert(upsertQuery, update, collectionName)
            }
            pageLastId = docs.last()["_id"] as ObjectId
        } while (batchSize == HISTORICAL_SYNC_BATCH_SIZE)
    }

    companion object {
        private const val NODE_RULE = "node"
        private const val SYNC_FAILED_COLLECTION = "node_project_sync_failed"
        private const val HISTORICAL_SYNC_BATCH_SIZE = 500
        private val logger = LoggerFactory.getLogger(MongoMigrationService::class.java)

        private fun phaseOrder(phase: MigrationPhase): Int = when (phase) {
            MigrationPhase.PENDING, MigrationPhase.INIT_FAILED -> 0
            MigrationPhase.CS_START -> 1
            MigrationPhase.DUMPING -> 2
            MigrationPhase.JOB_GAP -> 3
            MigrationPhase.JOB_FULL -> 4
            MigrationPhase.CATCH_UP -> 5
            MigrationPhase.VERIFY -> 6
            MigrationPhase.READY -> 7
            MigrationPhase.DUAL_WRITE -> 8
            MigrationPhase.ROUTED -> 9
            MigrationPhase.CLEANUP_READY -> 10
            MigrationPhase.CLEANED -> 11
            MigrationPhase.ROLLBACK, MigrationPhase.REBUILD_REQUIRED -> -1
        }
    }
}