package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

/**
 * 迁移门控（§3.10 / §25.3.2）。
 *
 * 在所有迁移状态跃迁前执行前置条件校验，任一不满足则拒绝跃迁。
 *
 * 门控校验链：
 * - DUAL_WRITE 前：补偿队列清零 + 旁路对账连续3轮零差异 + 双写并发上限
 * - ROUTED 前：补偿队列清零 + 旁路对账连续3轮零差异
 * - 100% Pod 滚动完成：运维 SOP（`kubectl rollout status`），不在此自动校验
 * - 迁移期 freeze-gc：按 Consul 配置 + [MongoRoutingRegistry] 判断（§3.18.2）
 */
@Component
@ConditionalOnBean(MongoRoutingRegistry::class)
class MigrationGate(
    private val registry: MongoRoutingRegistry,
    private val properties: MongoMultiInstanceProperties,
    @Autowired(required = false)
    private val syncStateDao: MigrationSyncStateDao? = null,
) {

    /**
     * 是否允许进入 DUAL_WRITE 阶段。
     */
    fun canEnterDualWrite(
        compensationQueueEmpty: Boolean,
        sidecarPassed: Boolean,
        currentDualWriteCount: Int = 0,
    ): GateResult {
        val checks = mutableListOf<GateCheck>()
        checks += checkMaxConcurrentDualWrite(currentDualWriteCount)
        checks += GateCheck("compensationQueueEmpty", compensationQueueEmpty,
            if (!compensationQueueEmpty) "补偿队列未清零，阻塞进入 DUAL_WRITE" else null)
        checks += GateCheck("sidecarPassed", sidecarPassed,
            if (!sidecarPassed) "旁路对账未通过（需连续 3 轮零差异），阻塞进入 DUAL_WRITE" else null)
        return GateResult(checks)
    }

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
     * 任一迁出项目处于需 freeze-gc 的迁移阶段时返回 true。
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

    /**
     * 判断指定项目是否允许物理删除 Default 上的 node 副本（§3.18.2）。
     */
    fun isPhysicalDeleteFrozen(projectId: String): Boolean =
        properties.rules.any { (ruleName, rule) ->
            projectId in rule.projectRouting &&
                rule.migration.projectLocks.freezePhysicalDelete &&
                isPhysicalDeleteFrozen(ruleName, projectId)
        }

    /**
     * 判断指定项目是否禁止 Default 侧 node 变更（§3.18.2）。
     */
    fun isDefaultNodeMutationFrozen(projectId: String): Boolean =
        properties.rules.any { (ruleName, rule) ->
            projectId in rule.projectRouting &&
                rule.migration.projectLocks.freezeDefaultNodeMutation &&
                isDefaultNodeMutationFrozen(ruleName, projectId)
        }

    /** G-17：ROUTED 后僵尸副本超时，阻断后续迁移编排 */
    fun isZombieReplicaOverdue(ruleName: String, projectId: String): Boolean {
        val rule = properties.rules[ruleName] ?: return false
        val state = syncStateDao?.findByProjectId(projectId) ?: return false
        if (state.phase != MigrationPhase.ROUTED && state.phase != MigrationPhase.CLEANUP_READY) {
            return false
        }
        val maxHours = rule.migration.maxZombieHours.toLong()
        return Duration.between(state.updatedAt, LocalDateTime.now()).toHours() >= maxHours
    }

    // ─── private ────────────────────────────────────────────────

    private fun isProjectGcFrozen(ruleName: String, projectId: String): Boolean =
        registry.isProjectInDualWrite(ruleName, projectId)

    private fun isPhysicalDeleteFrozen(ruleName: String, projectId: String): Boolean =
        registry.isProjectRoutedOut(ruleName, projectId)

    private fun isDefaultNodeMutationFrozen(ruleName: String, projectId: String): Boolean =
        registry.isProjectInDualWrite(ruleName, projectId) || registry.isProjectRoutedOut(ruleName, projectId)

    private fun checkMaxConcurrentDualWrite(currentDualWriteCount: Int): GateCheck {
        val limit = properties.maxConcurrentDualWrite
        val passed = currentDualWriteCount < limit
        return GateCheck(
            name = "maxConcurrentDualWrite",
            passed = passed,
            reason = if (!passed) {
                "当前双写项目数 $currentDualWriteCount 已达上限 $limit，阻塞进入 DUAL_WRITE"
            } else null,
        )
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
}