package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.model.TMigrationSyncState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * MigrationGate 单元测试（Spec §3.10 / §25.3.2）。
 */
class MigrationGateTest {

    private lateinit var registry: MongoRoutingRegistry
    private lateinit var gate: MigrationGate
    private lateinit var properties: MongoMultiInstanceProperties

    @BeforeEach
    fun setUp() {
        registry = mock()
        properties = MongoMultiInstanceProperties().apply {
            maxConcurrentDualWrite = 1
            rules = mapOf(
                "node" to MongoMultiInstanceProperties.RoutingRule(
                    routingState = RuleRoutingState.DUAL_WRITE,
                    projectRouting = mapOf("projectA" to "heavy1"),
                    migration = MongoMultiInstanceProperties.RoutingRule.MigrationConfig(
                        projectLocks = MongoMultiInstanceProperties.RoutingRule.ProjectLocksConfig(
                            freezeGc = true,
                            freezePhysicalDelete = true,
                        ),
                    ),
                ),
            )
        }
        gate = MigrationGate(registry, properties)
    }

    // ── 5. canSwitchToRouted: 全部满足 → passed ─────────────────────────

    @Test
    fun `canSwitchToRouted passes when queue empty and sidecar passed`() {
        val result = gate.canSwitchToRouted(
            compensationQueueEmpty = true,
            sidecarPassed = true,
        )
        assertTrue(result.passed)
        assertEquals(2, result.checks.size)
    }

    // ── 6. canSwitchToRouted: 补偿未清零 → failed ────────────────────────

    @Test
    fun `canSwitchToRouted fails when compensation queue not empty`() {
        val result = gate.canSwitchToRouted(
            compensationQueueEmpty = false,
            sidecarPassed = true,
        )
        assertFalse(result.passed)
        assertTrue(result.failedChecks.any { it.name == "compensationQueueEmpty" })
    }

    @Test
    fun `canSwitchToRouted fails when sidecar not passed`() {
        val result = gate.canSwitchToRouted(
            compensationQueueEmpty = true,
            sidecarPassed = false,
        )
        assertFalse(result.passed)
        assertTrue(result.failedChecks.any { it.name == "sidecarPassed" })
        assertTrue(result.failedChecks.any { it.reason?.contains("旁路对账") == true })
    }

    // ── 7. GateCheck / GateResult 格式化 ─────────────────────────────────

    @Test
    fun `GateResult toString includes failure reasons`() {
        val check = MigrationGate.GateCheck(
            name = "test",
            passed = false,
            reason = "test failure reason",
        )
        val result = MigrationGate.GateResult(listOf(check))
        assertFalse(result.passed)
        assertTrue(result.toString().contains("test failure reason"))
    }

    @Test
    fun `GateResult with all passed prints correct message`() {
        val checks = listOf(
            MigrationGate.GateCheck("a", true),
            MigrationGate.GateCheck("b", true),
        )
        val result = MigrationGate.GateResult(checks)
        assertTrue(result.passed)
        assertTrue(result.failedChecks.isEmpty())
    }

    // ── 8. GateCheck 属性验证 ────────────────────────────────────────────

    @Test
    fun `GateCheck with null reason has no error message`() {
        val check = MigrationGate.GateCheck("ok", true, null)
        assertTrue(check.passed)
        assertEquals(null, check.reason)
    }

    @Test
    fun `isGcFrozen is true when project is in dual-write`() {
        whenever(registry.isProjectInDualWrite("node", "projectA")).thenReturn(true)
        assertTrue(gate.isGcFrozen())
        assertTrue(gate.isProjectGcFrozen("projectA"))
    }

    @Test
    fun `isGcFrozen is true when project is routed out`() {
        whenever(registry.isProjectInDualWrite("node", "projectA")).thenReturn(false)
        whenever(registry.isProjectRoutedOut("node", "projectA")).thenReturn(true)
        assertTrue(gate.isGcFrozen())
        assertTrue(gate.isProjectGcFrozen("projectA"))
    }

    @Test
    fun `isGcFrozen is false when project is neither dual-write nor routed`() {
        whenever(registry.isProjectInDualWrite("node", "projectA")).thenReturn(false)
        whenever(registry.isProjectRoutedOut("node", "projectA")).thenReturn(false)
        assertFalse(gate.isGcFrozen())
        assertFalse(gate.isProjectGcFrozen("projectA"))
    }

    @Test
    fun `isPhysicalDeleteFrozen is true when project is in dual-write`() {
        whenever(registry.isProjectInDualWrite("node", "projectA")).thenReturn(true)
        whenever(registry.isProjectRoutedOut("node", "projectA")).thenReturn(false)
        assertTrue(gate.isPhysicalDeleteFrozen("projectA"))
    }

    @Test
    fun `isPhysicalDeleteFrozen is true when project is routed out`() {
        whenever(registry.isProjectRoutedOut("node", "projectA")).thenReturn(true)
        whenever(registry.isProjectInDualWrite("node", "projectA")).thenReturn(false)
        assertTrue(gate.isPhysicalDeleteFrozen("projectA"))
    }

    @Test
    fun `isPhysicalDeleteFrozen is false when project is neither dual-write nor routed`() {
        whenever(registry.isProjectRoutedOut("node", "projectA")).thenReturn(false)
        whenever(registry.isProjectInDualWrite("node", "projectA")).thenReturn(false)
        assertFalse(gate.isPhysicalDeleteFrozen("projectA"))
    }

    @Test
    fun `isGcFrozen is true when DB phase is INITIAL_SYNC without Consul dual-write`() {
        val syncStateDao = mock<MigrationSyncStateDao>()
        whenever(syncStateDao.findByProjectId("projectA")).thenReturn(
            TMigrationSyncState(
                id = "projectA",
                projectId = "projectA",
                ruleName = "node",
                targetInstance = "heavy1",
                phase = MigrationPhase.INITIAL_SYNC,
            ),
        )
        whenever(registry.isProjectInDualWrite("node", "projectA")).thenReturn(false)
        whenever(registry.isProjectRoutedOut("node", "projectA")).thenReturn(false)
        gate = MigrationGate(registry, properties, syncStateDao)
        assertTrue(gate.isProjectGcFrozen("projectA"))
        assertTrue(gate.isPhysicalDeleteFrozen("projectA"))
    }
}