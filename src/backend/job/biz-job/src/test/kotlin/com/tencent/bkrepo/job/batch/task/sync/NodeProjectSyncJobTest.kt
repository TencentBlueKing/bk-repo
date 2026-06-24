package com.tencent.bkrepo.job.batch.task.sync

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.routing.NodeReconciliationHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime

/**
 * NodeProjectSyncJob 单元测试。
 *
 * 重点验证 Bug1 修复：INITIAL_SYNC 多分片断点续传逻辑（Spec §3.9.1）：
 * 1. 全新开始：从 shard 0 的 MIN_OBJECT_ID 开始，所有分片按序扫描
 * 2. 断点续传（currentShardIdx=2, lastSyncedId="someId"）：
 *    - shard 0、1 不被扫描
 *    - shard 2 从 lastSyncedId 开始（而非 MIN_OBJECT_ID）
 *    - shard 3+ 从 MIN_OBJECT_ID 开始
 * 3. upsert 失败重试超限后写入 sync_failed 表
 * 4. VERIFY 阶段少量分片不一致时执行定向修复，不全量重同步
 *
 * 架构变更：状态管理从 MongoDB node_project_sync_state 集合迁移到内存 ConcurrentHashMap。
 * 测试通过直接写入 job.syncStates 完成状态注入，不再 mock DB 读写。
 */
class NodeProjectSyncJobTest {

    private val PROJECT_ID = "testProject"
    private val INSTANCE_NAME = "heavy1"
    private val MIN_OBJECT_ID = "000000000000000000000000"

    private lateinit var defaultTemplate: MongoTemplate
    private lateinit var heavyTemplate: MongoTemplate
    private lateinit var registry: MongoRoutingRegistry

    @BeforeEach
    fun setUp() {
        defaultTemplate = mockk(relaxed = true)
        heavyTemplate = mockk(relaxed = true)
        registry = mockk()

        every { registry.allConfiguredProjectsByInstance("node") } returns
            mapOf(INSTANCE_NAME to setOf(PROJECT_ID))
        every { registry.primaryTemplateByInstance("node", INSTANCE_NAME) } returns heavyTemplate
    }

    /**
     * 创建一个 SyncStateDoc 并注入到 Job 的内存状态中。
     */
    private fun injectState(
        job: NodeProjectSyncJob,
        state: NodeProjectSyncJob.SyncState,
        currentShardIdx: Int = 0,
        lastSyncedId: String? = null,
        scanStartTimestamp: Long? = null,
    ) {
        job.syncStates[PROJECT_ID] = NodeProjectSyncJob.SyncStateDoc(
            id = PROJECT_ID,
            projectId = PROJECT_ID,
            targetInstance = INSTANCE_NAME,
            state = state,
            currentShardIdx = currentShardIdx,
            lastSyncedId = lastSyncedId,
            lastError = null,
            updatedAt = LocalDateTime.now(),
            resumeToken = null,
            scanStartTimestamp = scanStartTimestamp,
        )
    }

    // ── 辅助：准备通用 mock ──────────────────────────────────────────────────

    private fun stubDefaultCounts() {
        every { defaultTemplate.count(any<Query>(), any<String>()) } returns 0L
        every { heavyTemplate.count(any<Query>(), any<String>()) } returns 0L
        every { defaultTemplate.count(any<Query>(), "node_project_sync_failed") } returns 0L
        every { registry.migrationMode("node") } returns "SYNC_JOB"
        every { registry.historicalSyncStrategy("node") } returns "JOB_ONLY"
        val emptyAgg = AggregationResults(emptyList<Document>(), Document())
        every { defaultTemplate.aggregate(any(), any<String>(), Document::class.java) } returns emptyAgg
        every { heavyTemplate.aggregate(any(), any<String>(), Document::class.java) } returns emptyAgg
        every { defaultTemplate.insert(any<Document>(), NodeReconciliationHelper.RECONCILIATION_LOG_COLLECTION) } returns Document()
    }

    private fun stubFindEmpty() {
        every { defaultTemplate.find(any<Query>(), Document::class.java, any<String>()) } returns emptyList()
    }

