package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.util.sharding.HashShardingUtils
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate

@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NodeReconciliationHelperTest {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    private val projectId = "reconcileProject"
    private lateinit var collection: String

    @BeforeEach
    fun setUp() {
        collection = NodeReconciliationHelper.shardCollection(projectId)
        mongoTemplate.dropCollection(collection)
    }

    @AfterEach
    fun tearDown() {
        mongoTemplate.dropCollection(collection)
        mongoTemplate.dropCollection(NodeReconciliationHelper.RECONCILIATION_LOG_COLLECTION)
    }

    @Test
    fun `shardCollection matches HashShardingUtils`() {
        val expected = "node_${HashShardingUtils.shardingSequenceFor(projectId, 256)}"
        assertEquals(expected, NodeReconciliationHelper.shardCollection(projectId))
    }

    @Test
    fun `documentsEqual ignores _class and version`() {
        val id = ObjectId()
        val a = Document("_id", id).append("_class", "A").append("version", 1).append("name", "x")
        val b = Document("_id", id).append("_class", "B").append("version", 2).append("name", "x")
        assertTrue(NodeReconciliationHelper.documentsEqual(a, b))
    }

    @Test
    fun `checksumSnapshot and checksumsEqual`() {
        mongoTemplate.insert(
            Document().apply {
                put("_id", ObjectId())
                put("projectId", projectId)
                put("deleted", true)
                put("lastModifiedDate", java.util.Date())
            },
            collection,
        )
        val snap = NodeReconciliationHelper.checksumSnapshot(mongoTemplate, collection, projectId)
        assertEquals(1L, snap.count)
        assertEquals(1L, snap.deletedCount)
        assertTrue(NodeReconciliationHelper.checksumsEqual(snap, snap))
    }

    @Test
    fun `fullIdScanDiff reports symmetric diff`() {
        val shared = ObjectId()
        val onlyPrimary = ObjectId()
        mongoTemplate.insert(Document("_id", shared).append("projectId", projectId), collection)
        mongoTemplate.insert(Document("_id", onlyPrimary).append("projectId", projectId), collection)

        val secondary = org.mockito.kotlin.mock<MongoTemplate>()
        org.mockito.kotlin.whenever(
            secondary.find(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.eq(Document::class.java),
                org.mockito.kotlin.eq(collection),
            ),
        ).thenReturn(listOf(Document("_id", shared).append("projectId", projectId)))

        val (missingInSecondary, extraInSecondary) = NodeReconciliationHelper.fullIdScanDiff(
            projectId, mongoTemplate, secondary,
        )
        assertTrue(missingInSecondary.contains(onlyPrimary))
        assertTrue(extraInSecondary.isEmpty())
    }

    @Test
    fun `persistLog writes reconciliation record`() {
        NodeReconciliationHelper.persistLog(mongoTemplate, projectId, "TEST", true, "ok")
        val logs = mongoTemplate.findAll(
            Document::class.java,
            NodeReconciliationHelper.RECONCILIATION_LOG_COLLECTION,
        )
        assertEquals(1, logs.size)
        assertEquals(projectId, logs[0]["projectId"])
        assertTrue(logs[0].getBoolean("passed"))
    }

    @Test
    fun `checksumsEqual returns false on count mismatch`() {
        val a = NodeReconciliationHelper.ChecksumSnapshot(1, 0, 100L)
        val b = NodeReconciliationHelper.ChecksumSnapshot(2, 0, 100L)
        assertFalse(NodeReconciliationHelper.checksumsEqual(a, b))
    }
}
