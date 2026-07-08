package com.tencent.bkrepo.job.batch.utils

import com.tencent.bkrepo.common.metadata.routing.NodeBatchQueryHelper
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

/**
 * NodeBatchQueryHelper 分组生成逻辑单元测试（Spec §16.1）。
 *
 * 验证：
 * 1. 路由未开启时返回 null（Job 降级单实例模式）
 * 2. project-routing：Default 追加 NOT IN，Heavy 追加 IN
 * 3. shard-routing：Default 排除分片集合，Heavy 获取整表无 projectId 过滤
 * 4. project + shard 混合路由分组正确
 */
class NodeBatchQueryHelperTest {

    private val defaultTemplate: MongoTemplate = mockk(relaxed = true)
    private val heavy1Template: MongoTemplate = mockk(relaxed = true)
    private val heavy2Template: MongoTemplate = mockk(relaxed = true)

    private lateinit var registry: MongoRoutingRegistry

    @BeforeEach
    fun setUp() {
        registry = mockk()
    }

    private fun helper() = NodeBatchQueryHelper(defaultTemplate, registry)

    // ── 1. 路由未开启 ────────────────────────────────────────────────────────

    @Test
    fun `buildGroups returns null when no project or shard routing configured`() {
        every { registry.routedProjectIds("node") } returns emptySet()
        every { registry.shardRoutedCollections("node") } returns emptySet()

        assertNull(helper().buildGroups(listOf("node_0", "node_1")))
    }

    // ── 2. project-routing：Default NOT IN，Heavy IN ─────────────────────────

    @Test
    fun `buildGroups generates NOT-IN filter for Default and IN filter for Heavy on project-routing`() {
        val collections = listOf("node_0", "node_1", "node_2")
        every { registry.routedProjectIds("node") } returns setOf("projectA", "projectB")
        every { registry.shardRoutedCollections("node") } returns emptySet()
        every { registry.allKnownProjectIds("node") } returns setOf("projectA", "projectB", "projectC")
        every { registry.projectsByInstance("node") } returns mapOf(
            "heavy1" to setOf("projectA", "projectB")
        )
        every { registry.shardsByInstance("node") } returns emptyMap()
        every { registry.primaryTemplateByInstance("node", "heavy1") } returns heavy1Template

        val groups = helper().buildGroups(collections)!!

        // Default 组
        val defaultGroup = groups.first { it.mongoTemplate === defaultTemplate }
        assertEquals(collections, defaultGroup.collectionNames)
        // Default 组的 criteriaCustomizer 必须追加 NOT IN 过滤
        val defaultQuery = defaultGroup.criteriaCustomizer(Query())
        val projectIdFilter = defaultQuery.queryObject["projectId"] as? Map<*, *>
        assertNotNull(projectIdFilter)
        val notInValues = projectIdFilter!!.values.first()
        assertNotNull(notInValues) // $nin 存在

        // Heavy1 project 组
        val heavyGroup = groups.first { it.mongoTemplate === heavy1Template }
        val heavyQuery = heavyGroup.criteriaCustomizer(Query())
        val heavyFilter = heavyQuery.queryObject["projectId"] as? Map<*, *>
        assertNotNull(heavyFilter) // $in 存在
    }

    @Test
    fun `Default group contains all non-shard collections when project-routing only`() {
        val collections = listOf("node_0", "node_1")
        every { registry.routedProjectIds("node") } returns setOf("projectA")
        every { registry.shardRoutedCollections("node") } returns emptySet()
        every { registry.projectsByInstance("node") } returns mapOf("heavy1" to setOf("projectA"))
        every { registry.shardsByInstance("node") } returns emptyMap()
        every { registry.primaryTemplateByInstance("node", "heavy1") } returns heavy1Template

        val groups = helper().buildGroups(collections)!!
        val defaultGroup = groups.first { it.mongoTemplate === defaultTemplate }
        assertEquals(collections.sorted(), defaultGroup.collectionNames.sorted())
    }

    // ── 3. shard-routing：Default 排除整表，Heavy 无 projectId 过滤 ───────────

    @Test
    fun `buildGroups excludes shard collection from Default and adds it to Heavy without projectId filter`() {
        val collections = listOf("node_0", "node_188")
        every { registry.routedProjectIds("node") } returns emptySet()
        every { registry.shardRoutedCollections("node") } returns setOf("node_188")
        every { registry.projectsByInstance("node") } returns emptyMap()
        every { registry.shardsByInstance("node") } returns mapOf("heavy1" to setOf("node_188"))
        every { registry.primaryTemplateByInstance("node", "heavy1") } returns heavy1Template

        val groups = helper().buildGroups(collections)!!

        // Default 只包含 node_0，不包含 node_188
        val defaultGroup = groups.first { it.mongoTemplate === defaultTemplate }
        assertEquals(listOf("node_0"), defaultGroup.collectionNames)

        // Heavy 组包含 node_188，criteriaCustomizer 不追加 projectId（整表迁移）
        val heavyShardGroup = groups.first { it.mongoTemplate === heavy1Template }
        assertEquals(listOf("node_188"), heavyShardGroup.collectionNames)
        val shardQuery = heavyShardGroup.criteriaCustomizer(Query())
        // 不应有 projectId 过滤条件
        assertNull(shardQuery.queryObject["projectId"])
    }