    // ── 1. INITIAL_SYNC 全新开始：从 shard 0 + MIN_OBJECT_ID ────────────────

    @Test
    fun `INITIAL_SYNC fresh start queries shard 0 from MIN_OBJECT_ID`() {
        stubDefaultCounts()
        val job = NodeProjectSyncJob(defaultTemplate, registry)
        injectState(job, NodeProjectSyncJob.SyncState.INITIAL_SYNC, currentShardIdx = 0, lastSyncedId = null)

        val docId = ObjectId()
        val fakeDoc = Document().apply {
            put("_id", docId)
            put("projectId", PROJECT_ID)
            put("name", "file.txt")
        }
        var shard0Called = false
        every {
            defaultTemplate.find(any<Query>(), Document::class.java, match { it == "node_0" })
        } answers {
            if (!shard0Called) {
                shard0Called = true; listOf(fakeDoc)
            } else emptyList()
        }
        every {
            defaultTemplate.find(any<Query>(), Document::class.java, match { it != "node_0" })
        } returns emptyList()

        job.run()

        val querySlot = slot<Query>()
        verify { defaultTemplate.find(capture(querySlot), Document::class.java, "node_0") }
        val idFilter = querySlot.captured.queryObject["_id"] as? Map<*, *>
        val gtValue = idFilter?.get("\$gt") as? ObjectId
        assertEquals(ObjectId(MIN_OBJECT_ID), gtValue)
    }

    // ── 2. 断点续传：shard < currentShardIdx 不被扫描 ───────────────────────

    @Test
    fun `INITIAL_SYNC resume skips shards before currentShardIdx`() {
        stubDefaultCounts()
        stubFindEmpty()
        val resumeId = ObjectId().toHexString()
        val job = NodeProjectSyncJob(defaultTemplate, registry)
        injectState(job, NodeProjectSyncJob.SyncState.INITIAL_SYNC, currentShardIdx = 3, lastSyncedId = resumeId)

        job.run()

        verify(exactly = 0) { defaultTemplate.find(any<Query>(), Document::class.java, "node_0") }
        verify(exactly = 0) { defaultTemplate.find(any<Query>(), Document::class.java, "node_1") }
        verify(exactly = 0) { defaultTemplate.find(any<Query>(), Document::class.java, "node_2") }
    }

    // ── 3. 断点续传：currentShardIdx 分片从 lastSyncedId 开始 ────────────────

    @Test
    fun `INITIAL_SYNC resume uses lastSyncedId as start for currentShardIdx shard`() {
        stubDefaultCounts()
        stubFindEmpty()
        val resumeId = ObjectId().toHexString()
        val job = NodeProjectSyncJob(defaultTemplate, registry)
        injectState(job, NodeProjectSyncJob.SyncState.INITIAL_SYNC, currentShardIdx = 5, lastSyncedId = resumeId)

        job.run()

        val querySlot = slot<Query>()
        verify { defaultTemplate.find(capture(querySlot), Document::class.java, "node_5") }
        val idFilter = querySlot.captured.queryObject["_id"] as? Map<*, *>
        val gtValue = idFilter?.get("\$gt") as? ObjectId
        assertEquals(ObjectId(resumeId), gtValue)
    }

    // ── 4. 断点续传：currentShardIdx+1 分片从 MIN_OBJECT_ID 开始 ─────────────

    @Test
    fun `INITIAL_SYNC resume starts subsequent shards from MIN_OBJECT_ID`() {
        stubDefaultCounts()
        val resumeId = ObjectId().toHexString()
        val job = NodeProjectSyncJob(defaultTemplate, registry)
        injectState(job, NodeProjectSyncJob.SyncState.INITIAL_SYNC, currentShardIdx = 2, lastSyncedId = resumeId)

        every { defaultTemplate.find(any<Query>(), Document::class.java, "node_2") } returns emptyList()
        val shard3DocId = ObjectId()
        val shard3Doc = Document().apply { put("_id", shard3DocId); put("projectId", PROJECT_ID) }
        var shard3FirstCall = true
        every { defaultTemplate.find(any<Query>(), Document::class.java, "node_3") } answers {
            if (shard3FirstCall) { shard3FirstCall = false; listOf(shard3Doc) } else emptyList()
        }
        every {
            defaultTemplate.find(any<Query>(), Document::class.java, not(match { it == "node_2" || it == "node_3" }))
        } returns emptyList()

        job.run()

        val querySlots = mutableListOf<Query>()
        verify(atLeast = 1) { defaultTemplate.find(capture(querySlots), Document::class.java, "node_3") }
        val firstShard3Query = querySlots.first()
        val idFilter = firstShard3Query.queryObject["_id"] as? Map<*, *>
        val gtValue = idFilter?.get("\$gt") as? ObjectId
        assertEquals(ObjectId(MIN_OBJECT_ID), gtValue)
    }

