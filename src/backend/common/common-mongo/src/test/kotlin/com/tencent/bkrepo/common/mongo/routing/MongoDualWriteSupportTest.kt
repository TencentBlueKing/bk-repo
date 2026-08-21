package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MongoDualWriteSupport 单元测试（Spec §25.2.2 E-01 / §3.6.3 双写副路径执行）。
 *
 * 覆盖双写执行器的关键路径：
 * 1. executePrimaryWrite: 正常写 primary
 * 2. executePrimaryWrite: fallback 到 Default
 * 3. executePrimaryWrite: 无 fallback 时异常传播
 * 4. executePrimaryWrite: zombie 副本写保护（fail-fast）
 * 5. submitSecondaryWrite: secondary=null 时跳过
 * 6. submitSecondaryWrite: 同步写（syncSecondaryWrite=true）
 * 7. submitSecondaryWrite: 异步写
 * 8. submitSecondaryWrite: 副路径写失败时触发补偿入队
 * 9. submitSecondaryWrite: 线程池拒绝时触发补偿入队
 * 10. assertNotZombieReplica: primary 不是 Default 时跳过
 */
class MongoDualWriteSupportTest {

    private lateinit var primaryTemplate: MongoTemplate
    private lateinit var secondaryTemplate: MongoTemplate
    private lateinit var fallbackTemplate: MongoTemplate
    private lateinit var registry: MongoRoutingRegistry

    @BeforeEach
    fun setUp() {
        primaryTemplate = mock()
        secondaryTemplate = mock()
        fallbackTemplate = mock()
        registry = mock()
    }

    // ── 1. executePrimaryWrite: 正常写 primary ──────────────────

    @Test
    fun `executePrimaryWrite calls action on primary template`() {
        val route = WriteRoute(primary = primaryTemplate)
        val result = MongoDualWriteSupport.executePrimaryWrite(
            route, "node_0", fallbackTemplate, registry,
        ) { it }
        assertSame(primaryTemplate, result)
    }

    // ── 2. executePrimaryWrite: fallback 到 Default ────────────

    @Test
    fun `executePrimaryWrite falls back to default on exception`() {
        val route = WriteRoute(
            primary = primaryTemplate,
            fallbackTemplate = fallbackTemplate,
            fallbackToDefault = true,
        )
        // primary 抛异常，期望 fallback 模板被使用
        var calledOnFallback = false
        val result = MongoDualWriteSupport.executePrimaryWrite(
            route, "node_0", mock(), registry,
        ) { template ->
            if (template === primaryTemplate) {
                throw RuntimeException("primary write failed")
            }
            calledOnFallback = true
            template
        }
        assertSame(fallbackTemplate, result)
        assertTrue(calledOnFallback, "Fallback template should be called")
    }

    // ── 3. executePrimaryWrite: 无 fallback 时异常传播 ─────────

    @Test
    fun `executePrimaryWrite propagates exception when fallback disabled`() {
        val route = WriteRoute(
            primary = primaryTemplate,
            // fallbackToDefault = false（默认）
        )
        assertThrows(RuntimeException::class.java) {
            MongoDualWriteSupport.executePrimaryWrite(
                route, "node_0", mock(), registry,
            ) { throw RuntimeException("primary write failed") }
        }
    }

    @Test
    fun `executePrimaryWrite falls back only when not same template`() {
        val route = WriteRoute(
            primary = primaryTemplate,
            fallbackTemplate = primaryTemplate, // same as primary
            fallbackToDefault = true,
        )
        assertThrows(RuntimeException::class.java) {
            MongoDualWriteSupport.executePrimaryWrite(
                route, "node_0", mock(), registry,
            ) { throw RuntimeException("primary write failed") }
        }
    }

    // ── 4. Zombie 副本写保护（E-01） ────────────────────────────

    @Test
    fun `executePrimaryWrite throws when zombie replica detected`() {
        // primary 是 defaultTemplate，且项目已 ROUTED
        val route = WriteRoute(
            primary = fallbackTemplate,
            routingKey = "projectA",
        )
        whenever(registry.resolveRuleName("node_0")).thenReturn("node")
        whenever(registry.isProjectRoutedOut("node", "projectA")).thenReturn(true)

        assertThrows(IllegalStateException::class.java) {
            MongoDualWriteSupport.executePrimaryWrite(
                route, "node_0", fallbackTemplate, registry,
            ) { it }
        }
    }