    // ── 4. project + shard 混合 ───────────────────────────────────────────────

    @Test
    fun `buildGroups handles mixed project and shard routing across two Heavy instances`() {
        val collections = listOf("node_0", "node_65", "node_188")
        every { registry.routedProjectIds("node") } returns setOf("projectA")
        every { registry.shardRoutedCollections("node") } returns setOf("node_188")
        every { registry.projectsByInstance("node") } returns mapOf("heavy1" to setOf("projectA"))
        every { registry.shardsByInstance("node") } returns mapOf("heavy2" to setOf("node_188"))
        every { registry.primaryTemplateByInstance("node", "heavy1") } returns heavy1Template
        every { registry.primaryTemplateByInstance("node", "heavy2") } returns heavy2Template

        val groups = helper().buildGroups(collections)!!

        // Default：仅包含非 shard 集合，条件追加 NOT IN projectA
        val defaultGroup = groups.first { it.mongoTemplate === defaultTemplate }
        assertEquals(setOf("node_0", "node_65"), defaultGroup.collectionNames.toSet())

        // heavy1：project-routing，扫描非 shard 集合，追加 IN [projectA]
        val heavy1Group = groups.first { it.mongoTemplate === heavy1Template }
        assertNull(heavy1Group.collectionNames.find { it == "node_188" })

        // heavy2：shard-routing，只扫描 node_188，无 projectId 过滤
        val heavy2Group = groups.first { it.mongoTemplate === heavy2Template }
        assertEquals(listOf("node_188"), heavy2Group.collectionNames)
        assertNull(heavy2Group.criteriaCustomizer(Query()).queryObject["projectId"])
    }

    @Test
    fun `buildGroups result count includes Default plus each Heavy instance group`() {
        val collections = listOf("node_0", "node_1")
        every { registry.routedProjectIds("node") } returns setOf("p1", "p2")
        every { registry.shardRoutedCollections("node") } returns emptySet()
        every { registry.projectsByInstance("node") } returns mapOf(
            "heavy1" to setOf("p1"),
            "heavy2" to setOf("p2"),
        )
        every { registry.shardsByInstance("node") } returns emptyMap()
        every { registry.primaryTemplateByInstance("node", "heavy1") } returns heavy1Template
        every { registry.primaryTemplateByInstance("node", "heavy2") } returns heavy2Template

        val groups = helper().buildGroups(collections)!!
        // Default + heavy1 project group + heavy2 project group = 3
        assertEquals(3, groups.size)
    }

    // ── 5. 白名单模式：已迁出项目 > 20 时切换为 IN remaining（§3.7.2）────────

    @Test
    fun `Default group uses IN remaining when routed project count exceeds whitelist threshold`() {
        val routed = (1..21).map { "routed-$it" }.toSet()
        val remaining = setOf("stay-1", "stay-2")
        val collections = listOf("node_0")
        every { registry.routedProjectIds("node") } returns routed
        every { registry.shardRoutedCollections("node") } returns emptySet()
        every { registry.allKnownProjectIds("node") } returns routed + remaining
        every { registry.projectsByInstance("node") } returns mapOf("heavy1" to routed)
        every { registry.shardsByInstance("node") } returns emptyMap()
        every { registry.primaryTemplateByInstance("node", "heavy1") } returns heavy1Template

        val defaultGroup = helper().buildGroups(collections)!!
            .first { it.mongoTemplate === defaultTemplate }
        val filter = defaultGroup.criteriaCustomizer(Query()).queryObject["projectId"] as Map<*, *>
        val inValues = (filter["\$in"] as? Iterable<*>)?.map { it.toString() }?.toSet()
        assertEquals(remaining, inValues)
    }

    @Test
    fun `Default group uses NOT IN when routed project count at whitelist threshold`() {
        val routed = (1..20).map { "routed-$it" }.toSet()
        val collections = listOf("node_0")
        every { registry.routedProjectIds("node") } returns routed
        every { registry.shardRoutedCollections("node") } returns emptySet()
        every { registry.projectsByInstance("node") } returns mapOf("heavy1" to routed)
        every { registry.shardsByInstance("node") } returns emptyMap()
        every { registry.primaryTemplateByInstance("node", "heavy1") } returns heavy1Template

        val defaultGroup = helper().buildGroups(collections)!!
            .first { it.mongoTemplate === defaultTemplate }
        val filter = defaultGroup.criteriaCustomizer(Query()).queryObject["projectId"] as Map<*, *>
        assertNotNull(filter["\$nin"])
    }
}