    // ── 5. updateProgress 更新 currentShardIdx ───────────────────────────────

    @Test
    fun `INITIAL_SYNC calls updateProgress with correct shardIdx after processing shard`() {
        stubDefaultCounts()
        stubFindEmpty()
        val job = NodeProjectSyncJob(defaultTemplate, registry)
        injectState(job, NodeProjectSyncJob.SyncState.INITIAL_SYNC, currentShardIdx = 0, lastSyncedId = null)

        job.run()

        // 验证内存中的状态被更新（currentShardIdx 递增）
        val state = job.syncStates[PROJECT_ID]
        assertTrue(
            state != null && state.currentShardIdx > 0,
            "updateProgress should advance currentShardIdx, got: ${state?.currentShardIdx}"
        )
    }

    // ── 6. upsert 失败后写入 sync_failed 表 ─────────────────────────────────

    @Test
    fun `INITIAL_SYNC writes to sync_failed collection after exhausting upsert retries`() {
        stubDefaultCounts()
        val job = NodeProjectSyncJob(defaultTemplate, registry)
        injectState(job, NodeProjectSyncJob.SyncState.INITIAL_SYNC, currentShardIdx = 0, lastSyncedId = null)

        val docId = ObjectId()
        val badDoc = Document().apply { put("_id", docId); put("projectId", PROJECT_ID) }
        var firstCall = true
        every { defaultTemplate.find(any<Query>(), Document::class.java, "node_0") } answers {
            if (firstCall) { firstCall = false; listOf(badDoc) } else emptyList()
        }
        every {
            defaultTemplate.find(any<Query>(), Document::class.java, not(match { it == "node_0" }))
        } returns emptyList()
        every { heavyTemplate.upsert(any<Query>(), any<Update>(), any<String>()) } throws RuntimeException("upsert error")

        job.run()

        verify(atLeast = 1) {
            defaultTemplate.insert(
                match<Document> { it["collectionName"] == "node_0" && it["docId"] == docId.toHexString() },
                "node_project_sync_failed",
            )
        }
    }

    // ── 7. VERIFY：少量分片不一致时执行定向修复，不转 INITIAL_SYNC ────────────

    @Test
    fun `VERIFY repairs mismatched shards without going back to INITIAL_SYNC`() {
        stubDefaultCounts()
        every { defaultTemplate.count(any<Query>(), any<String>()) } returns 0L
        every { heavyTemplate.count(any<Query>(), any<String>()) } returns 0L
        every { defaultTemplate.count(any<Query>(), "node_0") } returns 1L
        every { heavyTemplate.count(any<Query>(), "node_0") } returns 0L

        val repairDocId = ObjectId()
        val repairDoc = Document().apply { put("_id", repairDocId); put("projectId", PROJECT_ID) }
        var repairFirstCall = true
        every { defaultTemplate.find(any<Query>(), Document::class.java, "node_0") } answers {
            if (repairFirstCall) { repairFirstCall = false; listOf(repairDoc) } else emptyList()
        }

        val job = NodeProjectSyncJob(defaultTemplate, registry)
        injectState(job, NodeProjectSyncJob.SyncState.VERIFY)

        job.run()

        val state = job.syncStates[PROJECT_ID]
        assertTrue(state != null, "should have state after VERIFY")
        assertTrue(
            state?.state != NodeProjectSyncJob.SyncState.INITIAL_SYNC,
            "VERIFY should not fall back to INITIAL_SYNC for small mismatch"
        )
    }

