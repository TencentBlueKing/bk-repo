package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.RouteTarget
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import org.bson.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.mapping.Document as MongoDocument

/**
 * 双写 + 补偿全流程集成测试（Spec §16.1）。
 *
 * 使用嵌入式 MongoDB 验证：副路径写入失败 → 补偿入队 → replay 消费 → 目标实例落库。
 */
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoDualWriteFlowIntegrationTest {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    @Autowired
    lateinit var mongoConverter: MongoConverter

    private lateinit var registry: MongoRoutingRegistry
    private lateinit var compensationService: MongoDualWriteCompensationService

    private val targetCollection = "dual_write_flow_target"

    @BeforeEach
    fun setUp() {
        mongoTemplate.dropCollection(targetCollection)
        mongoTemplate.dropCollection(MongoDualWriteCompensationService.COLLECTION_NAME)
        registry = mock()
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(mongoTemplate)
        compensationService = MongoDualWriteCompensationService(
            mongoTemplate, mongoConverter, registry, MongoMultiInstanceProperties(),
        )
    }

    @AfterEach
    fun tearDown() {
        mongoTemplate.dropCollection(targetCollection)
        mongoTemplate.dropCollection(MongoDualWriteCompensationService.COLLECTION_NAME)
    }

    @Test
    fun `secondary failure enqueues compensation and replay writes to heavy instance`() {
        val entity = FlowNode("projectA", "compensated.txt")
        val route = WriteRoute(
            primary = mongoTemplate,
            secondary = mock(),
            secondaryTarget = RouteTarget(ruleName = "node", instanceName = "heavy1"),
            syncSecondaryWrite = true,
            routingKey = "projectA",
            ruleName = "node",
        )

        MongoDualWriteSupport.submitSecondaryWrite(
            route,
            targetCollection,
            enqueue = { compensationService.enqueueInsert(route, targetCollection, entity) },
            action = { throw RuntimeException("heavy primary unavailable") },
        )

        assertTrue(compensationService.hasPendingTasks("node", "projectA"))
        compensationService.consume()

        val pending = compensationService.countPendingTasks("node", "projectA")
        assertEquals(0L, pending)

        val stored = mongoTemplate.findAll(FlowNode::class.java, targetCollection)
        assertEquals(1, stored.size)
        assertEquals("compensated.txt", stored.first().name)

        val task = mongoTemplate.findAll(Document::class.java, MongoDualWriteCompensationService.COLLECTION_NAME)
        assertEquals(1, task.size)
        assertEquals("DONE", task.first().getString("status"))
        assertNotNull(task.first().getObjectId("_id"))
    }

    @MongoDocument(collection = "dual_write_flow_target")
    data class FlowNode(
        val projectId: String,
        val name: String,
    )
}
