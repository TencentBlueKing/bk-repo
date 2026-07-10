package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import java.time.Duration
import java.time.LocalDateTime

/**
 * 迁移门控（§3.10 / §25.3.2）。
 *
 * 在所有迁移状态跃迁前执行前置条件校验，任一不满足则拒绝跃迁。
 *
 * 门控校验链：
 * - ROUTED 前：补偿队列清零 + 旁路对账连续3轮零差异
 * - 100% Pod 滚动完成：运维 SOP（`kubectl rollout status`），不在此自动校验
 * - 迁移期 freeze-gc / freeze-physical-delete：`isProjectInDualWrite` + DB `phase`（§3.18.2/3.18.3）
 *
 * 从 @Component 改为 MongoMultiInstanceConfiguration 中的显式 @Bean，
 * 因为 common-mongo 包的 @ComponentScan 不会覆盖到 Job 等非 web 模块。
 */
class MigrationGate(
    private val registry: MongoRoutingRegistry,
    private val properties: MongoMultiInstanceProperties,
    private val syncStateDao: MigrationSyncStateDao,
) {

    /**
     * 是否允许切换到 ROUTED（关闭双写，单写 Heavy）。
     */
    fun canSwitchToRouted(
        compensationQueueEmpty: Boolean,
        sidecarPassed: Boolean,
    ): GateResult {
        val checks = mutableListOf<GateCheck>()
        checks += GateCheck("compensationQueueEmpty", compensationQueueEmpty,
            if (!compensationQueueEmpty) "补偿队列未清零，阻塞切流到 ROUTED" else null)
        checks += GateCheck("sidecarPassed", sidecarPassed,
            if (!sidecarPassed) "旁路对账未通过（需连续 3 轮零差异），阻塞切流到 ROUTED" else null)
        return GateResult(checks)
    }

    /**
     * 迁移期间 file_reference GC 是否应全局暂停（§3.18.2）。
     * 任一 project-routing 内项目处于迁移冻结阶段时返回 true。
     */
    fun isGcFrozen(): Boolean =
        properties.rules.any { (ruleName, rule) ->
            rule.migration.projectLocks.freezeGc &&
                rule.projectRouting.keys.any { projectId ->
                    isProjectGcFrozen(ruleName, projectId)
                }
        }

    /**
     * 判断指定项目的 file_reference GC 是否应暂停。
     */
    fun isProjectGcFrozen(projectId: String): Boolean =
        properties.rules.any { (ruleName, rule) ->
            projectId in rule.projectRouting &&
                rule.migration.projectLocks.freezeGc &&
                isProjectGcFrozen(ruleName, projectId)
        }

    fun isPhysicalDeleteFrozen(projectId: String): Boolean =
        properties.rules.any { (ruleName, rule) ->
            projectId in rule.projectRouting &&
                rule.migration.projectLocks.freezePhysicalDelete &&
                isPhysicalDeleteFrozen(ruleName, projectId)
        }

    /** G-17：ROUTED 后僵尸副本超时，阻断后续迁移编排 */
    fun isZombieReplicaOverdue(ruleName: String, projectId: String): Boolean {
        val rule = properties.rules[ruleName] ?: return false
        val state = syncStateDao.findByRuleAndProject(ruleName, projectId) ?: return false
        if (state.phase != MigrationPhase.ROUTED && state.phase != MigrationPhase.CLEANUP_READY) {
            return false
        }
        val maxHours = rule.migration.maxZombieHours.toLong()
        return Duration.between(state.updatedAt, LocalDateTime.now()).toHours() >= maxHours
    }

    // ─── private ────────────────────────────────────────────────

    // spec §3.18.2：INITIAL_SYNC → CLEANUP_READY 冻结 GC；全部 CLEANED 后解除。
    // 不用 isProjectRoutedOut：项目在 project-routing 内会永久为 true，导致 CLEANED 后仍误冻结。
    private fun isProjectGcFrozen(ruleName: String, projectId: String): Boolean =
        registry.isProjectInDualWrite(ruleName, projectId) ||
            isProjectInMigrationPhase(ruleName, projectId, GC_FREEZE_PHASES)

    // spec §3.18.3：CLEANUP_READY 起临时解除，允许 Job 删 Default 僵尸副本。
    private fun isPhysicalDeleteFrozen(ruleName: String, projectId: String): Boolean =
        registry.isProjectInDualWrite(ruleName, projectId) ||
            isProjectInMigrationPhase(ruleName, projectId, PHYSICAL_DELETE_FREEZE_PHASES)

    private fun isProjectInMigrationPhase(
        ruleName: String,
        projectId: String,
        phases: Set<MigrationPhase>,
    ): Boolean {
        val state = syncStateDao.findByRuleAndProject(ruleName, projectId) ?: return false
        return state.phase in phases
    }

    data class GateCheck(
        val name: String,
        val passed: Boolean,
        val reason: String? = null,
    )

    data class GateResult(val checks: List<GateCheck>) {
        val passed: Boolean get() = checks.all { it.passed }
        val failedChecks: List<GateCheck> get() = checks.filter { !it.passed }

        override fun toString(): String = buildString {
            append("GateResult[passed=$passed]")
            if (!passed) {
                append(" failures: ")
                append(failedChecks.joinToString("; ") { "${it.name}=${it.reason}" })
            }
        }
    }

    companion object {
        private val GC_FREEZE_PHASES = setOf(
            MigrationPhase.INITIAL_SYNC,
            MigrationPhase.DUAL_WRITE,
            MigrationPhase.ROUTED,
            MigrationPhase.CLEANUP_READY,
        )
        private val PHYSICAL_DELETE_FREEZE_PHASES = setOf(
            MigrationPhase.INITIAL_SYNC,
            MigrationPhase.DUAL_WRITE,
            MigrationPhase.ROUTED,
        )
    }
}