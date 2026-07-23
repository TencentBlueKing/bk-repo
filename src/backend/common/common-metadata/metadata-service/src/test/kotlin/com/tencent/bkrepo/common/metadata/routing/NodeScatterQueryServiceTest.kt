package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.LocalDateTime
import java.util.concurrent.Executors

/**
 * NodeScatterQueryService 单元测试（Spec §16.1）。
 *
 * 覆盖：
 * 1. 无路由时只查 Default，不追加 projectId 过滤
 * 2. 路由开启：Default 追加 NOT IN，Heavy 追加 IN，结果合并
 * 3. 跨实例结果按 _id 去重
 * 4. 部分实例超时时降级返回已完成实例的结果
 * 5. 深度分页（offset > 10000）抛出异常
 */
class NodeScatterQueryServiceTest {

    private val collections = listOf("node_0", "node_1")

    private lateinit var defaultTemplate: MongoTemplate
    private lateinit var heavy1Template: MongoTemplate
    private lateinit var registry: MongoRoutingRegistry

    @BeforeEach
    fun setUp() {
        defaultTemplate = mock()
        heavy1Template = mock()
        registry = mock()
    }

    private fun service(
        timeoutSeconds: Long = 5L,
        mode: NodeScatterQueryService.ScatterMode = NodeScatterQueryService.ScatterMode.STRICT,
    ) = NodeScatterQueryService(
        defaultTemplate = defaultTemplate,
        registry = registry,
        executor = Executors.newFixedThreadPool(4),
        timeoutSeconds = timeoutSeconds,
        mode = mode,
        batchQueryHelper = NodeBatchQueryHelper(defaultTemplate, registry),
    )

    private fun makeNode(id: String, projectId: String = "proj", sha256: String = "abc") = TNode(
        id = id,
        createdBy = "test",
        createdDate = LocalDateTime.now(),
        lastModifiedBy = "test",
        lastModifiedDate = LocalDateTime.now(),
        folder = false,
        path = "/",
        name = "file.txt",
        fullPath = "/file.txt",
        size = 100L,
        sha256 = sha256,
        projectId = projectId,
        repoName = "repo",
    )

    // ── 1. 无路由时只查 Default，不追加额外过滤 ─────────────────────────────

    @Test
    fun `scatterFind queries only Default when no routing configured`() {
        whenever(registry.routedProjectIds("node")).thenReturn(emptySet())
        whenever(registry.projectsByInstance("node")).thenReturn(emptyMap())

        val node1 = makeNode("id1")
        val node2 = makeNode("id2")
        whenever(defaultTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(node1), listOf(node2))

        val result = service().scatterFind(Query(), TNode::class.java, collections)

        assertEquals(2, result.size)
        assertEquals(setOf("id1", "id2"), result.map { it.id }.toSet())
    }

    // ── 2. 路由开启：Default + Heavy 合并结果 ───────────────────────────────

    @Test
    fun `scatterFind merges results from Default and Heavy instances`() {
        whenever(registry.routedProjectIds("node")).thenReturn(setOf("projectA"))
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("projectA"))
        )
whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavy1Template)

        val defaultNode = makeNode("default-id", projectId = "projectB")
        val heavyNode = makeNode("heavy-id", projectId = "projectA")
        whenever(defaultTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(defaultNode), emptyList())
        whenever(heavy1Template.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(heavyNode), emptyList())

        val result = service().scatterFind(Query(), TNode::class.java, collections)

        assertEquals(setOf("default-id", "heavy-id"), result.map { it.id }.toSet())
    }

    // ── 3. 跨实例结果按 _id 去重 ─────────────────────────────────────────────

    @Test
    fun `scatterFind deduplicates results by id when same document appears in multiple instances`() {
        // 双写期间同一文档同时存在于 Default 和 Heavy，id 相同
        whenever(registry.routedProjectIds("node")).thenReturn(setOf("projectA"))
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("projectA"))
        )
whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavy1Template)

        val duplicateId = "shared-id"
        val nodeFromDefault = makeNode(duplicateId, projectId = "projectA")
        val nodeFromHeavy = makeNode(duplicateId, projectId = "projectA")
        whenever(defaultTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(nodeFromDefault), emptyList())
        whenever(heavy1Template.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(nodeFromHeavy), emptyList())

        val result = service().scatterFind(Query(), TNode::class.java, collections)

        // 同一 id 只保留一条
        assertEquals(1, result.size)
        assertEquals(duplicateId, result.first().id)
    }

    @Test
    fun `pageBySha256 result has no duplicate ids`() {
        whenever(registry.routedProjectIds("node")).thenReturn(setOf("projectA"))
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("projectA"))
        )
whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavy1Template)

        val sha256 = "deadbeef"
        val shared = makeNode("dup", sha256 = sha256)
        whenever(defaultTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(shared), emptyList())
        whenever(heavy1Template.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(shared), emptyList())

        val page = service().pageBySha256(sha256, PageRequest.of(0, 10), collections)
        val ids = page.content.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    // ── 4. 部分实例超时降级 ──────────────────────────────────────────────────

    @Test
    fun `scatterFind returns partial results when one instance times out in DEGRADE mode`() {
        val slowTemplate: MongoTemplate = mock()
        whenever(registry.routedProjectIds("node")).thenReturn(setOf("projectA"))
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("projectA"))
        )
whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(slowTemplate)

        val fastNode = makeNode("fast-id")
        whenever(defaultTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(fastNode), emptyList())
        whenever(slowTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenAnswer { Thread.sleep(3_000); emptyList<TNode>() }

        val result = service(
            timeoutSeconds = 0L,
            mode = NodeScatterQueryService.ScatterMode.DEGRADE,
        ).scatterFind(Query(), TNode::class.java, collections)

        result.forEach { node -> assertEquals("fast-id", node.id) }
    }

    @Test
    fun `scatterFind throws in STRICT mode when one instance times out`() {
        val slowTemplate: MongoTemplate = mock()
        whenever(registry.routedProjectIds("node")).thenReturn(setOf("projectA"))
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("projectA"))
        )
whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(slowTemplate)

        whenever(defaultTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(makeNode("fast-id")), emptyList())
        whenever(slowTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenAnswer { Thread.sleep(3_000); emptyList<TNode>() }

        assertThrows(com.tencent.bkrepo.common.api.exception.BadRequestException::class.java) {
            service(timeoutSeconds = 0L).scatterFind(Query(), TNode::class.java, collections)
        }
    }

    @Test
    fun `scatterFind throws in STRICT mode when one instance throws`() {
        val failingTemplate: MongoTemplate = mock()
        whenever(registry.routedProjectIds("node")).thenReturn(setOf("projectA"))
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("projectA"))
        )
whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(failingTemplate)

        whenever(defaultTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(makeNode("good-id")), emptyList())
        whenever(failingTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenThrow(RuntimeException("connection refused"))

        assertThrows(com.tencent.bkrepo.common.api.exception.BadRequestException::class.java) {
            service().scatterFind(Query(), TNode::class.java, collections)
        }
    }

    // ── 5. 深度分页拒绝 ──────────────────────────────────────────────────────

    @Test
    fun `pageBySha256 throws BadRequestException when offset exceeds 10000`() {
        whenever(registry.routedProjectIds("node")).thenReturn(emptySet())
        whenever(registry.projectsByInstance("node")).thenReturn(emptyMap())

        // offset = page * size = 1001 * 10 = 10010 > 10000
        val badPageRequest = PageRequest.of(1001, 10)
        assertThrows(com.tencent.bkrepo.common.api.exception.BadRequestException::class.java) {
            service().pageBySha256("anysha256", badPageRequest, collections)
        }
    }

    // ── 6. 单实例异常时降级返回其余实例结果 ──────────────────────────────────

    @Test
    fun `scatterFind returns results from healthy instances when one instance throws in DEGRADE mode`() {
        val failingTemplate: MongoTemplate = mock()
        whenever(registry.routedProjectIds("node")).thenReturn(setOf("projectA"))
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("projectA"))
        )
whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(failingTemplate)

        val goodNode = makeNode("good-id")
        whenever(defaultTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(goodNode), emptyList())
        whenever(failingTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenThrow(RuntimeException("connection refused"))

        val result = service(mode = NodeScatterQueryService.ScatterMode.DEGRADE)
            .scatterFind(Query(), TNode::class.java, collections)

        assertEquals(listOf("good-id"), result.map { it.id })
    }

    @Test
    fun `scatterFind merges results preserving encounter order after dedup`() {
        whenever(registry.routedProjectIds("node")).thenReturn(setOf("projectA"))
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("projectA"))
        )
whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavy1Template)

        val first = makeNode("id-1", projectId = "projectB")
        val second = makeNode("id-2", projectId = "projectA")
        whenever(defaultTemplate.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(first), emptyList())
        whenever(heavy1Template.find(any<Query>(), any<Class<TNode>>(), any<String>()))
            .thenReturn(listOf(second), emptyList())

        val result = service().scatterFind(Query(), TNode::class.java, collections)
        assertEquals(listOf("id-1", "id-2"), result.map { it.id })
    }
}
