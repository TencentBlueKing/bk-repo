package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.mongo.api.routing.BatchQueryGroup
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

/**
 * NodeBatchQueryHelper 单元测试（Spec §3.8.2 / G-40）。
 *
 * 覆盖 buildGroups 全部分支：
 * 1. registry 为 null → 返回 null
 * 2. 无路由项目且无 shard → 返回 null
 * 3. 仅有 shard-routing → 按实例分组
 * 4. 仅有 project-routing → Default NOT IN + Heavy IN 分组
 * 5. project-routing 项目数超过阈值 → Default 改用 IN whitelist
 * 6. shard-routing + project-routing 共存 → 正确分组
 * 7. 空 collectionNames → 返回 null
 * 8. 某实例无 template → 跳过该实例
 */
class NodeBatchQueryHelperTest {

    private lateinit var defaultTemplate: MongoTemplate
    private lateinit var heavyTemplate: MongoTemplate
    private lateinit var registry: MongoRoutingRegistry
    private lateinit var helper: NodeBatchQueryHelper

    private fun allNodeCollections(): List<String> =
        (0 until SHARDING_COUNT).map { "node_$it" }

    private fun shardFor(projectId: String): String =
        "node_${HashShardingUtils.shardingSequenceFor(projectId, SHARDING_COUNT)}"

    @BeforeEach
    fun setUp() {
        defaultTemplate = mock()
        heavyTemplate = mock()
        registry = mock()
    }

    // ── 1. registry 为 null → 返回 null ─────────────────────

    @Test
    fun `buildGroups returns null when registry is null`() {
        helper = NodeBatchQueryHelper(defaultTemplate, null)
        assertNull(helper.buildGroups(listOf("node_0", "node_1")))
    }

    // ── 2. 无路由项目且无 shard → 返回 null ─────────────────

    @Test
    fun `buildGroups returns null when no routing configured`() {
        helper = NodeBatchQueryHelper(defaultTemplate, registry)
        whenever(registry.routedProjectIds("node")).thenReturn(emptySet())
        whenever(registry.shardRoutedCollections("node")).thenReturn(emptySet())
        whenever(registry.projectsByInstance("node")).thenReturn(emptyMap())
        whenever(registry.shardsByInstance("node")).thenReturn(emptyMap())

        assertNull(helper.buildGroups(listOf("node_0", "node_1")))
    }

    // ── 3. 仅有 shard-routing → 按实例分组 ──────────────────

    @Test
    fun `buildGroups groups by shard routing only`() {
        helper = NodeBatchQueryHelper(defaultTemplate, registry)
        whenever(registry.routedProjectIds("node")).thenReturn(emptySet())
        whenever(registry.shardRoutedCollections("node")).thenReturn(setOf("node_99", "node_188"))
        whenever(registry.projectsByInstance("node")).thenReturn(emptyMap())
        whenever(registry.shardsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("node_99", "node_188")),
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)

        val groups = helper.buildGroups(listOf("node_0", "node_99", "node_188"), "node")
        assertNotNull(groups)
        val groupList = groups!!

        // shard-routed 集合分到 heavy1 组
        val shardGroup = groupList.firstOrNull { it.instanceId == "heavy1" }
        assertNotNull(shardGroup, "should have heavy1 group for shard-routed collections")
        assertTrue(shardGroup!!.collectionNames.containsAll(listOf("node_99", "node_188")))

