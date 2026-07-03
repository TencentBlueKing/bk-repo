package com.tencent.bkrepo.common.mongo.routing

import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter
import java.time.LocalDateTime

/**
 * CompensationHealthChecker 单元测试（嵌入式 MongoDB）。
 *
 * 验证 P0-3 修复：createdAt 落为 BSON Date 时健康检查能正确计算最老 PENDING 任务年龄。
 */
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompensationHealthCheckerTest {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    @Autowired
    lateinit var mongoConverter: MongoConverter

    private lateinit var service: MongoDualWriteCompensationService
    private lateinit var checker: CompensationHealthChecker

    private val compensationCollection = "mongo_dual_write_compensation"
    private val ruleName = "node"

    @BeforeEach
    fun setUp() {
        mongoTemplate.dropCollection(compensationCollection)
        service = MongoDualWriteCompensationService(
            mongoTemplate, mongoConverter, mock(), MongoMultiInstanceProperties(),
        )
        checker = CompensationHealthChecker(service)
    }

    @AfterEach
    fun tearDown() {
        mongoTemplate.dropCollection(compensationCollection)
    }

    private fun insertPending(createdAt: LocalDateTime, ruleName: String = this.ruleName) {
        val doc = Document()
        doc["_id"] = ObjectId()
        doc["ruleName"] = ruleName
        doc["routingKey"] = "projectA"
        doc["collectionName"] = "node_0"
        doc["operationType"] = "INSERT"
        doc["targetUseDefault"] = true
        doc["targetInstance"] = "heavy1"
        doc["status"] = "PENDING"
        doc["retryCount"] = 0
        doc["createdAt"] = createdAt
        doc["updatedAt"] = LocalDateTime.now()
        mongoTemplate.insert(doc, compensationCollection)
    }

    @Test
    fun `oldestPendingAgeSeconds reflects real createdAt age and marks unhealthy when stale`() {
        // createdAt = 3600s 前，超过 MAX_HEALTHY_AGE_SECONDS(1800)
        insertPending(LocalDateTime.now().minusSeconds(3600))

        val health = checker.check(ruleName)

        assertEquals(1L, health.pendingCount)
        // 年龄应接近 3600s，允许 ±10s 抖动
        assertTrue(
            health.oldestPendingAgeSeconds in 3590L..3610L,
            "expected age ~3600, got ${health.oldestPendingAgeSeconds}",
        )
        assertFalse(health.healthy, "stale pending task should make health unhealthy")
    }

    @Test
    fun `healthy when no pending tasks`() {
        val health = checker.check(ruleName)
        assertEquals(0L, health.pendingCount)
        assertEquals(0L, health.oldestPendingAgeSeconds)
        assertTrue(health.healthy)
    }

    @Test
    fun `healthy when pending task is fresh`() {
        insertPending(LocalDateTime.now().minusSeconds(10))
        val health = checker.check(ruleName)
        assertEquals(1L, health.pendingCount)
        assertTrue(health.oldestPendingAgeSeconds in 0L..20L)
        assertTrue(health.healthy)
    }
}
