package com.tencent.bkrepo.job.batch.task.sync

import com.mongodb.client.ListCollectionNamesIterable
import com.mongodb.client.MongoCursor
import com.mongodb.client.MongoDatabase
import com.tencent.bkrepo.common.mongo.api.routing.MigrationPhase
import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.dao.MigrationSyncStateDao
import com.tencent.bkrepo.common.mongo.model.TMigrationSyncState
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import com.tencent.bkrepo.common.lock.service.LockOperation
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.RedisTemplate
import java.time.LocalDateTime

class MigrationSyncJobTest {

    private val projectId = "testProject"
    private val nodeRule = "node"
    private val instanceName = "heavy1"
    private val minObjectId = "000000000000000000000000"
    private val oplogRule = CollectionFamilyMigrationScanStrategy.ARTIFACT_OPLOG_RULE
    private val oplogPrefix = "artifact_oplog_"

    private lateinit var defaultTemplate: MongoTemplate
    private lateinit var heavyTemplate: MongoTemplate
    private lateinit var registry: MongoRoutingRegistry
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var lockOperation: LockOperation
    private lateinit var properties: MongoMultiInstanceProperties
    private lateinit var syncStateDao: MigrationSyncStateDao

    @BeforeEach
    fun setUp() {
        defaultTemplate = mockk(relaxed = true)
        heavyTemplate = mockk(relaxed = true)
        registry = mockk()
        redisTemplate = mockk(relaxed = true)
        lockOperation = mockk(relaxed = true)
        syncStateDao = mockk(relaxed = true)
        properties = MongoMultiInstanceProperties().apply {
            rules = mapOf(
                oplogRule to MongoMultiInstanceProperties.RoutingRule(collectionPrefix = oplogPrefix),
            )
        }
        every { lockOperation.getLock(any()) } returns Any()
        every { lockOperation.acquireLock(any(), any()) } returns true
        every { lockOperation.close(any(), any()) } returns Unit

        every { registry.primaryTemplateByInstance(nodeRule, instanceName) } returns heavyTemplate
        every { registry.primaryTemplateByInstance(oplogRule, "oplog") } returns heavyTemplate
        every { registry.listOffloadRuleNames() } returns listOf(oplogRule)
        every { registry.historicalSyncStrategy(oplogRule) } returns "JOB_ONLY"
    }

    private fun nodeStrategy() = ProjectShardMigrationScanStrategy(
        ruleName = nodeRule,
        shardCollectionsProvider = { (0 until 256).map { "node_$it" } },
        syncFailedCollection = "node_project_sync_failed",
    )

    private fun oplogStrategy() = CollectionFamilyMigrationScanStrategy(
        ruleName = oplogRule,
        defaultMongoTemplate = defaultTemplate,
        properties = properties,
    )

    private fun createEngine(
        strategies: Map<String, MigrationScanStrategy> = mapOf(nodeRule to nodeStrategy()),
        dao: MigrationSyncStateDao? = null,
    ) = MigrationSyncEngine(defaultTemplate, registry, dao, strategies)

    private fun createJob(
        engine: MigrationSyncEngine,
        dao: MigrationSyncStateDao? = null,
    ) = MigrationSyncJob(defaultTemplate, registry, dao, redisTemplate, lockOperation, engine)

    private fun injectNodeTask(
        engine: MigrationSyncEngine,
        state: MigrationSyncJobState,
        currentShardIdx: Int = 0,
        lastSyncedId: String? = null,
    ) {
        engine.tasks["$nodeRule:$projectId"] = MigrationSyncTask(
            stateKey = projectId,
            projectId = projectId,
            ruleName = nodeRule,
            targetInstance = instanceName,
            state = state,
            currentShardIdx = currentShardIdx,
            lastSyncedId = lastSyncedId,
            lastError = null,
            updatedAt = LocalDateTime.now(),
        )
    }

    private fun stubNodeCounts() {
        every { defaultTemplate.count(any<Query>(), any<String>()) } returns 0L
        every { defaultTemplate.count(any<Query>(), "node_project_sync_failed") } returns 0L
    }

    private fun stubFindEmpty() {
        every { defaultTemplate.find(any<Query>(), Document::class.java, any<String>()) } returns emptyList()
    }

    private fun stubOplogCollectionNames(vararg names: String) {
        val db = mockk<MongoDatabase>()
        val iterable = mockk<ListCollectionNamesIterable>()
        val cursor = mockk<MongoCursor<String>>()
        every { defaultTemplate.db } returns db
        every { db.listCollectionNames() } returns iterable
        every { iterable.iterator() } returns cursor
        every { cursor.hasNext() } returnsMany List(names.size) { true } + false
        every { cursor.next() } returnsMany names.toList()
    }

