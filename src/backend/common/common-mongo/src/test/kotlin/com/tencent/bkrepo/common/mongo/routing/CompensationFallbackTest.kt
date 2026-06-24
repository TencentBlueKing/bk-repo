package com.tencent.bkrepo.common.mongo.routing

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import java.nio.file.Files
import java.nio.file.Path

/**
 * CompensationFallbackWriter / CompensationFallbackRecovery 单元测试（Spec §25.2.3 E-15）。
 *
 * 文件兜底和恢复链路使用嵌入式 MongoDB 真实读写：
 * 1. fallbackWriter.write: 正常写入本地JSON文件
 * 2. fallbackWriter.list: 列出所有回退文件
 * 3. fallbackWriter.readAndDelete: 读取并删除
 * 4. fallbackRecovery.recover: 扫描并恢复文件到 MongoDB（真实 DB 验证）
 * 5. CompensationTaskSnapshot: JSON序列化/反序列化
 * 6. readAndDelete: 无效JSON返回null
 */
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompensationFallbackTest {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    private lateinit var writer: CompensationFallbackWriter
    private lateinit var recovery: CompensationFallbackRecovery

    private val compensationCollection = "mongo_dual_write_compensation"

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        mongoTemplate.dropCollection(compensationCollection)
        cleanupFallbackFiles()
        writer = CompensationFallbackWriter()
        recovery = CompensationFallbackRecovery(writer, mongoTemplate)
    }

    @AfterEach
    fun tearDown() {
        mongoTemplate.dropCollection(compensationCollection)
        cleanupFallbackFiles()
    }

    private fun cleanupFallbackFiles() {
        val cleaner = CompensationFallbackWriter()
        cleaner.list().forEach { cleaner.readAndDelete(it) }
    }

    // ── 1. write: 任务序列化为JSON文件 ────────────────────────────────

    @Test
    fun `write creates JSON file with snapshot content`() {
        val snapshot = CompensationTaskSnapshot(
            ruleName = "node",
            routingKey = "projectA",
            collectionName = "node_0",
            operationType = "INSERT",
            targetUseDefault = true,
            targetInstance = "heavy1",
            entityClass = "com.tencent.TestEntity",
            entityDocument = mapOf("_id" to "abc123", "name" to "test"),
            queryDocument = null,
            updateDocument = null,
            optionsDocument = null,
            createdAt = "2026-06-22T20:00:00",
        )

        writer.write(snapshot)

        val files = writer.list()
        assertTrue(files.isNotEmpty(), "Should have written at least one fallback file")
        assertTrue(files.any { it.toString().endsWith(".json") })
    }

    // ── 2. toJson / fromJson: 序列化往返 ──────────────────────────────

    @Test
    fun `CompensationTaskSnapshot toJson and fromJson roundtrip`() {
        val original = CompensationTaskSnapshot(
            ruleName = "node",
            routingKey = "projectB",
            collectionName = "node_1",
            operationType = "UPDATE_FIRST",
            targetUseDefault = false,
            targetInstance = "heavy2",
            entityClass = "com.tencent.TNode",
            entityDocument = null,
            queryDocument = mapOf("_id" to mapOf("\$oid" to "507f1f77bcf86cd799439011")),
            updateDocument = mapOf("\$set" to mapOf("size" to 200)),
            optionsDocument = null,
            createdAt = "2026-06-22T20:00:00",
        )

        val json = original.toJson()
        assertTrue(json.contains("projectB"))
        assertTrue(json.contains("UPDATE_FIRST"))

        val restored = CompensationTaskSnapshot.fromJson(json)
        assertNotNull(restored)
        assertEquals(original.ruleName, restored!!.ruleName)
        assertEquals(original.routingKey, restored.routingKey)
        assertEquals(original.operationType, restored.operationType)
        assertEquals(original.collectionName, restored.collectionName)
    }

    // ── 3. readAndDelete: 读取后删除文件 ──────────────────────────────

    @Test
    fun `readAndDelete removes file after successful read`() {
        val snapshot = CompensationTaskSnapshot(
            ruleName = "node",
            routingKey = "projectC",
            collectionName = "node_0",
            operationType = "SAVE",
            targetUseDefault = false,
            targetInstance = "heavy3",
            entityClass = null,
            entityDocument = mapOf("name" to "test"),
            queryDocument = null,
            updateDocument = null,
            optionsDocument = null,
            createdAt = "2026-06-22T20:00:00",
        )
        writer.write(snapshot)

        val filesBefore = writer.list()
        assertTrue(filesBefore.isNotEmpty())

        val file = filesBefore.first()
        val restored = writer.readAndDelete(file)
        assertNotNull(restored)

        val filesAfter = writer.list()
        val deleted = filesAfter.none { it == file }
        assertTrue(deleted, "File should be deleted after readAndDelete")
    }

    // ── 4. recovery.recover: 扫描并写入真实 MongoDB ───────────────────

    @Test
    fun `recover scans files and inserts into MongoDB`() {
        val snapshot = CompensationTaskSnapshot(
            ruleName = "node",
            routingKey = "projectD",
            collectionName = "node_2",
            operationType = "REMOVE",
            targetUseDefault = true,
            targetInstance = null,
            entityClass = null,
            entityDocument = null,
            queryDocument = mapOf("projectId" to "projectD"),
            updateDocument = null,
            optionsDocument = null,
            createdAt = "2026-06-22T20:00:00",
        )
        writer.write(snapshot)

        // recovery.recover() 扫描文件并写入 MongoDB 补偿集合
        recovery.recover()

        // 从真实 DB 验证补偿文档
        val docs = mongoTemplate.findAll(
            org.bson.Document::class.java, compensationCollection,
        )
        assertEquals(1, docs.size)
        val doc = docs.single { it.getString("routingKey") == "projectD" }
        assertEquals("node", doc.getString("ruleName"))
        assertEquals("projectD", doc.getString("routingKey"))
        assertEquals("REMOVE", doc.getString("operationType"))
        assertEquals("PENDING", doc.getString("status"))
        assertEquals("fallback_recovery", doc.getString("source"))

        // 补偿后回退文件应已被 readAndDelete 删除
        val remainingFiles = writer.list()
        val fileGone = remainingFiles.none { it.toString().contains("projectD") }
        assertTrue(fileGone, "Fallback file should be deleted after successful recovery")
    }

    // ── 5. 无文件时 recovery 跳过 ─────────────────────────────────────

    @Test
    fun `recover does nothing when no files exist`() {
        // CompensationFallbackWriter.list() 扫描 /data/bkrepo/compensation_fallback
        // 确保当前没有残留文件后调用 recover 不抛异常
        // 先清理已知的测试残留文件
        val existingFiles = writer.list()
        existingFiles.forEach { writer.readAndDelete(it) }

        val remainingFiles = writer.list()
        if (remainingFiles.isEmpty()) {
            recovery.recover()
        }

        // 没有文件时 DB 中不应有新文档
        val docs = mongoTemplate.findAll(
            org.bson.Document::class.java, compensationCollection,
        )
        assertEquals(0, docs.size)
    }

    // ── 6. readAndDelete: 无效JSON返回null ────────────────────────────

    @Test
    fun `readAndDelete returns null for invalid JSON`() {
        val invalidFile = Files.createTempFile(tempDir, "invalid", ".json")
        Files.writeString(invalidFile, "{not valid json")

        val result = writer.readAndDelete(invalidFile)
        assertEquals(null, result)
    }
}
