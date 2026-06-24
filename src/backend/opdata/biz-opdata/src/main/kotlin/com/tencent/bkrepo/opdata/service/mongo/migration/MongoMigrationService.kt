package com.tencent.bkrepo.opdata.service.mongo.migration

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.mongo.api.routing.HistoricalSyncStrategy
import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessChecker
import com.tencent.bkrepo.common.mongo.routing.CompensationHealthChecker
import com.tencent.bkrepo.common.mongo.routing.DiskUsageGuard
import com.tencent.bkrepo.common.mongo.routing.DualWriteSidecarVerifier
import com.tencent.bkrepo.common.mongo.routing.MigrationGate
import com.tencent.bkrepo.common.mongo.routing.MigrationInitValidator
import com.tencent.bkrepo.common.mongo.routing.MigrationSyncStateRepository
import com.tencent.bkrepo.common.mongo.routing.MongoDualWriteCompensationService
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationBindingRequest
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationProjectRequest
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationStatusListResponse
import com.tencent.bkrepo.opdata.api.mongo.migration.MigrationStatusResponse
import com.tencent.bkrepo.opdata.config.client.ConfigClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class MongoMigrationService(
    private val registry: MongoRoutingRegistry,
    private val properties: MongoMultiInstanceProperties,
    private val syncStateRepository: MigrationSyncStateRepository,
    @Autowired(required = false)
    private val initValidator: MigrationInitValidator? = null,
    @Autowired(required = false)
    private val diskUsageGuard: DiskUsageGuard? = null,
    @Autowired(required = false)
    private val migrationGate: MigrationGate? = null,
    @Autowired(required = false)
    private val sidecarVerifier: DualWriteSidecarVerifier? = null,
    @Autowired(required = false)
    private val compensationService: MongoDualWriteCompensationService? = null,
    @Autowired(required = false)
    private val compensationHealthChecker: CompensationHealthChecker? = null,
    @Autowired(required = false)
    private val readinessChecker: RoutingReadinessChecker? = null,
    private val configClientProvider: ObjectProvider<ConfigClient>,
    @Value("\${spring.application.name:bkrepo-repository}")
    private val appName: String,
    @Value("\${spring.profiles.active:prod}")
    private val profile: String,
) {

    fun binding(request: MigrationBindingRequest) {
        assertNodeReadiness(request.ruleName)
        assertNoZombieOverdue(request.ruleName)
        if (diskUsageGuard?.blocksMigration(request.ruleName) == true) {
            throw badRequest("Heavy disk usage >= 85%, migration blocked")
        }
        if (!request.businessId.isNullOrBlank()) {
            bindBusinessGroup(request)
            return
        }
        bindSingleProject(request)
    }

    private fun bindSingleProject(request: MigrationBindingRequest) {
        val init = initValidator?.validate(request.ruleName, request.projectId)
        if (init != null && !init.passed) {
            syncStateRepository.upsert(
                newState(request, MigrationPhase.INIT_FAILED, init.checks.joinToString(";") { it.reason.orEmpty() }),
            )
            throw badRequest("INIT validation failed: ${init.checks.filter { !it.passed }}")
        }
        syncStateRepository.upsert(newState(request, MigrationPhase.PENDING))
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
            syncStateRepository.upsert(
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
            syncStateRepository.updatePhase(request.projectId, MigrationPhase.INIT_FAILED, init.checks.toString())
            throw badRequest("INIT validation failed")
        }
        val next = MigrationPhase.CS_START
        syncStateRepository.updatePhase(request.projectId, next)
    }

    fun dumpComplete(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (state.phase != MigrationPhase.DUMPING) {
            throw badRequest("Project must be in DUMPING, current=${state.phase}")
        }
        syncStateRepository.markDumpComplete(request.projectId)
    }

    fun ready(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (state.phase != MigrationPhase.VERIFY) {
            throw badRequest("Project must be in VERIFY, current=${state.phase}")
        }
        if (compensationService?.hasPendingTasks(request.ruleName, request.projectId) == true) {
            throw badRequest("Compensation queue not empty for project")
        }
        syncStateRepository.updatePhase(request.projectId, MigrationPhase.READY)
    }

    fun enableDualWrite(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (phaseOrder(state.phase) < phaseOrder(MigrationPhase.READY)) {
            throw badRequest("Project not READY: ${state.phase}")
        }
        val gate = migrationGate ?: throw badRequest("MigrationGate not available")
        val pending = compensationService?.countPendingTasks(request.ruleName, request.projectId) ?: 0L
        val sidecarOk = sidecarVerifier?.isRecentVerificationPassed(request.projectId) ?: true
        val dualWriteCount = syncStateRepository.findByRuleName(request.ruleName)
            .count { it.phase == MigrationPhase.DUAL_WRITE }
        val result = gate.canEnterDualWrite(
            compensationQueueEmpty = pending == 0L,
            sidecarPassed = sidecarOk,
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
        syncStateRepository.updatePhase(request.projectId, MigrationPhase.DUAL_WRITE)
    }

    fun route(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (state.phase != MigrationPhase.DUAL_WRITE) {
            throw badRequest("Project must be DUAL_WRITE, current=${state.phase}")
        }
        val gate = migrationGate ?: throw badRequest("MigrationGate not available")
        val pending = compensationService?.countPendingTasks(request.ruleName, request.projectId) ?: 0L
        val sidecarOk = sidecarVerifier?.isRecentVerificationPassed(request.projectId) ?: true
        val result = gate.canSwitchToRouted(
            compensationQueueEmpty = pending == 0L,
            sidecarPassed = sidecarOk,
        )
        if (!result.passed) {
            throw badRequest("Route gate blocked: ${result.failedChecks}")
        }
        pushConsul("spring.data.mongodb.multi-instance.rules.${request.ruleName}.dual-write", false)
        bumpConfigVersion()
        syncStateRepository.updatePhase(request.projectId, MigrationPhase.ROUTED)
    }

    fun cleanup(request: MigrationProjectRequest) {
        val state = requireState(request)
        if (state.phase != MigrationPhase.ROUTED) {
            throw badRequest("Project must be ROUTED, current=${state.phase}")
        }
        syncStateRepository.updatePhase(request.projectId, MigrationPhase.CLEANED)
    }

    fun rollback(request: MigrationProjectRequest) {
        val routingKey = routingKey(request.ruleName, request.projectId)
        pushConsul(routingKey, null)
        pushConsul("spring.data.mongodb.multi-instance.rules.${request.ruleName}.dual-write", false)
        bumpConfigVersion()
        compensationService?.deletePendingByRoutingKey(request.ruleName, request.projectId)
        syncStateRepository.updatePhase(request.projectId, MigrationPhase.ROLLBACK)
    }

    fun status(ruleName: String, projectId: String?): MigrationStatusListResponse {
        val states = if (projectId != null) {
            listOfNotNull(syncStateRepository.findByProjectId(projectId))
        } else {
            syncStateRepository.findByRuleName(ruleName)
        }
        return MigrationStatusListResponse(states.map { it.toResponse() })
    }

    fun readiness() = readinessChecker?.check()
        ?: throw badRequest("RoutingReadinessChecker not available")

    fun compensationStats(ruleName: String) =
        compensationHealthChecker?.stats(ruleName)
            ?: throw badRequest("CompensationHealthChecker not available")

    fun compensationHealth(ruleName: String) =
        compensationHealthChecker?.check(ruleName)
            ?: throw badRequest("CompensationHealthChecker not available")

    private fun badRequest(message: String): Nothing =
        throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, message)

    private fun requireState(request: MigrationProjectRequest) =
        syncStateRepository.findByProjectId(request.projectId)
            ?: throw badRequest("No migration state for project ${request.projectId}")

    private fun newState(
        request: MigrationBindingRequest,
        phase: MigrationPhase,
        error: String? = null,
    ) = MigrationSyncStateRepository.MigrationSyncStateDoc(
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
        val ready = readinessChecker?.check()?.ready == true
        if (!ready) {
            throw badRequest("ROUTING_READINESS_BLOCKED: G-34 not passed")
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

    private fun MigrationSyncStateRepository.MigrationSyncStateDoc.toResponse() =
        MigrationStatusResponse(
            projectId = projectId,
            ruleName = ruleName,
            phase = phase,
            targetInstance = targetInstance,
            lastError = lastError,
            updatedAt = updatedAt?.toString(),
        )

    companion object {
        private const val NODE_RULE = "node"

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