    @Test
    fun `run does not scan when no active tasks in DB`() {
        every { syncStateDao.findByPhases(any()) } returns emptyList()
        val engine = createEngine(dao = syncStateDao)
        createJob(engine, syncStateDao).run()

        assertTrue(engine.tasks.isEmpty())
        verify(exactly = 0) {
            defaultTemplate.find(any<Query>(), Document::class.java, any<String>())
        }
    }

    @Test
    fun `INITIAL_SYNC fresh start queries shard 0 from MIN_OBJECT_ID`() {
        stubNodeCounts()
        val engine = createEngine()
        injectNodeTask(engine, MigrationSyncJobState.INITIAL_SYNC)

        val docId = ObjectId()
        val fakeDoc = Document().apply {
            put("_id", docId)
            put("projectId", projectId)
            put("name", "file.txt")
        }
        var shard0Called = false
        every {
            defaultTemplate.find(any<Query>(), Document::class.java, match { it == "node_0" })
        } answers {
            if (!shard0Called) {
                shard0Called = true
                listOf(fakeDoc)
            } else {
                emptyList()
            }
        }
        every {
            defaultTemplate.find(any<Query>(), Document::class.java, match { it != "node_0" })
        } returns emptyList()

        engine.advance(engine.tasks["$nodeRule:$projectId"]!!)

        val querySlot = slot<Query>()
        verify { defaultTemplate.find(capture(querySlot), Document::class.java, "node_0") }
        val idFilter = querySlot.captured.queryObject["_id"] as? Map<*, *>
        val gtValue = idFilter?.get("\$gt") as? ObjectId
        assertEquals(ObjectId(minObjectId), gtValue)
    }

    @Test
    fun `INITIAL_SYNC resume skips shards before currentShardIdx`() {
        stubNodeCounts()
        stubFindEmpty()
        val resumeId = ObjectId().toHexString()
        val engine = createEngine()
        injectNodeTask(engine, MigrationSyncJobState.INITIAL_SYNC, currentShardIdx = 3, lastSyncedId = resumeId)

        engine.advance(engine.tasks["$nodeRule:$projectId"]!!)

        verify(exactly = 0) { defaultTemplate.find(any<Query>(), Document::class.java, "node_0") }
        verify(exactly = 0) { defaultTemplate.find(any<Query>(), Document::class.java, "node_1") }
        verify(exactly = 0) { defaultTemplate.find(any<Query>(), Document::class.java, "node_2") }
    }

    @Test
    fun `INITIAL_SYNC resume uses lastSyncedId as start for currentShardIdx shard`() {
        stubNodeCounts()
        stubFindEmpty()
        val resumeId = ObjectId().toHexString()
        val engine = createEngine()
        injectNodeTask(engine, MigrationSyncJobState.INITIAL_SYNC, currentShardIdx = 5, lastSyncedId = resumeId)

        engine.advance(engine.tasks["$nodeRule:$projectId"]!!)

        val querySlot = slot<Query>()
        verify { defaultTemplate.find(capture(querySlot), Document::class.java, "node_5") }
        val idFilter = querySlot.captured.queryObject["_id"] as? Map<*, *>
        val gtValue = idFilter?.get("\$gt") as? ObjectId
        assertEquals(ObjectId(resumeId), gtValue)
    }

    @Test
    fun `INITIAL_SYNC resume starts subsequent shards from MIN_OBJECT_ID`() {
        stubNodeCounts()
        val resumeId = ObjectId().toHexString()
        val engine = createEngine()
        injectNodeTask(engine, MigrationSyncJobState.INITIAL_SYNC, currentShardIdx = 2, lastSyncedId = resumeId)

        every { defaultTemplate.find(any<Query>(), Document::class.java, "node_2") } returns emptyList()
        val shard3DocId = ObjectId()
        val shard3Doc = Document().apply { put("_id", shard3DocId); put("projectId", projectId) }
        var shard3FirstCall = true
        every { defaultTemplate.find(any<Query>(), Document::class.java, "node_3") } answers {
            if (shard3FirstCall) {
                shard3FirstCall = false
                listOf(shard3Doc)
            } else {
                emptyList()
            }
        }
        every {
            defaultTemplate.find(any<Query>(), Document::class.java, not(match { it == "node_2" || it == "node_3" }))
        } returns emptyList()

        engine.advance(engine.tasks["$nodeRule:$projectId"]!!)

        val querySlots = mutableListOf<Query>()
        verify(atLeast = 1) { defaultTemplate.find(capture(querySlots), Document::class.java, "node_3") }
        val idFilter = querySlots.first().queryObject["_id"] as? Map<*, *>
        val gtValue = idFilter?.get("\$gt") as? ObjectId
        assertEquals(ObjectId(minObjectId), gtValue)
    }

