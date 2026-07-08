package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory

/**
 * MongoDualWriteSupport 嵌入式 MongoDB 集成测试（P2 — §M2/M4）。
 *
 * 在真实 DB 上验证双写执行器的主/副路径写入与补偿入队：
 * 1. executePrimaryWrite：正常写入 real primary template
 * 2. executePrimaryWrite：zombie 保护 fail-fast
 * 3. submitSecondaryWrite：同步写副路径成功
 * 4. submitSecondaryWrite：副路径失败 → 补偿入队
 */
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoDualWriteSupportEmbeddedTest {

    @Autowired
    lateinit var defaultTemplate: MongoTemplate

    private lateinit var heavyTemplate: MongoTemplate
    private lateinit var compensationService: MongoDualWriteCompensationService
    private lateinit var registry: MongoRoutingRegistry

    private val primaryCollection = "dual_write_primary_test"
    private val secondaryCollection = "dual_write_secondary_test"
    private val routedProject = "projectA"

    @BeforeEach
    fun setUp() {
        heavyTemplate = buildHeavyTemplate()
        compensationService = MongoDualWriteCompensationService(
            mongoTemplate = defaultTemplate,
            mongoConverter = defaultTemplate.converter,
            routingRegistry = heavyTemplateOnlyRegistry(),
            properties = MongoMultiInstanceProperties(),
        )
        registry = mock()
        whenever(registry.isProjectRoutedOut("node", routedProject)).thenReturn(false)
        defaultTemplate.dropCollection(primaryCollection)
        heavyTemplate.dropCollection(primaryCollection)
        defaultTemplate.dropCollection(secondaryCollection)
        defaultTemplate.dropCollection(MongoDualWriteCompensationService.COLLECTION_NAME)
    }

    @AfterEach
    fun tearDown() {
        defaultTemplate.dropCollection(primaryCollection)
        heavyTemplate.dropCollection(primaryCollection)
        defaultTemplate.dropCollection(secondaryCollection)
        defaultTemplate.dropCollection(MongoDualWriteCompensationService.COLLECTION_NAME)
    }

    // ── 1. executePrimaryWrite 正常写入 ─────────────────────────

    @Test
    fun `executePrimaryWrite writes to primary template successfully`() {
        val route = WriteRoute(primary = defaultTemplate)
        val doc = Document("_id", "doc-1").append("name", "test")

        MongoDualWriteSupport.executePrimaryWrite(
            route, primaryCollection, defaultTemplate, null,
        ) { it.save(doc, primaryCollection) }

        val found = defaultTemplate.findById("doc-1", Document::class.java, primaryCollection)
        assertEquals("test", found?.getString("name"))
    }

    // ── 2. zombie 副本写保护 fail-fast ─────────────────────────

    @Test
    fun `executePrimaryWrite throws when zombie replica detected`() {
        val zombieRegistry: MongoRoutingRegistry = mock()
        whenever(zombieRegistry.isProjectRoutedOut("node", routedProject)).thenReturn(true)
        val route = WriteRoute(primary = defaultTemplate, isDefaultInstance = true, ruleName = "node",
            routingKey = routedProject)

        val ex = assertThrows(IllegalStateException::class.java) {
            MongoDualWriteSupport.executePrimaryWrite(
                route, "node_0", defaultTemplate, zombieRegistry,
            ) { it.save(Document("_id", "z-1"), "node_0") }
        }
        assertTrue(ex.message!!.contains("WRITE_PROTECTION"))
    }

    // ── 3. submitSecondaryWrite 同步写副路径 ────────────────────

    @Test
    fun `submitSecondaryWrite writes to secondary synchronously`() {
        val route = WriteRoute(
            primary = defaultTemplate,
            secondary = heavyTemplate,
            syncSecondaryWrite = true,
        )
        val doc = Document("_id", "doc-sync").append("value", 42)

        var secondaryWritten = false
        MongoDualWriteSupport.submitSecondaryWrite(
            route, primaryCollection, enqueue = {},
            action = {
                it.save(doc, primaryCollection)
                secondaryWritten = true
            },
        )
        // 同步写完成标记
        assertTrue(secondaryWritten)
        val found = heavyTemplate.findById("doc-sync", Document::class.java, primaryCollection)
        assertEquals(42, found?.getInteger("value"))
    }

    // ── 4. submitSecondaryWrite 副路径失败 → 补偿入队 ──────────

    @Test
    fun `submitSecondaryWrite enqueues compensation on secondary failure`() {
        val route = WriteRoute(
            primary = defaultTemplate,
            secondary = heavyTemplate,
            secondaryTarget = com.tencent.bkrepo.common.mongo.api.routing.RouteTarget(
                ruleName = "node", instanceName = "heavy1"
            ),
            routingKey = routedProject,
            ruleName = "node",
        )
        val doc = Document("_id", "doc-comp").append("value", 100)

        // 写 primary 成功
        defaultTemplate.save(doc, primaryCollection)

        // 副路径模拟失败，应入队补偿
        MongoDualWriteSupport.submitSecondaryWrite(
            route, primaryCollection,
            enqueue = {
                compensationService.enqueueInsert(
                    route.toCompRoute(), primaryCollection, doc
                )
            },
            action = { throw RuntimeException("heavy unavailable") },
        )

        val pending = compensationService.countPendingTasks("node", routedProject)
        assertEquals(1L, pending)

        // 消费补偿
        compensationService.consume()
        assertEquals(0L, compensationService.countPendingTasks("node", routedProject))
    }

    // ── helpers ────────────────────────────────────────────────

    private fun buildHeavyTemplate(): MongoTemplate {
        val factory = defaultTemplate.mongoDbFactory as SimpleMongoClientDatabaseFactory
        val clientField = SimpleMongoClientDatabaseFactory::class.java.getDeclaredField("mongoClient")
        clientField.isAccessible = true
        val client = clientField.get(factory) as com.mongodb.client.MongoClient
        return MongoTemplate(SimpleMongoClientDatabaseFactory(client, "dual_write_heavy_test"))
    }

    private fun heavyTemplateOnlyRegistry(): MongoRoutingRegistry {
        val reg: MongoRoutingRegistry = mock()
        whenever(reg.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        return reg
    }

    private fun WriteRoute.toCompRoute() = copy(
        primary = heavyTemplate,
        secondary = null,
    )
}