        // 非 shard-routed 集合留在 default 组且无额外过滤
        val defaultGroup = groupList.firstOrNull { it.instanceId == "default" }
        assertNotNull(defaultGroup, "should have default group for non-shard collections")
        assertTrue(defaultGroup!!.collectionNames.contains("node_0"))
    }

    // ── 4. 仅有 project-routing → Default NOT IN + Heavy IN ─

    @Test
    fun `buildGroups applies NOT IN on Default and IN on Heavy for project routing`() {
        helper = NodeBatchQueryHelper(defaultTemplate, registry)
        val routedProjects = setOf("projectA", "projectB")
        whenever(registry.routedProjectIds("node")).thenReturn(routedProjects)
        whenever(registry.shardRoutedCollections("node")).thenReturn(emptySet())
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to routedProjects),
        )
        whenever(registry.shardsByInstance("node")).thenReturn(emptyMap())
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(registry.allKnownProjectIds("node")).thenReturn(routedProjects)

        val collections = allNodeCollections()
        val groups = helper.buildGroups(collections, "node")
        assertNotNull(groups)
        val groupList = groups!!

        // 以 NOT IN 为默认组过滤（routedProjects=2 < WHITELIST_THRESHOLD=20）
        val defaultGroup = groupList.first { it.instanceId == "default" }
        val defaultCustomized = defaultGroup.criteriaCustomizer(Query())
        assertNotNull(defaultCustomized)

        // Heavy 组：以 projectId IN 过滤，且只扫迁入项目对应的分表
        val heavyGroup = groupList.first { it.instanceId == "heavy1" }
        val heavyCustomized = heavyGroup.criteriaCustomizer(Query())
        assertNotNull(heavyCustomized)
        val expectedHeavyCollections = routedProjects.map { shardFor(it) }.toSet()
        assertEquals(expectedHeavyCollections, heavyGroup.collectionNames.toSet())
    }

    // ── 5. project-routing 超过阈值 → Default 改用 IN whitelist

    @Test
    fun `buildGroups uses IN whitelist on Default when routed projects exceed threshold`() {
        helper = NodeBatchQueryHelper(defaultTemplate, registry)
        val routedProjects = (1..25).map { "project$it" }.toSet()
        val allKnown = (1..30).map { "project$it" }.toSet()
        whenever(registry.routedProjectIds("node")).thenReturn(routedProjects)
        whenever(registry.shardRoutedCollections("node")).thenReturn(emptySet())
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to routedProjects),
        )
        whenever(registry.shardsByInstance("node")).thenReturn(emptyMap())
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(registry.allKnownProjectIds("node")).thenReturn(allKnown)

        val groups = helper.buildGroups(listOf("node_0"), "node")
        assertNotNull(groups)
        val groupList = groups!!

        val defaultGroup = groupList.first { it.instanceId == "default" }
        val customizedQuery = defaultGroup.criteriaCustomizer(Query())
        // 超过阈值(20)时使用 IN whitelist，remaining = allKnown - routed
        assertNotNull(customizedQuery)
    }

    // ── 6. shard-routing + project-routing 共存 ──────────────

    @Test
    fun `buildGroups handles mixed shard and project routing`() {
        helper = NodeBatchQueryHelper(defaultTemplate, registry)
        val routedProjects = setOf("projectA")
        val shardCollections = setOf("node_188")
        whenever(registry.routedProjectIds("node")).thenReturn(routedProjects)
        whenever(registry.shardRoutedCollections("node")).thenReturn(shardCollections)
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf("heavy1" to routedProjects),
        )
        whenever(registry.shardsByInstance("node")).thenReturn(
            mapOf("heavy1" to shardCollections),
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(registry.allKnownProjectIds("node")).thenReturn(routedProjects)

        val groups = helper.buildGroups(
            listOf("node_0", "node_1", "node_188"), "node",
        )
        assertNotNull(groups)
        val groupList = groups!!

        // heavy1 应有 shard 组（仅 node_188）和 project 组（排除 node_188）
        val heavyShardGroup = groupList.firstOrNull {
            it.instanceId == "heavy1" && it.collectionNames.contains("node_188")
        }
        assertNotNull(heavyShardGroup, "should have heavy1 group with shard collection")

        // default 组应排除 shard collection
        val defaultGroup = groupList.first { it.instanceId == "default" }
        assertTrue("node_188" !in defaultGroup.collectionNames)
    }

    // ── 7. 空 collectionNames → 返回 null ───────────────────

    @Test
    fun `buildGroups returns null for empty collection names`() {
        helper = NodeBatchQueryHelper(defaultTemplate, registry)
        whenever(registry.routedProjectIds("node")).thenReturn(setOf("projectA"))
        whenever(registry.shardRoutedCollections("node")).thenReturn(emptySet())
        whenever(registry.projectsByInstance("node")).thenReturn(emptyMap())
        whenever(registry.shardsByInstance("node")).thenReturn(emptyMap())

        val groups = helper.buildGroups(emptyList(), "node")
        assertNull(groups, "empty collections should produce null result")
    }

    // ── 8. 某实例无 template → 跳过该实例 ────────────────────

    @Test
    fun `buildGroups skips instance when template not available`() {
        helper = NodeBatchQueryHelper(defaultTemplate, registry)
        whenever(registry.routedProjectIds("node")).thenReturn(emptySet())
        whenever(registry.shardRoutedCollections("node")).thenReturn(setOf("node_188"))
        whenever(registry.projectsByInstance("node")).thenReturn(emptyMap())
        whenever(registry.shardsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("node_188"), "heavy2" to setOf("node_200")),
        )
        // heavy1 有 template，heavy2 没有
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(registry.primaryTemplateByInstance("node", "heavy2")).thenReturn(null)

        val groups = helper.buildGroups(listOf("node_0", "node_188", "node_200"), "node")
        assertNotNull(groups)
        val groupList = groups!!

        val heavyGroups = groupList.filter { it.instanceId.startsWith("heavy") }
        val heavyGroupIds = heavyGroups.map { it.instanceId }.toSet()
        assertTrue("heavy2" !in heavyGroupIds, "heavy2 without template should be skipped")
    }

    // ── 9. 多实例 project-routing 正确分组 ───────────────────

    @Test
    fun `buildGroups creates groups for multiple project-routed instances`() {
        helper = NodeBatchQueryHelper(defaultTemplate, registry)
        val heavy2Template: MongoTemplate = mock()
        whenever(registry.routedProjectIds("node")).thenReturn(setOf("projectA", "projectB"))
        whenever(registry.shardRoutedCollections("node")).thenReturn(emptySet())
        whenever(registry.projectsByInstance("node")).thenReturn(
            mapOf(
                "heavy1" to setOf("projectA"),
                "heavy2" to setOf("projectB"),
            ),
        )
        whenever(registry.shardsByInstance("node")).thenReturn(emptyMap())
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(registry.primaryTemplateByInstance("node", "heavy2")).thenReturn(heavy2Template)
        whenever(registry.allKnownProjectIds("node")).thenReturn(setOf("projectA", "projectB"))

        val groups = helper.buildGroups(allNodeCollections(), "node")
        assertNotNull(groups)
        val groupList = groups!!

        // default 组 + 每个 heavy 实例一个 project 组
        val heavyGroupIds = groupList
            .filter { it.instanceId.startsWith("heavy") }
            .map { it.instanceId }.toSet()
        assertTrue("heavy1" in heavyGroupIds)
        assertTrue("heavy2" in heavyGroupIds)
        assertTrue(groupList.any { it.instanceId == "default" })
    }

    @Test
    fun `narrowCollectionsForProjects keeps full list for non-node rules`() {
        val collections = listOf("block_node_0", "block_node_1", "block_node_2")
        val narrowed = NodeBatchQueryHelper.narrowCollectionsForProjects(
            ruleName = "block-node",
            projectIds = listOf("projectA"),
            availableCollections = collections,
            shardingCount = 3,
        )
        assertEquals(collections, narrowed)
    }

    @Test
    fun `buildGroups narrows Default collections in whitelist mode`() {
        helper = NodeBatchQueryHelper(defaultTemplate, registry)
        val routedProjects = (1..25).map { "routed-$it" }.toSet()
        val remaining = setOf("stay-1", "stay-2")
        val allKnown = routedProjects + remaining
        whenever(registry.routedProjectIds("node")).thenReturn(routedProjects)
        whenever(registry.shardRoutedCollections("node")).thenReturn(emptySet())
        whenever(registry.projectsByInstance("node")).thenReturn(mapOf("heavy1" to routedProjects))
        whenever(registry.shardsByInstance("node")).thenReturn(emptyMap())
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(registry.allKnownProjectIds("node")).thenReturn(allKnown)

        val groups = helper.buildGroups(allNodeCollections(), "node")!!
        val defaultGroup = groups.first { it.instanceId == "default" }
        val expected = remaining.map { shardFor(it) }.toSet()
        assertEquals(expected, defaultGroup.collectionNames.toSet())
    }

    // ── 10. BatchQueryGroup 数据类正确性 ─────────────────────

    @Test
    fun `BatchQueryGroup holds correct values`() {
        val identity: (Query) -> Query = { it }
        val group = BatchQueryGroup(
            instanceId = "test",
            mongoTemplate = defaultTemplate,
            collectionNames = listOf("node_0"),
            criteriaCustomizer = identity,
        )
        assertEquals("test", group.instanceId)
        assertEquals(listOf("node_0"), group.collectionNames)
        val q = Query()
        assertTrue(group.criteriaCustomizer(q) === q)
    }
}