    @Test
    fun `INITIAL_SYNC advances currentShardIdx after processing shard`() {
        stubNodeCounts()
        stubFindEmpty()
        val engine = createEngine()
        injectNodeTask(engine, MigrationSyncJobState.INITIAL_SYNC)

        engine.advance(engine.tasks["$nodeRule:$projectId"]!!)

        val state = engine.tasks["$nodeRule:$projectId"]
        assertTrue(
            state != null && state.currentShardIdx > 0,
            "updateProgress should advance currentShardIdx, got: ${state?.currentShardIdx}",
        )
    }

    @Test
    fun `INITIAL_SYNC writes to sync_failed collection after exhausting upsert retries`() {
        stubNodeCounts()
        val engine = createEngine()
        injectNodeTask(engine, MigrationSyncJobState.INITIAL_SYNC)

        val docId = ObjectId()
        val badDoc = Document().apply { put("_id", docId); put("projectId", projectId) }
        var firstCall = true
        every { defaultTemplate.find(any<Query>(), Document::class.java, "node_0") } answers {
            if (firstCall) {
                firstCall = false
                listOf(badDoc)
            } else {
                emptyList()
            }
        }
        every {
            defaultTemplate.find(any<Query>(), Document::class.java, not(match { it == "node_0" }))
        } returns emptyList()
        every {
            heavyTemplate.upsert(any<Query>(), any<Update>(), any<String>())
        } throws RuntimeException("upsert error")

        engine.advance(engine.tasks["$nodeRule:$projectId"]!!)

        verify(atLeast = 1) {
            defaultTemplate.upsert(
                any<Query>(),
                match<Update> { it.updateObject.containsKey("\$setOnInsert") },
                "node_project_sync_failed",
            )
        }
    }

    @Test
    fun `oplog INITIAL_SYNC uses setOnInsert upsert on target`() {
        val col = "${oplogPrefix}202601"
        stubOplogCollectionNames(col)
        every { defaultTemplate.count(any<Query>(), "oplog_sync_failed") } returns 0L
        val docId = ObjectId()
        val doc = Document("_id", docId).append("action", "download")
        var first = true
        every { defaultTemplate.find(any<Query>(), Document::class.java, col) } answers {
            if (first) {
                first = false
                listOf(doc)
            } else {
                emptyList()
            }
        }

        val engine = createEngine(mapOf(oplogRule to oplogStrategy()), syncStateDao)
        every { syncStateDao.findByPhases(any()) } returns listOf(
            TMigrationSyncState(
                id = oplogRule,
                projectId = oplogRule,
                ruleName = oplogRule,
                targetInstance = "oplog",
                phase = MigrationPhase.INITIAL_SYNC,
            ),
        )
        createJob(engine, syncStateDao).run()

        verify(atLeast = 1) {
            heavyTemplate.upsert(
                any<Query>(),
                match<Update> { it.updateObject.containsKey("\$setOnInsert") },
                col,
            )
        }
    }

    @Test
    fun `oplog INITIAL_SYNC writes to oplog_sync_failed after upsert retries exhausted`() {
        val col = "${oplogPrefix}202601"
        stubOplogCollectionNames(col)
        every { defaultTemplate.count(any<Query>(), "oplog_sync_failed") } returns 1L
        val docId = ObjectId()
        val doc = Document("_id", docId)
        var first = true
        every { defaultTemplate.find(any<Query>(), Document::class.java, col) } answers {
            if (first) {
                first = false
                listOf(doc)
            } else {
                emptyList()
            }
        }
        every { heavyTemplate.upsert(any<Query>(), any<Update>(), any<String>()) } throws
            RuntimeException("upsert error")

        val engine = createEngine(mapOf(oplogRule to oplogStrategy()), syncStateDao)
        every { syncStateDao.findByPhases(any()) } returns listOf(
            TMigrationSyncState(
                id = oplogRule,
                projectId = oplogRule,
                ruleName = oplogRule,
                targetInstance = "oplog",
                phase = MigrationPhase.INITIAL_SYNC,
            ),
        )
        createJob(engine, syncStateDao).run()

        verify(atLeast = 1) {
            defaultTemplate.upsert(
                any<Query>(),
                match<Update> { it.updateObject.containsKey("\$setOnInsert") },
                "oplog_sync_failed",
            )
        }
        assertTrue(engine.tasks["$oplogRule:$oplogRule"]?.state != MigrationSyncJobState.DUAL_WRITE)
    }

    @Test
    fun `loadActiveTasks returns empty when DB has no active phases`() {
        every { syncStateDao.findByPhases(any()) } returns emptyList()
        val engine = createEngine(dao = syncStateDao)

        val active = engine.loadActiveTasks()

        assertTrue(active.isEmpty())
        assertNull(engine.tasks["$nodeRule:$projectId"])
    }
}
