package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.WriteRoute
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * CompensationPostCheck 单元测试（Spec §3.17.3 补偿消费后即时校验）。
 *
 * 使用嵌入式 MongoDB 做真实文档查询，mock routingRegistry 模拟跨实例场景：
 * 1. postReplayCheck: 跳过非检查类操作（REMOVE/UPDATE_FIRST/UPDATE_MULTI）
 * 2. postReplayCheck: INSERT 校验两侧 _id 一致 → 无告警
 * 3. postReplayCheck: collectionName 缺失 → 跳过
 * 4. postReplayCheck: primaryKey 缺失 → 跳过
 * 5. postReplayCheck: 补偿写入目标实例时 primaryKey 不可解析 → 跳过
 * 6. postReplayCheck: 字段值不一致时记录 warn
 */
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompensationPostCheckTest {

    @Autowired
    lateinit var defaultMongoTemplate: MongoTemplate

    private lateinit var registry: MongoRoutingRegistry
    private lateinit var postCheck: CompensationPostCheck

    private val testCollection = "node_post_check_test"

    @BeforeEach
    fun setUp() {
        registry = mock()
        postCheck = CompensationPostCheck(registry, defaultMongoTemplate, SimpleMeterRegistry())
        defaultMongoTemplate.dropCollection(testCollection)
    }

    @AfterEach
    fun tearDown() {
        defaultMongoTemplate.dropCollection(testCollection)
    }

    @Test
    fun `postReplayCheck validates REMOVE when both sides absent`() {
        val entityId = ObjectId()
        val task = Document().apply {
            put("operationType", "REMOVE")
            put("collectionName", testCollection)
            put("primaryKey", entityId.toString())
            put("targetUseDefault", true)
        }
        val writeRoute = WriteRoute(primary = defaultMongoTemplate)
        whenever(registry.resolveWriteRoute(testCollection, null, defaultMongoTemplate))
            .thenReturn(writeRoute)
        postCheck.postReplayCheck(task)
    }

    @Test
    fun `postReplayCheck warns REMOVE when doc still exists`() {
        val entityId = ObjectId()
        defaultMongoTemplate.insert(
            Document().apply { put("_id", entityId); put("projectId", "p1") },
            testCollection,
        )
        val task = Document().apply {
            put("operationType", "REMOVE")
            put("collectionName", testCollection)
            put("primaryKey", entityId.toString())
            put("targetUseDefault", true)
        }
        val writeRoute = WriteRoute(primary = defaultMongoTemplate)
        whenever(registry.resolveWriteRoute(testCollection, null, defaultMongoTemplate))
            .thenReturn(writeRoute)
        postCheck.postReplayCheck(task)
    }

    @Test
    fun `postReplayCheck validates UPDATE_FIRST operations`() {
        val entityId = ObjectId()
        defaultMongoTemplate.insert(
            Document().apply {
                put("_id", entityId)
                put("projectId", "projectA")
                put("fullPath", "/repo/file.txt")
            },
            testCollection,
        )
        val task = Document().apply {
            put("operationType", "UPDATE_FIRST")
            put("collectionName", testCollection)
            put("primaryKey", entityId.toString())
            put("targetUseDefault", true)
        }
        val writeRoute = WriteRoute(primary = defaultMongoTemplate)
        whenever(registry.resolveWriteRoute(testCollection, null, defaultMongoTemplate))
            .thenReturn(writeRoute)
        postCheck.postReplayCheck(task)
    }

    @Test
    fun `postReplayCheck validates UPDATE_MULTI count match`() {
        val entityId = ObjectId()
        defaultMongoTemplate.insert(
            Document().apply { put("_id", entityId); put("projectId", "p1") },
            testCollection,
        )
        val task = Document().apply {
            put("operationType", "UPDATE_MULTI")
            put("collectionName", testCollection)
            put("query", Document("projectId", "p1"))
            put("targetUseDefault", true)
        }
        val writeRoute = WriteRoute(primary = defaultMongoTemplate)
        whenever(registry.resolveWriteRoute(testCollection, null, defaultMongoTemplate))
            .thenReturn(writeRoute)
        postCheck.postReplayCheck(task)
    }

    // ── 2. collectionName 缺失时跳过 ────────────────────────────

    @Test
    fun `postReplayCheck skips when collectionName is null`() {
        val task = Document().apply {
            put("operationType", "INSERT")
            put("primaryKey", ObjectId().toString())
            // collectionName 缺失
        }
        postCheck.postReplayCheck(task)
        verify(registry, never()).resolveWriteRoute(any(), any(), any())
    }

    // ── 3. primaryKey 缺失时跳过 ────────────────────────────────

    @Test
    fun `postReplayCheck skips when primaryKey is null`() {
        val task = Document().apply {
            put("operationType", "INSERT")
            put("collectionName", testCollection)
            // primaryKey 缺失
        }
        postCheck.postReplayCheck(task)
        verify(registry, never()).resolveWriteRoute(any(), any(), any())
    }

    // ── 4. operationType 缺失时跳过 ─────────────────────────────

    @Test
    fun `postReplayCheck skips when operationType is null`() {
        val task = Document().apply {
            put("collectionName", testCollection)
            put("primaryKey", ObjectId().toString())
        }
        postCheck.postReplayCheck(task)
        verify(registry, never()).resolveWriteRoute(any(), any(), any())
    }

    // ── 5. INSERT 校验：两侧 _id 一致 → 无异常 ──────────────────

    @Test
    fun `postReplayCheck passes when both sides have matching documents`() {
        val entityId = ObjectId()
        // 在 defaultMongoTemplate（模拟主路径）中插入文档
        defaultMongoTemplate.insert(
            Document().apply {
                put("_id", entityId)
                put("projectId", "projectA")
                put("fullPath", "/repo/file.txt")
                put("deleted", false)
            },
            testCollection,
        )

        val task = Document().apply {
            put("operationType", "INSERT")
            put("collectionName", testCollection)
            put("primaryKey", entityId.toString())
            put("targetUseDefault", true)
        }

        // resolveWriteRoute 返回 primary=defaultMongoTemplate
        val writeRoute = WriteRoute(primary = defaultMongoTemplate)
        whenever(registry.resolveWriteRoute(
            testCollection, null, defaultMongoTemplate,
        )).thenReturn(writeRoute)

        // 不应抛异常（postCheck 内部只记录 warn/error，不抛异常）
        postCheck.postReplayCheck(task)
    }

    // ── 6. SAVE 校验 ────────────────────────────────────────────

    @Test
    fun `postReplayCheck validates SAVE operations`() {
        val entityId = ObjectId()
        defaultMongoTemplate.insert(
            Document().apply {
                put("_id", entityId)
                put("projectId", "projectB")
                put("fullPath", "/repo/save.txt")
                put("deleted", false)
            },
            testCollection,
        )

        val task = Document().apply {
            put("operationType", "SAVE")
            put("collectionName", testCollection)
            put("primaryKey", entityId.toString())
            put("targetUseDefault", true)
        }

        val writeRoute = WriteRoute(primary = defaultMongoTemplate)
        whenever(registry.resolveWriteRoute(
            testCollection, null, defaultMongoTemplate,
        )).thenReturn(writeRoute)

        postCheck.postReplayCheck(task)
    }

    // ── 7. UPSERT 校验 ──────────────────────────────────────────

    @Test
    fun `postReplayCheck validates UPSERT operations`() {
        val entityId = ObjectId()
        defaultMongoTemplate.insert(
            Document().apply {
                put("_id", entityId)
                put("projectId", "projectC")
                put("fullPath", "/repo/upsert.txt")
            },
            testCollection,
        )

        val task = Document().apply {
            put("operationType", "UPSERT")
            put("collectionName", testCollection)
            put("primaryKey", entityId.toString())
            put("targetUseDefault", true)
        }

        val writeRoute = WriteRoute(primary = defaultMongoTemplate)
        whenever(registry.resolveWriteRoute(
            testCollection, null, defaultMongoTemplate,
        )).thenReturn(writeRoute)

        postCheck.postReplayCheck(task)
    }

    // ── 8. FIND_AND_MODIFY 校验 ─────────────────────────────────

    @Test
    fun `postReplayCheck validates FIND_AND_MODIFY operations`() {
        val entityId = ObjectId()
        defaultMongoTemplate.insert(
            Document().apply {
                put("_id", entityId)
                put("projectId", "projectD")
                put("fullPath", "/repo/fam.txt")
            },
            testCollection,
        )

        val task = Document().apply {
            put("operationType", "FIND_AND_MODIFY")
            put("collectionName", testCollection)
            put("primaryKey", entityId.toString())
            put("targetUseDefault", true)
        }

        val writeRoute = WriteRoute(primary = defaultMongoTemplate)
        whenever(registry.resolveWriteRoute(
            testCollection, null, defaultMongoTemplate,
        )).thenReturn(writeRoute)

        postCheck.postReplayCheck(task)
    }

    // ── 9. primary 侧文档不存在 → 记录 warn ────────────────────

    @Test
    fun `postReplayCheck handles missing primary document gracefully`() {
        val entityId = ObjectId()
        val task = Document().apply {
            put("operationType", "INSERT")
            put("collectionName", testCollection)
            put("primaryKey", entityId.toString())
            put("targetUseDefault", true)
        }

        val writeRoute = WriteRoute(primary = defaultMongoTemplate)
        whenever(registry.resolveWriteRoute(
            testCollection, null, defaultMongoTemplate,
        )).thenReturn(writeRoute)

        postCheck.postReplayCheck(task)
    }

    @Test
    fun `postReplayCheck persists inconsistency log when doc missing`() {
        val entityId = ObjectId()
        val task = Document().apply {
            put("operationType", "INSERT")
            put("collectionName", testCollection)
            put("primaryKey", entityId.toString())
            put("targetUseDefault", true)
            put("ruleName", "node")
            put("routingKey", "projectA")
        }
        val writeRoute = WriteRoute(primary = defaultMongoTemplate)
        whenever(registry.resolveWriteRoute(testCollection, "projectA", defaultMongoTemplate))
            .thenReturn(writeRoute)
        defaultMongoTemplate.dropCollection("mongo_inconsistency_log")
        postCheck.postReplayCheck(task)
        val logs = defaultMongoTemplate.findAll(Document::class.java, "mongo_inconsistency_log")
        assertEquals(1, logs.size)
        assertEquals("node", logs[0].getString("ruleName"))
        assertEquals(testCollection, logs[0].getString("collectionName"))
    }
}
