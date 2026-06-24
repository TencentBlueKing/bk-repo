package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * DualWriteSidecarVerifier 单元测试（Spec §25.3.2 E-05）。
 *
 * defaultTemplate 使用嵌入式 MongoDB 真实读写，heavyTemplate 保持 mock（模拟远端 Heavy 实例）。
 * 覆盖旁路对账关键场景：
 * 1. 无路由项目时跳过对账
 * 2. 零差异 → passed（两侧数据一致）
 * 3. 有差异 → not passed（Heavy 侧数据不同）
 * 4. 轮数不足时返回 false
 * 5. Heavy 缺失文档 → diff
 * 6. _class / version 被排除在对比外
 */
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DualWriteSidecarVerifierTest {

    @Autowired
    lateinit var defaultTemplate: MongoTemplate

    private lateinit var heavyTemplate: MongoTemplate
    private lateinit var registry: MongoRoutingRegistry
    private lateinit var verifier: DualWriteSidecarVerifier

    @BeforeEach
    fun setUp() {
        heavyTemplate = mock()
        registry = mock()
        whenever(registry.isProjectInDualWrite(any(), any())).thenReturn(true)
        verifier = DualWriteSidecarVerifier(defaultTemplate, registry)
    }

    @AfterEach
    fun tearDown() {
        defaultTemplate.collectionNames
            .filter { it.startsWith("node_") }
            .forEach { defaultTemplate.dropCollection(it) }
    }

    /**
     * 与 [DualWriteSidecarVerifier.resolveShardCollection] 保持一致。
     */
    private fun shardCollection(projectId: String): String {
        val shardIndex = HashShardingUtils.shardingSequenceFor(projectId, 256)
        return "node_$shardIndex"
    }

    // ── 1. 非 DUAL_WRITE 项目跳过对账 ──────────────────────────────────

    @Test
    fun `verify skips non DUAL_WRITE projects`() {
        val localVerifier = DualWriteSidecarVerifier(defaultTemplate, registry)
        whenever(registry.isProjectInDualWrite(any(), eq("projectA"))).thenReturn(false)
        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf("projectA")),
        )
        localVerifier.verify()
        assertFalse(localVerifier.isRecentVerificationPassed("projectA", requiredPassRounds = 1))
    }

    // ── 2. 无路由项目 → verify 跳过 ──────────────────────────────────

    @Test
    fun `verify skips when no configured projects`() {
        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(emptyMap())
        verifier.verify() // 不应抛异常
    }

    // ── 3. 零差异 → passed ──────────────────────────────────────────

    @Test
    fun `verify marks passed when both sides have identical samples`() {
        val projectId = "projectA"
        val collection = shardCollection(projectId)

        val doc = Document().apply {
            put("_id", ObjectId())
            put("projectId", projectId)
            put("name", "file.txt")
            put("size", 100)
        }
        defaultTemplate.insert(doc, collection)

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        // Heavy 返回相同文档
        whenever(heavyTemplate.findById(doc.getObjectId("_id"), Document::class.java, collection))
            .thenReturn(doc)

        verifier.verify()

        assertTrue(verifier.isRecentVerificationPassed(projectId, requiredPassRounds = 1))
    }

    // ── 3. 有差异 → not passed ──────────────────────────────────────

    @Test
    fun `isRecentVerificationPassed returns false when diffs exist`() {
        val projectId = "projectB"
        val collection = shardCollection(projectId)

        val id = ObjectId()
        val defaultDoc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
            put("name", "v1")
        }
        defaultTemplate.insert(defaultDoc, collection)

        val heavyDoc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
            put("name", "v2") // 内容不一致
        }

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(heavyTemplate.findById(id, Document::class.java, collection)).thenReturn(heavyDoc)

        verifier.verify()

        assertFalse(verifier.isRecentVerificationPassed(projectId, requiredPassRounds = 1))
    }

    // ── 4. 轮数不足 → false ─────────────────────────────────────────

    @Test
    fun `isRecentVerificationPassed returns false when not enough rounds`() {
        assertFalse(verifier.isRecentVerificationPassed("unknown-project", requiredPassRounds = 3))
    }

    // ── 5. 获取对账历史 ──────────────────────────────────────────────

    @Test
    fun `getHistory returns empty for unknown project`() {
        assertTrue(verifier.getHistory("unknown").isEmpty())
    }

    // ── 6. Heavy 有数据但 Default 没有 → 视为 passed（空集合场景） ──

    @Test
    fun `verify detects missing documents in Heavy as diff`() {
        val projectId = "projectC"
        val collection = shardCollection(projectId)

        val id = ObjectId()
        val defaultDoc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
        }
        defaultTemplate.insert(defaultDoc, collection)

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        // Heavy 查不到该文档
        whenever(heavyTemplate.findById(id, Document::class.java, collection)).thenReturn(null)

        verifier.verify()

        assertFalse(verifier.isRecentVerificationPassed(projectId, requiredPassRounds = 1))
    }

    // ── 7. 文档内容深度对比：_class 和 version 被排除 ─────────────────

    @Test
    fun `documentsEqual ignores _class and version fields`() {
        val projectId = "projectD"
        val collection = shardCollection(projectId)

        val id = ObjectId()
        val defaultDoc = Document().apply {
            put("_id", id)
            put("_class", "com.tencent.TNode")
            put("version", 3)
            put("name", "file.txt")
        }
        defaultTemplate.insert(defaultDoc, collection)

        val heavyDoc = Document().apply {
            put("_id", id)
            put("_class", "com.tencent.bkrepo.common.metadata.model.TNode")
            put("version", 5)
            put("name", "file.txt")
        }

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(heavyTemplate.findById(id, Document::class.java, collection)).thenReturn(heavyDoc)

        verifier.verify()

        assertTrue(verifier.isRecentVerificationPassed(projectId, requiredPassRounds = 1))
    }

    // ── 8. 多轮验证全部通过 → passed ──────────────────────────────────

    @Test
    fun `isRecentVerificationPassed requires all three rounds passed`() {
        val projectId = "projectMulti"
        val collection = shardCollection(projectId)

        val id = ObjectId()
        val doc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
            put("name", "consistent")
        }

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(heavyTemplate.findById(id, Document::class.java, collection)).thenReturn(doc)

        // 连续 3 轮对账
        repeat(3) {
            // 每轮重新插入文档（因为 verify 不修改数据）
            defaultTemplate.dropCollection(collection)
            defaultTemplate.insert(doc, collection)
            verifier.verify()
        }

        assertTrue(verifier.isRecentVerificationPassed(projectId, requiredPassRounds = 3))
    }

    // ── 9. 前两轮通过第三轮失败 → not passed ────────────────────────

    @Test
    fun `isRecentVerificationPassed fails if any round has diff`() {
        val projectId = "projectPartial"
        val collection = shardCollection(projectId)

        val id1 = ObjectId()
        val id2 = ObjectId()
        val doc1 = Document().apply { put("_id", id1); put("projectId", projectId); put("name", "v1") }
        val doc2 = Document().apply { put("_id", id2); put("projectId", projectId); put("name", "v2") }

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)

        // Round 1: passed（Heavy 返回相同文档）
        defaultTemplate.insert(doc1, collection)
        whenever(heavyTemplate.findById(id1, Document::class.java, collection)).thenReturn(doc1)
        verifier.verify()

        // Round 2: passed
        defaultTemplate.dropCollection(collection)
        defaultTemplate.insert(doc1, collection)
        verifier.verify()

        // Round 3: failed（Heavy 返回不同文档）
        defaultTemplate.dropCollection(collection)
        defaultTemplate.insert(doc2, collection)
        val heavyDiff = Document().apply { put("_id", id2); put("projectId", projectId); put("name", "different") }
        whenever(heavyTemplate.findById(id2, Document::class.java, collection)).thenReturn(heavyDiff)
        verifier.verify()

        assertFalse(verifier.isRecentVerificationPassed(projectId, requiredPassRounds = 3))
    }

    // ── 10. getHistory 返回对账历史 ─────────────────────────────────

    @Test
    fun `getHistory returns verification history after multiple rounds`() {
        val projectId = "projectHistory"
        val collection = shardCollection(projectId)

        val id = ObjectId()
        val doc = Document().apply { put("_id", id); put("projectId", projectId); put("name", "v") }

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(heavyTemplate.findById(id, Document::class.java, collection)).thenReturn(doc)

        repeat(5) {
            defaultTemplate.dropCollection(collection)
            defaultTemplate.insert(doc, collection)
            verifier.verify()
        }

        val history = verifier.getHistory(projectId)
        assertTrue(history.size >= 5)
        assertTrue(history.all { it.passed })
    }

    // ── 11. 历史截断：超过 MAX_HISTORY 时保留最近 20 条 ────────────

    @Test
    fun `verification history truncates to MAX_HISTORY`() {
        val projectId = "projectTruncate"
        val collection = shardCollection(projectId)

        val id = ObjectId()
        val doc = Document().apply { put("_id", id); put("projectId", projectId); put("name", "v") }

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(heavyTemplate.findById(id, Document::class.java, collection)).thenReturn(doc)

        // 执行 25 轮对账
        repeat(25) {
            defaultTemplate.dropCollection(collection)
            defaultTemplate.insert(doc, collection)
            verifier.verify()
        }

        val history = verifier.getHistory(projectId)
        // 历史应被截断到 20 条
        assertTrue(history.size <= 20)
    }

    // ── 12. 嵌套文档深度对比 ────────────────────────────────────────

    @Test
    fun `documentsEqual handles nested documents`() {
        val projectId = "projectNested"
        val collection = shardCollection(projectId)

        val id = ObjectId()
        val defaultDoc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
            put("metadata", Document().apply {
                put("author", "user1")
                put("tags", listOf("tag1", "tag2"))
            })
        }
        defaultTemplate.insert(defaultDoc, collection)

        val heavyDoc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
            put("metadata", Document().apply {
                put("author", "user1")
                put("tags", listOf("tag1", "tag2"))
            })
        }

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(heavyTemplate.findById(id, Document::class.java, collection)).thenReturn(heavyDoc)

        verifier.verify()
        assertTrue(verifier.isRecentVerificationPassed(projectId, requiredPassRounds = 1))
    }

    @Test
    fun `documentsEqual detects difference in nested documents`() {
        val projectId = "projectNestedDiff"
        val collection = shardCollection(projectId)

        val id = ObjectId()
        val defaultDoc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
            put("metadata", Document().apply {
                put("author", "user1")
                put("version", 1)
            })
        }
        defaultTemplate.insert(defaultDoc, collection)

        val heavyDoc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
            put("metadata", Document().apply {
                put("author", "user1")
                put("version", 2) // 版本号不一致
            })
        }

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(heavyTemplate.findById(id, Document::class.java, collection)).thenReturn(heavyDoc)

        verifier.verify()
        assertFalse(verifier.isRecentVerificationPassed(projectId, requiredPassRounds = 1))
    }

    // ── 13. 列表字段深度对比 ────────────────────────────────────────

    @Test
    fun `documentsEqual handles list fields in deep comparison`() {
        val projectId = "projectList"
        val collection = shardCollection(projectId)

        val id = ObjectId()
        val defaultDoc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
            put("tags", listOf("a", "b", "c"))
        }
        defaultTemplate.insert(defaultDoc, collection)

        val heavyDoc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
            put("tags", listOf("a", "b", "c"))
        }

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(heavyTemplate.findById(id, Document::class.java, collection)).thenReturn(heavyDoc)

        verifier.verify()
        assertTrue(verifier.isRecentVerificationPassed(projectId, requiredPassRounds = 1))
    }

    @Test
    fun `documentsEqual detects list size difference`() {
        val projectId = "projectListDiff"
        val collection = shardCollection(projectId)

        val id = ObjectId()
        val defaultDoc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
            put("tags", listOf("a", "b"))
        }
        defaultTemplate.insert(defaultDoc, collection)

        val heavyDoc = Document().apply {
            put("_id", id)
            put("projectId", projectId)
            put("tags", listOf("a", "b", "c")) // 多一个元素
        }

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectId))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)
        whenever(heavyTemplate.findById(id, Document::class.java, collection)).thenReturn(heavyDoc)

        verifier.verify()
        assertFalse(verifier.isRecentVerificationPassed(projectId, requiredPassRounds = 1))
    }

    // ── 14. 多个项目管理 ─────────────────────────────────────────────

    @Test
    fun `verify handles multiple projects in same instance`() {
        val projectA = "multiA"
        val projectB = "multiB"
        val collectionA = shardCollection(projectA)
        val collectionB = shardCollection(projectB)

        val docA = Document().apply { put("_id", ObjectId()); put("projectId", projectA) }
        val docB = Document().apply { put("_id", ObjectId()); put("projectId", projectB) }
        defaultTemplate.insert(docA, collectionA)
        defaultTemplate.insert(docB, collectionB)

        whenever(registry.allConfiguredProjectsByInstance("node")).thenReturn(
            mapOf("heavy1" to setOf(projectA, projectB))
        )
        whenever(registry.primaryTemplateByInstance("node", "heavy1")).thenReturn(heavyTemplate)

        // projectA: Heavy 一致 → passed
        whenever(heavyTemplate.findById(docA.getObjectId("_id"), Document::class.java, collectionA))
            .thenReturn(docA)
        // projectB: Heavy 不一致 → not passed
        val diffDoc = Document().apply { put("_id", docB.getObjectId("_id")); put("projectId", projectB); put("extra", "diff") }
        whenever(heavyTemplate.findById(docB.getObjectId("_id"), Document::class.java, collectionB))
            .thenReturn(diffDoc)

        verifier.verify()

        assertTrue(verifier.isRecentVerificationPassed(projectA, requiredPassRounds = 1))
        assertFalse(verifier.isRecentVerificationPassed(projectB, requiredPassRounds = 1))
    }
}