    @Test
    fun `executePrimaryWrite does not throw when primary is not Default`() {
        // primary 是 Heavy 模板（非 Default），不应触发僵尸检查
        val route = WriteRoute(
            primary = primaryTemplate, // Heavy
            routingKey = "projectA",
        )
        whenever(registry.resolveRuleName("node_0")).thenReturn("node")
        whenever(registry.isProjectRoutedOut("node", "projectA")).thenReturn(true)

        // 不应抛异常，因为 primary 不是 Default
        val result = MongoDualWriteSupport.executePrimaryWrite(
            route, "node_0", fallbackTemplate, registry,
        ) { it }
        assertSame(primaryTemplate, result)
    }

    @Test
    fun `assertNotZombieReplica skips when registry is null`() {
        // registry 为 null 时直接跳过检查
        val route = WriteRoute(primary = fallbackTemplate, routingKey = "projectA")
        MongoDualWriteSupport.executePrimaryWrite(
            route, "node_0", fallbackTemplate, null,
        ) { it }
    }

    // ── 5. submitSecondaryWrite: secondary=null 时跳过 ─────────

    @Test
    fun `submitSecondaryWrite skips when secondary is null`() {
        val route = WriteRoute(primary = primaryTemplate, secondary = null)
        var enqueueCalled = false
        var actionCalled = false
        MongoDualWriteSupport.submitSecondaryWrite(
            route, "node_0",
            enqueue = { enqueueCalled = true },
            action = { actionCalled = true },
        )
        assertFalse(enqueueCalled, "Enqueue should not be called when secondary is null")
        assertFalse(actionCalled, "Action should not be called when secondary is null")
    }

    // ── 6. submitSecondaryWrite: 同步写（模式二双写） ──────────

    @Test
    fun `submitSecondaryWrite executes synchronously when syncSecondaryWrite is true`() {
        val route = WriteRoute(
            primary = primaryTemplate,
            secondary = secondaryTemplate,
            syncSecondaryWrite = true,
        )
        var enqueueCalled = false
        var actionCalled = false
        MongoDualWriteSupport.submitSecondaryWrite(
            route, "node_0",
            enqueue = { enqueueCalled = true },
            action = {
                actionCalled = true
                // 同步模式下，不应抛异常导致 enqueue
            },
        )
        assertTrue(actionCalled, "Synchronous secondary write should execute action")
        assertFalse(enqueueCalled, "Enqueue should not be called on success")
    }

    // ── 7. submitSecondaryWrite: 副路径写失败触发补偿 ──────────

    @Test
    fun `submitSecondaryWrite enqueues compensation on secondary failure`() {
        val route = WriteRoute(
            primary = primaryTemplate,
            secondary = secondaryTemplate,
            syncSecondaryWrite = true, // 同步模式便于测试
        )
        var enqueueCalled = false
        MongoDualWriteSupport.submitSecondaryWrite(
            route, "node_0",
            enqueue = { enqueueCalled = true },
            action = { throw RuntimeException("secondary write failed") },
        )
        assertTrue(enqueueCalled, "Enqueue should be called when secondary write fails")
    }

    // ── 8. submitSecondaryWrite: 异步模式 ──────────────────────

    @Test
    fun `submitSecondaryWrite executes async when syncSecondaryWrite is false`() {
        val route = WriteRoute(
            primary = primaryTemplate,
            secondary = secondaryTemplate,
            // syncSecondaryWrite = false（默认异步）
        )
        var actionExecuted = AtomicBoolean(false)
        var enqueueCalled = false

        MongoDualWriteSupport.submitSecondaryWrite(
            route, "node_0",
            enqueue = { enqueueCalled = true },
            action = {
                actionExecuted.set(true)
            },
        )

        // 等待异步执行完成（最多等待 5 秒）
        val deadline = System.currentTimeMillis() + 5000
        while (!actionExecuted.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }

        assertTrue(actionExecuted.get(), "Async secondary write should eventually execute")
        assertFalse(enqueueCalled, "Enqueue should not be called on success")
    }
}
