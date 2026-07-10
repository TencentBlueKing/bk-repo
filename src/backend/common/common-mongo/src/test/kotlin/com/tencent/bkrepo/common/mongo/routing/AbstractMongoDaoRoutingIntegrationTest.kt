package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.RuleRoutingState
import com.tencent.bkrepo.common.mongo.dao.AbstractMongoDao
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo

/**
 * AbstractMongoDao 路由集成测试（P0 — §M4 核心）。
 *
 * 使用嵌入式 MongoDB 验证 [AbstractMongoDao] 的写操作（save/updateMulti/updateFirst/insert）
 * 在 PROJECT 路由开启时写入正确的目标实例。
 *
 * 覆盖场景：
 * 1. save：projectId 命中 project-routing → 写入 Heavy 实例
 * 2. updateMulti：projectId 命中 → 更新落在 Heavy
 * 3. updateFirst：projectId 命中 → 更新落在 Heavy
 * 4. insert：projectId 命中 → 文档在 Heavy
 * 5. save：projectId 未命中 → 写入 Default（回退）
 * 6. save：routing-state=OFF → 写入 Default（未启用路由）
 */
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AbstractMongoDaoRoutingIntegrationTest {

    @Autowired
    lateinit var defaultTemplate: MongoTemplate

    @Autowired
    lateinit var mongoClient: com.mongodb.client.MongoClient

    private lateinit var heavyTemplate: MongoTemplate
    private lateinit var registry: DefaultMongoRoutingRegistry
    private lateinit var dao: RoutingTestDao

    private val collectionName = "dao_routing_test"
    private val routedProject = "projectA"
    private val unroutedProject = "projectB"

    @BeforeEach
    fun setUp() {
        heavyTemplate = buildHeavyTemplate()
        registry = buildRoutingRegistry()
        dao = RoutingTestDao(defaultTemplate).apply { injectRegistry(registry) }
        defaultTemplate.dropCollection(collectionName)
        heavyTemplate.dropCollection(collectionName)
    }

    @AfterEach
    fun tearDown() {
        defaultTemplate.dropCollection(collectionName)
        heavyTemplate.dropCollection(collectionName)
    }

    // ── 1. save: routed projectId → Heavy ──────────────────────

    @Test
    fun `save writes to Heavy when projectId matches project-routing in ROUTED state`() {
        val entity = TestEntity(projectId = routedProject, name = "routed-save")
        val saved = dao.save(entity)

        assertNotNull(heavyTemplate.findById(saved.id, TestEntity::class.java, collectionName),
            "Entity must exist in Heavy instance")
        assertNull(defaultTemplate.findById(saved.id, TestEntity::class.java, collectionName),
            "Entity must NOT exist in Default instance")
    }

    // ── 2. updateMulti: routed projectId → Heavy ───────────────

    @Test
    fun `updateMulti routes to Heavy when projectId matches`() {
        val entity = TestEntity(projectId = routedProject, name = "multi-before")
        val saved = heavyTemplate.save(entity, collectionName)

        val query = Query(Criteria.where("projectId").isEqualTo(routedProject))
        val update = Update().set("name", "multi-after")
        dao.updateMulti(query, update)

        val updated = heavyTemplate.findById(saved.id, TestEntity::class.java, collectionName)
        assertEquals("multi-after", updated?.name)
    }

    // ── 3. updateFirst: routed projectId → Heavy ───────────────

    @Test
    fun `updateFirst routes to Heavy when projectId matches`() {
        val entity = TestEntity(projectId = routedProject, name = "first-before")
        val saved = heavyTemplate.save(entity, collectionName)

        val query = Query(
            Criteria.where("_id").isEqualTo(saved.id)
                .and("projectId").isEqualTo(routedProject)
        )
        val update = Update().set("name", "first-after")
        dao.updateFirst(query, update)

        val updated = heavyTemplate.findById(saved.id, TestEntity::class.java, collectionName)
        assertEquals("first-after", updated?.name)
    }

    // ── 4. insert: routed projectId → Heavy ────────────────────

    @Test
    fun `insert writes to Heavy when projectId matches`() {
        val entity = TestEntity(projectId = routedProject, name = "inserted")
        val inserted = dao.insert(entity)

        assertNotNull(heavyTemplate.findById(inserted.id, TestEntity::class.java, collectionName))
        assertNull(defaultTemplate.findById(inserted.id, TestEntity::class.java, collectionName))
    }

    // ── 5. save: unrouted projectId → Default fallback ─────────

    @Test
    fun `save writes to Default when projectId not in project-routing`() {
        val entity = TestEntity(projectId = unroutedProject, name = "unrouted-save")
        val saved = dao.save(entity)

        assertNotNull(defaultTemplate.findById(saved.id, TestEntity::class.java, collectionName))
        assertNull(heavyTemplate.findById(saved.id, TestEntity::class.java, collectionName))
    }

    // ── 6. OFF state: routing disabled → Default ───────────────

    @Test
    fun `save writes to Default when routing-state is OFF`() {
        val offDao = RoutingTestDao(defaultTemplate).apply {
            injectRegistry(buildOffRegistry())
        }
        val entity = TestEntity(projectId = routedProject, name = "off-save")
        val saved = offDao.save(entity)

        assertNotNull(defaultTemplate.findById(saved.id, TestEntity::class.java, collectionName))
        assertNull(heavyTemplate.findById(saved.id, TestEntity::class.java, collectionName))
        defaultTemplate.dropCollection(collectionName)
    }

    // ── 7. read route: ROUTED + routed projectId → 读 Heavy（§2.11 禁止回退 Default）──

    @Test
    fun `read routes to Heavy when projectId matches in ROUTED state`() {
        heavyTemplate.save(TestEntity(projectId = routedProject, name = "routed-read"), collectionName)
        defaultTemplate.dropCollection(collectionName)

        val read = dao.find(Query(Criteria.where("projectId").isEqualTo(routedProject)))
        assertEquals(1, read.size)
        assertEquals("routed-read", read.first().name)
    }

    // ── 8. read route: unrouted projectId → 读 Default ──────────

    @Test
    fun `read routes to Default when projectId not in project-routing`() {
        defaultTemplate.save(TestEntity(projectId = unroutedProject, name = "unrouted-read"), collectionName)

        val read = dao.find(Query(Criteria.where("projectId").isEqualTo(unroutedProject)))
        assertEquals(1, read.size)
        assertEquals("unrouted-read", read.first().name)
    }

    // ── helper methods ─────────────────────────────────────────

    private fun buildHeavyTemplate(): MongoTemplate {
        return MongoTemplate(
            SimpleMongoClientDatabaseFactory(mongoClient, "routing_heavy_test_db"),
            defaultTemplate.converter,
        )
    }

    private fun buildRoutingRegistry(): DefaultMongoRoutingRegistry {
        val properties = MongoMultiInstanceProperties().apply {
            rules = mapOf(
                "node" to MongoMultiInstanceProperties.RoutingRule(
                    routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                    collectionPrefix = collectionName,
                    routingKeyField = "projectId",
                    routingState = RuleRoutingState.ROUTED,
                    routingEffectiveAt = Instant.EPOCH,
                    instances = mapOf(
                        "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                            uri = "mongodb://localhost:27017/routing_heavy_test_db"
                        )
                    ),
                    projectRouting = mapOf(routedProject to "heavy1"),
                )
            )
        }
        val reg = DefaultMongoRoutingRegistry(properties)
        // 用真实模板覆盖 URI 创建的模板
        val tmplField = DefaultMongoRoutingRegistry::class.java.getDeclaredField("primaryTemplates")
        tmplField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val templates = tmplField.get(reg) as MutableMap<String, MutableMap<String, MongoTemplate>>
        templates["node"]?.set("heavy1", heavyTemplate)
        return reg
    }

    private fun buildOffRegistry(): DefaultMongoRoutingRegistry {
        val properties = MongoMultiInstanceProperties().apply {
            rules = mapOf(
                "node" to MongoMultiInstanceProperties.RoutingRule(
                    routingType = MongoMultiInstanceProperties.RoutingType.PROJECT,
                    collectionPrefix = collectionName,
                    routingKeyField = "projectId",
                    routingState = RuleRoutingState.OFF,
                    instances = mapOf(
                        "heavy1" to MongoMultiInstanceProperties.RoutingRule.InstanceConfig(
                            uri = "mongodb://localhost:27017/routing_heavy_test_db"
                        )
                    ),
                    projectRouting = mapOf(routedProject to "heavy1"),
                )
            )
        }
        return DefaultMongoRoutingRegistry(properties)
    }

    companion object {
        private fun RoutingTestDao.injectRegistry(registry: MongoRoutingRegistry) {
            val field = AbstractMongoDao::class.java.getDeclaredField("routingRegistry")
            field.isAccessible = true
            field.set(this, registry)
        }
    }
}

// ── test model & DAO ──────────────────────────────────────────

@Document(collection = "dao_routing_test")
data class TestEntity(
    @Id
    val id: String? = null,
    val projectId: String,
    val name: String,
)

class RoutingTestDao(private val template: MongoTemplate) : AbstractMongoDao<TestEntity>() {

    override fun determineMongoTemplate(): MongoTemplate = template

    override fun determineCollectionName(entity: TestEntity): String = "dao_routing_test"
    override fun determineCollectionName(query: Query): String = "dao_routing_test"
    override fun determineCollectionName(aggregation: Aggregation): String = "dao_routing_test"
}
