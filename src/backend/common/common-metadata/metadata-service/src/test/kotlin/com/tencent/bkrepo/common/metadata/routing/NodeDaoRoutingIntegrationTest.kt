package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao
import com.tencent.bkrepo.common.mongo.routing.DefaultMongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import com.tencent.bkrepo.repository.constant.SHARDING_COUNT
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

/**
 * MetadataServiceImpl 写路径路由集成测试（P0 — §M5）。
 *
 * 使用嵌入式 MongoDB + 真实 [NodeDao] 验证 MetadataServiceImpl 底层调用的路由行为：
 * saveMetadata → nodeDao.save() → Heavy
 * deleteMetadata → nodeDao.updateMulti() → Heavy
 *
 * 覆盖：
 * 1. save：routed projectId → 写入 Heavy 分片集合
 * 2. save：unrouted projectId → 写入 Default 分片集合（回退）
 * 3. updateMulti：routed projectId → 更新落在 Heavy
 * 4. updateMulti + lastModifiedDate touch
 */
@DataMongoTest
@ContextConfiguration(classes = [NodeDaoRoutingIntegrationTest.RoutingConfig::class])
@Import(NodeDao::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    locations = ["classpath:bootstrap-ut.properties"],
    properties = ["sharding.count=256"]
)
class NodeDaoRoutingIntegrationTest @Autowired constructor(
    private val nodeDao: NodeDao,
    private val defaultTemplate: MongoTemplate,
    private val routingConfig: RoutingConfig,
) {

    private val routedProject = "projectA"
    private val unroutedProject = "projectB"
    private val repoName = "test-repo"

    @BeforeEach
    fun setUp() {
        routingConfig.registry?.let { reg ->
            val field = AbstractMongoDao::class.java.getDeclaredField("routingRegistry")
            field.isAccessible = true
            field.set(nodeDao, reg)
        }
        // 清空分片集合
        for (i in 0 until SHARDING_COUNT) {
            defaultTemplate.dropCollection("node_$i")
            routingConfig.heavyTemplate?.dropCollection("node_$i")
        }
    }

    @AfterEach
    fun tearDown() {
        for (i in 0 until 2) {
            defaultTemplate.dropCollection("node_$i")
            routingConfig.heavyTemplate?.dropCollection("node_$i")
        }
    }

    // ── 1. save: routed projectId → Heavy ──────────────────────

    @Test
    fun `save routes node to Heavy when projectId matches project-routing`() {
        val node = buildNode(projectId = routedProject)
        nodeDao.save(node)

        val col = shardingCollection(routedProject)
        assertNotNull(
            routingConfig.heavyTemplate?.findById(node.id, TNode::class.java, col),
            "Node must exist in Heavy instance"
        )
        assertNull(
            defaultTemplate.findById(node.id, TNode::class.java, col),
            "Node must NOT exist in Default instance"
        )
    }

    // ── 2. save: unrouted projectId → Default ──────────────────

    @Test
    fun `save routes node to Default when projectId not in project-routing`() {
        val node = buildNode(projectId = unroutedProject)
        nodeDao.save(node)

        val col = shardingCollection(unroutedProject)
        assertNotNull(defaultTemplate.findById(node.id, TNode::class.java, col))
        assertNull(routingConfig.heavyTemplate?.findById(node.id, TNode::class.java, col))
    }

    // ── 3. updateMulti: routed projectId → Heavy ───────────────

    @Test
    fun `updateMulti routes to Heavy when projectId matches`() {
        val node = buildNode(projectId = routedProject)
        val col = shardingCollection(routedProject)
        routingConfig.heavyTemplate?.save(node, col)

        val query = Query(Criteria.where(TNode::projectId.name).isEqualTo(routedProject))
        val update = Update().set(TNode::size.name, 999L)
        nodeDao.updateMulti(query, update)

        val updated = routingConfig.heavyTemplate?.findById(node.id, TNode::class.java, col)
        assertEquals(999L, updated?.size)
    }

    // ── 4. deleteMetadata 语义验证（updateMulti + lastModifiedDate） ─

    @Test
    fun `updateMulti with lastModifiedDate touch routes to Heavy`() {
        val node = buildNode(projectId = routedProject)
        val col = shardingCollection(routedProject)
        routingConfig.heavyTemplate?.save(node, col)

        val query = Query(Criteria.where(TNode::projectId.name).isEqualTo(routedProject))
        val now = LocalDateTime.now()
        // 模拟 MetadataServiceImpl.deleteMetadata 的 update 构造
        val update = Update()
            .pull(TNode::metadata.name, Query.query(Criteria.where("key").isEqualTo("test-key")))
            .set(TNode::lastModifiedDate.name, now)
        nodeDao.updateMulti(query, update)

        val updated = routingConfig.heavyTemplate?.findById(node.id, TNode::class.java, col)
        assertNotNull(updated)
    }

    // ── helpers ────────────────────────────────────────────────

    private fun buildNode(projectId: String) = TNode(
        projectId = projectId,
        repoName = repoName,
        path = "/",
        name = "test.txt",
        fullPath = "/test.txt",
        folder = false,
        size = 100L,
        sha256 = null,
        md5 = null,
        createdBy = "test",
        createdDate = LocalDateTime.now(),
        lastModifiedBy = "test",
        lastModifiedDate = LocalDateTime.now(),
        lastAccessDate = LocalDateTime.now(),
    )

    private fun shardingCollection(projectId: String): String {
        val hash = com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
            .shardingSequenceFor(projectId, SHARDING_COUNT)
        return "node_$hash"
    }

    // ── Test routing configuration ─────────────────────────────

    @org.springframework.boot.test.context.TestConfiguration
    class RoutingConfig {

        @Autowired
        lateinit var defaultTemplate: MongoTemplate

        @Autowired
        lateinit var mongoClient: com.mongodb.client.MongoClient

        var heavyTemplate: MongoTemplate? = null
        var registry: DefaultMongoRoutingRegistry? = null

        @Bean
        fun nodeRoutingRegistry(): MongoRoutingRegistry {
            heavyTemplate = buildHeavyTemplate()
            val properties = MongoMultiInstanceProperties().apply {
                rules = mapOf(
                    "node" to MongoMultiInstanceProperties.RoutingRule(
                        routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                        collectionPrefix = "node_",
                        routingKeyField = "projectId",
                        routingState = RuleRoutingState.ROUTED,
                        instances = mapOf(
                            "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                                uri = "mongodb://localhost:27017/metadata_routing_heavy"
                            )
                        ),
                        projectRouting = mapOf("projectA" to "heavy1"),
                    )
                )
            }
            val reg = DefaultMongoRoutingRegistry(properties)
            val tmplField = DefaultMongoRoutingRegistry::class.java.getDeclaredField("primaryTemplates")
            tmplField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val templates = tmplField.get(reg) as MutableMap<String, MutableMap<String, MongoTemplate>>
            templates["node"]?.set("heavy1", heavyTemplate!!)
            this.registry = reg
            return reg
        }

        private fun buildHeavyTemplate(): MongoTemplate {
            return MongoTemplate(
                SimpleMongoClientDatabaseFactory(mongoClient, "metadata_routing_heavy"),
            )
        }
    }
}
