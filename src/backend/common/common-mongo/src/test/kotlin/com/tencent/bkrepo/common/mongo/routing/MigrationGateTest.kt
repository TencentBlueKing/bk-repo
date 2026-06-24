package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * MigrationGate 单元测试（Spec §3.10 / §25.3.3 E-06）。
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
                    routingEnabled = true,
                    projectRouting = mapOf("projectA" to "heavy1"),
                    migration = MongoMultiInstanceProperties.RoutingRule.MigrationConfig(
                        projectLocks = MongoMultiInstanceProperties.RoutingRule.ProjectLocksConfig(
                            freezeGc = true,
                            freezePhysicalDelete = true,
                            freezeDefaultNodeMutation = true,
                        ),
                    ),
                ),
            )
        }
        gate = MigrationGate(registry, properties)
    }

    // ── 1. canEnterDualWrite: 全部条件满足 → passed ──────────────────────

    @Test
    fun `canEnterDualWrite passes when all checks pass`() {
        whenever(registry.isConfigUpToDate()).thenReturn(true)
        whenever(registry.getConfigVersion()).thenReturn(5)
        whenever(registry.getMinConfigVersion()).thenReturn(5)

        val result = gate.canEnterDualWrite(
            compensationQueueEmpty = true,
            sidecarPassed = true,
            currentDualWriteCount = 0,
        )
        assertTrue(result.passed)
        assertTrue(result.checks.all { it.passed })
    }

    @Test
    fun `canEnterDualWrite fails when concurrent dual-write limit reached`() {
        whenever(registry.isConfigUpToDate()).thenReturn(true)
        whenever(registry.getConfigVersion()).thenReturn(5)
        whenever(registry.getMinConfigVersion()).thenReturn(5)

        val result = gate.canEnterDualWrite(
            compensationQueueEmpty = true,
            sidecarPassed = true,
            currentDualWriteCount = 1,
        )
        assertFalse(result.passed)
        assertTrue(result.failedChecks.any { it.name == "maxConcurrentDualWrite" })
    }

    // ── 2. canEnterDualWrite: configVersion 不满足 ─────────────────────

    @Test
    fun `canEnterDualWrite fails when configVersion is behind`() {
        whenever(registry.isConfigUpToDate()).thenReturn(false)
        whenever(registry.getConfigVersion()).thenReturn(3)
        whenever(registry.getMinConfigVersion()).thenReturn(5)

        val result = gate.canEnterDualWrite(
            compensationQueueEmpty = true,
            sidecarPassed = true,
        )
        assertFalse(result.passed)
        assertTrue(result.failedChecks.any { it.name == "configVersion" })
    }

    @Test
    fun `canEnterDualWrite fails when cluster pod is behind minConfigVersion`() {
        whenever(registry.isConfigUpToDate()).thenReturn(true)
        whenever(registry.getConfigVersion()).thenReturn(5)
        whenever(registry.getMinConfigVersion()).thenReturn(5)
        val podRegistry = mock<MongoRoutingPodRegistry>()
        whenever(podRegistry.verifyClusterUpToDate(5)).thenReturn(
            MongoRoutingPodRegistry.ClusterPodCheck(
                passed = false,
                reason = "pods behind minConfigVersion=5: repo@host1(v=3)",
            ),
        )
        gate = MigrationGate(registry, properties, podRegistry = podRegistry)

        val result = gate.canEnterDualWrite(
            compensationQueueEmpty = true,
            sidecarPassed = true,
        )
        assertFalse(result.passed)
        assertTrue(result.failedChecks.any { it.name == "configVersion" })
    }

    // ── 3. canEnterDualWrite: 补偿队列未清零 ─────────────────────────────

    @Test
    fun `canEnterDualWrite fails when compensation queue is not empty`() {
        whenever(registry.isConfigUpToDate()).thenReturn(true)
        whenever(registry.getConfigVersion()).thenReturn(5)
        whenever(registry.getMinConfigVersion()).thenReturn(5)

        val result = gate.canEnterDualWrite(
            compensationQueueEmpty = false,
            sidecarPassed = true,
        )
        assertFalse(result.passed)
        assertTrue(result.failedChecks.any {
            it.name == "compensationQueueEmpty" && it.reason?.contains("补偿队列") == true
        })
    }

    // ── 4. canEnterDualWrite: 旁路对账未通过 ────────────────────────────

    @Test
    fun `canEnterDualWrite fails when sidecar verification not passed`() {
        whenever(registry.isConfigUpToDate()).thenReturn(true)
        whenever(registry.getConfigVersion()).thenReturn(5)
        whenever(registry.getMinConfigVersion()).thenReturn(5)

        val result = gate.canEnterDualWrite(
            compensationQueueEmpty = true,
            sidecarPassed = false,
        )
        assertFalse(result.passed)
        assertTrue(result.failedChecks.any {
            it.name == "sidecarPassed" && it.reason?.contains("旁路对账") == true
        })
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
    fun `isGcFrozen is false when project is not in dual-write`() {
        whenever(registry.isProjectInDualWrite("node", "projectA")).thenReturn(false)
        assertFalse(gate.isGcFrozen())
        assertFalse(gate.isProjectGcFrozen("projectA"))
    }

    @Test
    fun `isPhysicalDeleteFrozen is true when project is routed out`() {
        whenever(registry.isProjectRoutedOut("node", "projectA")).thenReturn(true)
        whenever(registry.isProjectInDualWrite("node", "projectA")).thenReturn(false)
        assertTrue(gate.isPhysicalDeleteFrozen("projectA"))
    }

    @Test
    fun `isPhysicalDeleteFrozen is false when project is not routed out`() {
        whenever(registry.isProjectRoutedOut("node", "projectA")).thenReturn(false)
        whenever(registry.isProjectInDualWrite("node", "projectA")).thenReturn(true)
        assertFalse(gate.isPhysicalDeleteFrozen("projectA"))
    }
}