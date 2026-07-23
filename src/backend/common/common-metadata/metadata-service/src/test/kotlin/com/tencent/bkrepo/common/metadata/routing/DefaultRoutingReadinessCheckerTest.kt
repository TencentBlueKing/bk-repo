package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultRoutingReadinessCheckerTest {

    private lateinit var registry: MongoRoutingRegistry
    private lateinit var scatterService: NodeScatterQueryService
    private lateinit var batchQueryHelper: NodeBatchQueryHelper
    private lateinit var properties: MongoMultiInstanceProperties

    @BeforeEach
    fun setUp() {
        registry = mock()
        scatterService = mock()
        batchQueryHelper = mock()
        properties = MongoMultiInstanceProperties()
    }

    // ── 1. 路由禁用时所有 P0 项自动通过 ──────────────────────

    @Test
    fun `P0 items auto-pass when routing is disabled`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(false)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = scatterService,
            nodeBatchQueryHelper = batchQueryHelper,
            properties = properties,
        )

        val result = checker.check()

        // 路由禁用时 P0 项全部 passed（不需要探针检查）
        DefaultRoutingReadinessChecker.LOCAL_P0_MANIFEST.forEach { (id, _) ->
            val item = result.checks.first { it.id == id }
            assertTrue(item.passed, "P0 item $id should auto-pass when routing disabled")
        }
    }

    // ── 2. 路由启用时 P0 项须经探针，不可配置旁路 ───────────

    @Test
    fun `P0 items reflect actual probe result, no config bypass when routing active`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(true)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()

        DefaultRoutingReadinessChecker.LOCAL_P0_MANIFEST.forEach { (id, _) ->
            val item = result.checks.first { it.id == id }
            assertEquals(P0RoutingReadinessProbes.check(id), item.passed, "P0 item $id bypassed probe")
        }
    }

    // ── 3. INFRA 检查：registry 存在 ────────────────────────

    @Test
    fun `INFRA-01 passes when registry bean present`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(false)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        val infra01 = result.checks.first { it.id == "INFRA-01" }
        assertTrue(infra01.passed)
        assertEquals("MongoRoutingRegistry bean", infra01.description)
    }

    @Test
    fun `INFRA-01 fails when registry is null`() {
        val checker = DefaultRoutingReadinessChecker(
            registry = null,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        val infra01 = result.checks.first { it.id == "INFRA-01" }
        assertFalse(infra01.passed)
    }

    // ── 4. INFRA 检查：routing-enabled ───────────────────────

    @Test
    fun `INFRA-02 passes when node routing enabled`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(true)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        val infra02 = result.checks.first { it.id == "INFRA-02" }
        assertTrue(infra02.passed)
    }

    @Test
    fun `INFRA-02 fails when node routing disabled`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(false)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        val infra02 = result.checks.first { it.id == "INFRA-02" }
        assertFalse(infra02.passed)
    }

    // ── 5. M5 检查：scatterQueryService / batchQueryHelper ──

    @Test
    fun `M5-01 passes when NodeScatterQueryService bean present`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(false)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = scatterService,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        val m501 = result.checks.first { it.id == "M5-01" }
        assertTrue(m501.passed)
    }

    @Test
    fun `M5-01 fails when NodeScatterQueryService bean missing`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(false)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        val m501 = result.checks.first { it.id == "M5-01" }
        assertFalse(m501.passed)
    }

    @Test
    fun `M5-02 passes when NodeBatchQueryHelper bean present`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(false)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = batchQueryHelper,
            properties = properties,
        )

        val result = checker.check()
        val m502 = result.checks.first { it.id == "M5-02" }
        assertTrue(m502.passed)
    }

    @Test
    fun `M5-02 fails when NodeBatchQueryHelper bean missing`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(false)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        val m502 = result.checks.first { it.id == "M5-02" }
        assertFalse(m502.passed)
    }

    // ── 6. M5 检查：config-version ────────────────────────────

    @Test
    fun `M5-03 passes when config version is up to date`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(false)
        whenever(registry.isConfigUpToDate()).thenReturn(true)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        val m503 = result.checks.first { it.id == "M5-03" }
        assertTrue(m503.passed)
    }

    @Test
    fun `M5-03 fails when config version is behind`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(false)
        whenever(registry.isConfigUpToDate()).thenReturn(false)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        val m503 = result.checks.first { it.id == "M5-03" }
        assertFalse(m503.passed)
    }

    @Test
    fun `M5-03 passes when registry is null`() {
        val checker = DefaultRoutingReadinessChecker(
            registry = null,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        val m503 = result.checks.first { it.id == "M5-03" }
        assertTrue(m503.passed, "null registry should not fail config-version check")
    }

    // ── 7. ready 标志：全部通过 → ready=true ─────────────────

    @Test
    fun `check returns ready=true when all items pass`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(false)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = scatterService,
            nodeBatchQueryHelper = batchQueryHelper,
            properties = properties,
        )

        val result = checker.check()
        // 路由禁用时 P0 自动通过，所有 INFRA/M5 项通过
        assertTrue(result.checks.none { !it.passed }, "all checks should pass")
        assertTrue(result.ready)
    }

    @Test
    fun `check returns ready=false when any item fails`() {
        val checker = DefaultRoutingReadinessChecker(
            registry = null,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        assertFalse(result.ready, "should be not ready when INFRA-01 fails")
    }

    // ── 8. check 返回完整结构 ─────────────────────────────────

    @Test
    fun `check always returns all expected check categories`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(true)
        whenever(registry.isConfigUpToDate()).thenReturn(true)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()

        // INFRA 项必须存在
        assertTrue(result.checks.any { it.id == "INFRA-01" })
        assertTrue(result.checks.any { it.id == "INFRA-02" })

        // M5 项必须存在
        assertTrue(result.checks.any { it.id == "M5-01" })
        assertTrue(result.checks.any { it.id == "M5-02" })
        assertTrue(result.checks.any { it.id == "M5-03" })
        assertTrue(result.checks.any { it.id == "M5-05" })

        // LOCAL_P0 项必须存在
        DefaultRoutingReadinessChecker.LOCAL_P0_MANIFEST.forEach { (id, _) ->
            assertTrue(result.checks.any { it.id == id },
                "P0 item $id should be present in checks")
        }
    }

    // ── 9. 所有检查项 description 不为空 ─────────────────────

    @Test
    fun `all check items have non-blank description`() {
        whenever(registry.isRoutingEnabled("node")).thenReturn(true)
        whenever(registry.isConfigUpToDate()).thenReturn(true)
        val checker = DefaultRoutingReadinessChecker(
            registry = registry,
            scatterQueryService = null,
            nodeBatchQueryHelper = null,
            properties = properties,
        )

        val result = checker.check()
        result.checks.forEach { item ->
            assertNotNull(item.description, "check ${item.id} has null description")
            assertTrue(item.description.isNotBlank(), "check ${item.id} has blank description")
        }
    }
}