    // ── 8. VERIFY：count 相等但抽样内容不一致时也应触发修复 ──────────────────

    @Test
    fun `VERIFY detects content mismatch when count equals but sample ids differ`() {
        stubDefaultCounts()
        every { defaultTemplate.count(any<Query>(), any<String>()) } returns 5L
        every { heavyTemplate.count(any<Query>(), any<String>()) } returns 5L
        every { defaultTemplate.count(any<Query>(), "node_project_sync_failed") } returns 0L

        val idA = ObjectId()
        val idB = ObjectId()
        every { defaultTemplate.find(any<Query>(), Document::class.java, "node_0") } returns
            listOf(Document().apply { put("_id", idA) })
        every { heavyTemplate.find(any<Query>(), Document::class.java, "node_0") } returns
            listOf(Document().apply { put("_id", idB) })
        val sharedId = ObjectId()
        every {
            defaultTemplate.find(any<Query>(), Document::class.java, not(match { it == "node_0" }))
        } returns listOf(Document().apply { put("_id", sharedId) })
        every {
            heavyTemplate.find(any<Query>(), Document::class.java, not(match { it == "node_0" }))
        } returns listOf(Document().apply { put("_id", sharedId) })

        val job = NodeProjectSyncJob(defaultTemplate, registry)
        injectState(job, NodeProjectSyncJob.SyncState.VERIFY)

        job.run()

        val state = job.syncStates[PROJECT_ID]
        assertTrue(state != null, "should have state after VERIFY")
        assertTrue(
            state?.state != NodeProjectSyncJob.SyncState.READY,
            "Should not pass VERIFY when sample _id sets differ"
        )
    }

    // ── 9. VERIFY：count 和内容均一致时通过转 READY ───────────────────────────

    @Test
    fun `VERIFY passes and transitions to READY when count and sample ids all match`() {
        stubDefaultCounts()
        every { defaultTemplate.count(any<Query>(), any<String>()) } returns 3L
        every { heavyTemplate.count(any<Query>(), any<String>()) } returns 3L
        every { defaultTemplate.count(any<Query>(), "node_project_sync_failed") } returns 0L

        val sharedId = ObjectId()
        every { defaultTemplate.find(any<Query>(), Document::class.java, any<String>()) } returns
            listOf(Document().apply { put("_id", sharedId) })
        every { heavyTemplate.find(any<Query>(), Document::class.java, any<String>()) } returns
            listOf(Document().apply { put("_id", sharedId) })

        val job = NodeProjectSyncJob(defaultTemplate, registry)
        injectState(job, NodeProjectSyncJob.SyncState.VERIFY)

        job.run()

        val state = job.syncStates[PROJECT_ID]
        assertTrue(state != null, "should have state after VERIFY")
        assertTrue(
            state?.state == NodeProjectSyncJob.SyncState.READY,
            "VERIFY should pass and transition to READY, got: ${state?.state}"
        )
    }

    // ── 10. VERIFY：大量分片不一致时转 REBUILD_REQUIRED ──────────────────────

    @Test
    fun `VERIFY transitions to REBUILD_REQUIRED when too many shards mismatch`() {
        stubDefaultCounts()
        every { defaultTemplate.count(any<Query>(), any<String>()) } returns 0L
        every { heavyTemplate.count(any<Query>(), any<String>()) } returns 0L
        for (shardIdx in 0 until 20) {
            every { defaultTemplate.count(any<Query>(), "node_$shardIdx") } returns 100L
            every { heavyTemplate.count(any<Query>(), "node_$shardIdx") } returns 0L
        }

        val job = NodeProjectSyncJob(defaultTemplate, registry)
        injectState(job, NodeProjectSyncJob.SyncState.VERIFY)

        job.run()

        val state = job.syncStates[PROJECT_ID]
        assertTrue(state != null, "should have state after VERIFY")
        assertTrue(
            state?.state == NodeProjectSyncJob.SyncState.REBUILD_REQUIRED,
            "Should transition to REBUILD_REQUIRED when too many shards mismatch, got: ${state?.state}"
        )
    }
}
