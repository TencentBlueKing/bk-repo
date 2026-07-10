package com.tencent.bkrepo.job.batch.utils

import com.tencent.bkrepo.common.metadata.routing.NodeBatchQueryHelper
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import com.tencent.bkrepo.job.JobTestConfiguration
import com.tencent.bkrepo.job.SHARDING_COUNT
import io.mockk.every
import io.mockk.mockk
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource

/**
 * Job 散发扫描集成测试（Spec §16.1）。
 *
 * 使用嵌入式 MongoDB 写入真实 node 文档，验证 Default 排除已迁出项目、
 * Heavy 仅扫描迁入项目的过滤条件。
 */
@DataMongoTest
@ContextConfiguration(classes = [JobTestConfiguration::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = ["de.flapdoodle.mongodb.embedded.version=4.0.2"])
class NodeBatchQueryJobIntegrationTest {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    private lateinit var registry: MongoRoutingRegistry
    private lateinit var helper: NodeBatchQueryHelper

    private val routedProjectId = "projectA"
    private val routedCollection =
        "node_${HashShardingUtils.shardingSequenceFor(routedProjectId, SHARDING_COUNT)}"
    private val defaultCollection = "node_0"

    @BeforeEach
    fun setUp() {
        mongoTemplate.dropCollection(routedCollection)
        mongoTemplate.dropCollection(defaultCollection)
        registry = mockk()
        every { registry.routedProjectIds("node") } returns setOf(routedProjectId)
        every { registry.shardRoutedCollections("node") } returns emptySet()
        every { registry.projectsByInstance("node") } returns mapOf("heavy1" to setOf(routedProjectId))
        every { registry.shardsByInstance("node") } returns emptyMap()
        every { registry.primaryTemplateByInstance("node", "heavy1") } returns mongoTemplate
        helper = NodeBatchQueryHelper(mongoTemplate, registry)
    }

    @AfterEach
    fun tearDown() {
        mongoTemplate.dropCollection(routedCollection)
        mongoTemplate.dropCollection(defaultCollection)
    }

    @Test
    fun `Default scan excludes routed project documents from embedded database`() {
        insertNode(routedProjectId, "routed.txt", routedCollection)
        insertNode("projectB", "default.txt", defaultCollection)

        val scanCollections = listOf(routedCollection, defaultCollection).distinct()
        val groups = helper.buildGroups(scanCollections)!!
        val defaultGroup = groups.first { group ->
            val filter = group.criteriaCustomizer(Query()).queryObject["projectId"] as? Map<*, *>
            filter?.containsKey("\$nin") == true
        }
        val heavyGroup = groups.first { group ->
            val filter = group.criteriaCustomizer(Query()).queryObject["projectId"] as? Map<*, *>
            filter?.containsKey("\$in") == true
        }

        val defaultHits = mongoTemplate.find(
            defaultGroup.criteriaCustomizer(Query()),
            Document::class.java,
            defaultCollection,
        )
        val heavyHits = mongoTemplate.find(
            heavyGroup.criteriaCustomizer(Query()),
            Document::class.java,
            routedCollection,
        )

        assertEquals(1, defaultHits.size)
        assertEquals("projectB", defaultHits.first().getString("projectId"))
        assertEquals(1, heavyHits.size)
        assertEquals(routedProjectId, heavyHits.first().getString("projectId"))
        assertTrue(groups.size >= 2)
    }

    @Test
    fun `shard-routed collection is excluded from Default scan group`() {
        val shardCollection = "node_188"
        mongoTemplate.dropCollection(shardCollection)
        every { registry.routedProjectIds("node") } returns emptySet()
        every { registry.shardRoutedCollections("node") } returns setOf(shardCollection)
        every { registry.projectsByInstance("node") } returns emptyMap()
        every { registry.shardsByInstance("node") } returns mapOf("heavy1" to setOf(shardCollection))

        insertNode("projectX", "shard-only.txt", shardCollection)
        insertNode("projectY", "default-only.txt", defaultCollection)

        val groups = helper.buildGroups(listOf(defaultCollection, shardCollection))!!
        val defaultGroup = groups.first { it.collectionNames == listOf(defaultCollection) }
        val shardGroup = groups.first { it.collectionNames == listOf(shardCollection) }

        val defaultHits = mongoTemplate.find(Query(), Document::class.java, defaultCollection)
        val shardHits = mongoTemplate.find(
            shardGroup.criteriaCustomizer(Query()),
            Document::class.java,
            shardCollection,
        )

        assertEquals(1, defaultHits.size)
        assertEquals("projectY", defaultHits.first().getString("projectId"))
        assertEquals(1, shardHits.size)
        assertEquals("projectX", shardHits.first().getString("projectId"))
        assertEquals(listOf(defaultCollection), defaultGroup.collectionNames)

        mongoTemplate.dropCollection(shardCollection)
    }

    private fun insertNode(projectId: String, name: String, col: String) {
        mongoTemplate.insert(
            Document().apply {
                put("projectId", projectId)
                put("name", name)
            },
            col,
        )
    }
}
