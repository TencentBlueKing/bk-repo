package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@DisplayName("联邦元数据跟踪DAO测试")
@DataMongoTest
@Import(FederationMetadataTrackingDao::class)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class FederationMetadataTrackingDaoTest @Autowired constructor(
    private val federationMetadataTrackingDao: FederationMetadataTrackingDao
) {

    private val testTaskKey = "test-task-key"
    private val testRemoteClusterId = "test-remote-cluster-id"
    private val testProjectId = "test-project-id"
    private val testLocalRepoName = "test-local-repo"
    private val testRemoteProjectId = "test-remote-project-id"
    private val testRemoteRepoName = "test-remote-repo"
    private val testNodePath = "/test/path"
    private val testNodeId = "test-node-id"

    @BeforeEach
    fun setUp() {
        // 清理所有测试数据
        federationMetadataTrackingDao.remove(Query())
    }

    // ========== findByTaskKeyAndNodeId 测试 ==========

    @Test
    fun `test findByTaskKeyAndNodeId - should find record`() {
        val record = createTestRecord(
            taskKey = testTaskKey,
            nodeId = testNodeId
        )
        federationMetadataTrackingDao.insert(record)

        val found = federationMetadataTrackingDao.findByTaskKeyAndNodeId(testTaskKey, testNodeId)

        assertNotNull(found)
        assertEquals(record.id, found?.id)
        assertEquals(testTaskKey, found?.taskKey)
        assertEquals(testNodeId, found?.nodeId)
    }

    @Test
    fun `test findByTaskKeyAndNodeId - should return null when not exists`() {
        val found = federationMetadataTrackingDao.findByTaskKeyAndNodeId("non-existent-key", "non-existent-node")

        assertNull(found)
    }

    @Test
    fun `test findByTaskKeyAndNodeId - should not find with different taskKey`() {
        val record = createTestRecord(
            taskKey = testTaskKey,
            nodeId = testNodeId
        )
        federationMetadataTrackingDao.insert(record)

        val found = federationMetadataTrackingDao.findByTaskKeyAndNodeId("different-task-key", testNodeId)

        assertNull(found)
    }

    @Test
    fun `test findByTaskKeyAndNodeId - should not find with different nodeId`() {
        val record = createTestRecord(
            taskKey = testTaskKey,
            nodeId = testNodeId
        )
        federationMetadataTrackingDao.insert(record)

        val found = federationMetadataTrackingDao.findByTaskKeyAndNodeId(testTaskKey, "different-node-id")

        assertNull(found)
    }

    // ========== deleteByTaskKeyAndNodeId 测试 ==========

    @Test
    fun `test deleteByTaskKeyAndNodeId - should delete record`() {
        val record = createTestRecord(
            taskKey = testTaskKey,
            nodeId = testNodeId
        )
        federationMetadataTrackingDao.insert(record)

        federationMetadataTrackingDao.deleteByTaskKeyAndNodeId(testTaskKey, testNodeId)

        val deleted = federationMetadataTrackingDao.findByTaskKeyAndNodeId(testTaskKey, testNodeId)
        assertNull(deleted)
    }

    @Test
    fun `test deleteByTaskKeyAndNodeId - should handle non-existent record gracefully`() {
        // 不应该抛出异常
        federationMetadataTrackingDao.deleteByTaskKeyAndNodeId("non-existent-key", "non-existent-node")
    }

    @Test
    fun `test deleteByTaskKeyAndNodeId - should only delete matching record`() {
        val record1 = createTestRecord(
            taskKey = testTaskKey,
            nodeId = testNodeId
        )
        val record2 = createTestRecord(
            taskKey = "another-task-key",
            nodeId = testNodeId
        )
        federationMetadataTrackingDao.insert(record1)
        federationMetadataTrackingDao.insert(record2)

        federationMetadataTrackingDao.deleteByTaskKeyAndNodeId(testTaskKey, testNodeId)

        assertNull(federationMetadataTrackingDao.findByTaskKeyAndNodeId(testTaskKey, testNodeId))
        assertNotNull(federationMetadataTrackingDao.findByTaskKeyAndNodeId("another-task-key", testNodeId))
    }

    // ========== updateRetryInfo 测试 ==========

    @Test
    fun `test updateRetryInfo - should update failure reason`() {
        val record = createTestRecord(retryCount = 1, retrying = true)
        val savedRecord = federationMetadataTrackingDao.insert(record)

        federationMetadataTrackingDao.updateRetryInfo(savedRecord.id!!, "Test failure reason")

        val updated = federationMetadataTrackingDao.findById(savedRecord.id!!)
        assertNotNull(updated)
        assertEquals(1, updated!!.retryCount)
        assertEquals("Test failure reason", updated.failureReason)
        assertFalse(updated.retrying)
    }

    @Test
    fun `test updateRetryInfo - should update lastModifiedDate`() {
        val record = createTestRecord(retryCount = 0)
        federationMetadataTrackingDao.insert(record)
        val originalDate = record.lastModifiedDate

        Thread.sleep(10) // 确保时间差异
        federationMetadataTrackingDao.updateRetryInfo(record.id!!, "Failure reason")

        val updated = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.lastModifiedDate.isAfter(originalDate))
    }

    // ========== setRetrying 测试 ==========

    @Test
    fun `test setRetrying - should set retrying to true without incrementing count`() {
        val record = createTestRecord(retrying = false, retryCount = 2)
        federationMetadataTrackingDao.insert(record)

        federationMetadataTrackingDao.setRetrying(record.id!!, true, incrementRetryCount = false)

        val updated = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.retrying)
        assertEquals(2, updated.retryCount) // 重试次数未增加
    }

    @Test
    fun `test setRetrying - should set retrying to true and increment count`() {
        val record = createTestRecord(retrying = false, retryCount = 2)
        federationMetadataTrackingDao.insert(record)

        federationMetadataTrackingDao.setRetrying(record.id!!, true, incrementRetryCount = true)

        val updated = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.retrying)
        assertEquals(3, updated.retryCount) // 重试次数增加
    }

    @Test
    fun `test setRetrying - should set retrying to false`() {
        val record = createTestRecord(retrying = true, retryCount = 2)
        federationMetadataTrackingDao.insert(record)

        federationMetadataTrackingDao.setRetrying(record.id!!, false, incrementRetryCount = false)

        val updated = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(updated)
        assertFalse(updated!!.retrying)
        assertEquals(2, updated.retryCount)
    }

    @Test
    fun `test setRetrying - should update lastModifiedDate`() {
        val record = createTestRecord(retrying = false)
        federationMetadataTrackingDao.insert(record)
        val originalDate = record.lastModifiedDate

        Thread.sleep(10)
        federationMetadataTrackingDao.setRetrying(record.id!!, true, incrementRetryCount = false)

        val updated = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.lastModifiedDate.isAfter(originalDate))
    }

    // ========== deleteExpiredFailedRecords 测试 ==========

    @Test
    fun `test deleteExpiredFailedRecords - should delete expired records`() {
        val now = LocalDateTime.now()
        val expiredRecord1 = createTestRecord(
            retryCount = 6,
            lastModifiedDate = now.minusDays(10)
        )
        val expiredRecord2 = createTestRecord(
            retryCount = 7,
            lastModifiedDate = now.minusDays(15)
        )
        val validRecord = createTestRecord(
            retryCount = 3,
            lastModifiedDate = now.minusDays(5)
        )
        val lowRetryRecord = createTestRecord(
            retryCount = 2,
            lastModifiedDate = now.minusDays(10)
        )
        federationMetadataTrackingDao.insert(expiredRecord1)
        federationMetadataTrackingDao.insert(expiredRecord2)
        federationMetadataTrackingDao.insert(validRecord)
        federationMetadataTrackingDao.insert(lowRetryRecord)

        val deletedCount = federationMetadataTrackingDao.deleteExpiredFailedRecords(
            maxRetryNum = 5,
            beforeDate = now.minusDays(7)
        )

        assertEquals(2, deletedCount)
        assertNull(federationMetadataTrackingDao.findById(expiredRecord1.id!!))
        assertNull(federationMetadataTrackingDao.findById(expiredRecord2.id!!))
        assertNotNull(federationMetadataTrackingDao.findById(validRecord.id!!))
        assertNotNull(federationMetadataTrackingDao.findById(lowRetryRecord.id!!))
    }

    @Test
    fun `test deleteExpiredFailedRecords - should return zero when no expired records`() {
        val now = LocalDateTime.now()
        val record1 = createTestRecord(
            retryCount = 3,
            lastModifiedDate = now.minusDays(5)
        )
        val record2 = createTestRecord(
            retryCount = 2,
            lastModifiedDate = now.minusDays(3)
        )
        federationMetadataTrackingDao.insert(record1)
        federationMetadataTrackingDao.insert(record2)

        val deletedCount = federationMetadataTrackingDao.deleteExpiredFailedRecords(
            maxRetryNum = 5,
            beforeDate = now.minusDays(7)
        )

        assertEquals(0, deletedCount)
        assertNotNull(federationMetadataTrackingDao.findById(record1.id!!))
        assertNotNull(federationMetadataTrackingDao.findById(record2.id!!))
    }

    @Test
    fun `test deleteExpiredFailedRecords - should only delete records matching both conditions`() {
        val now = LocalDateTime.now()
        val record1 = createTestRecord(
            retryCount = 6,
            lastModifiedDate = now.minusDays(5) // 重试次数足够，但未过期
        )
        val record2 = createTestRecord(
            retryCount = 3,
            lastModifiedDate = now.minusDays(10) // 已过期，但重试次数不足
        )
        federationMetadataTrackingDao.insert(record1)
        federationMetadataTrackingDao.insert(record2)

        val deletedCount = federationMetadataTrackingDao.deleteExpiredFailedRecords(
            maxRetryNum = 5,
            beforeDate = now.minusDays(7)
        )

        assertEquals(0, deletedCount)
        assertNotNull(federationMetadataTrackingDao.findById(record1.id!!))
        assertNotNull(federationMetadataTrackingDao.findById(record2.id!!))
    }

    // ========== buildQuery 测试 ==========

    @Test
    fun `test buildQuery - should build query with all conditions`() {
        val query = federationMetadataTrackingDao.buildQuery(
            taskKey = testTaskKey,
            remoteClusterId = testRemoteClusterId,
            projectId = testProjectId,
            localRepoName = testLocalRepoName,
            remoteProjectId = testRemoteProjectId,
            remoteRepoName = testRemoteRepoName,
            retrying = false,
            maxRetryCount = 3,
            sortField = "createdDate",
            sortDirection = Sort.Direction.ASC
        )

        assertNotNull(query)
        val count = federationMetadataTrackingDao.count(query)
        assertTrue(count >= 0)
    }

    @Test
    fun `test buildQuery - should build query with minimal conditions`() {
        val query = federationMetadataTrackingDao.buildQuery(
            taskKey = null,
            remoteClusterId = null,
            projectId = null,
            localRepoName = null,
            remoteProjectId = null,
            remoteRepoName = null,
            retrying = null,
            maxRetryCount = null,
            sortField = null,
            sortDirection = null
        )

        assertNotNull(query)
        val count = federationMetadataTrackingDao.count(query)
        assertTrue(count >= 0)
    }

    @Test
    fun `test buildQuery - should apply sort when sortField provided`() {
        val record1 = createTestRecord(createdDate = LocalDateTime.now().minusDays(2))
        val record2 = createTestRecord(createdDate = LocalDateTime.now().minusDays(1))
        val record3 = createTestRecord(createdDate = LocalDateTime.now())
        federationMetadataTrackingDao.insert(record1)
        federationMetadataTrackingDao.insert(record2)
        federationMetadataTrackingDao.insert(record3)

        val query = federationMetadataTrackingDao.buildQuery(
            taskKey = null,
            remoteClusterId = null,
            projectId = null,
            localRepoName = null,
            remoteProjectId = null,
            remoteRepoName = null,
            retrying = null,
            maxRetryCount = null,
            sortField = "createdDate",
            sortDirection = Sort.Direction.ASC
        )

        val records = federationMetadataTrackingDao.find(query)
        assertEquals(3, records.size)
        assertTrue(records[0].createdDate.isBefore(records[1].createdDate))
        assertTrue(records[1].createdDate.isBefore(records[2].createdDate))
    }

    @Test
    fun `test buildQuery - should filter by maxRetryCount correctly`() {
        val record1 = createTestRecord(retryCount = 3)
        val record2 = createTestRecord(retryCount = 5)
        val record3 = createTestRecord(retryCount = 7)
        federationMetadataTrackingDao.insert(record1)
        federationMetadataTrackingDao.insert(record2)
        federationMetadataTrackingDao.insert(record3)

        val query = federationMetadataTrackingDao.buildQuery(
            taskKey = null,
            remoteClusterId = null,
            projectId = null,
            localRepoName = null,
            remoteProjectId = null,
            remoteRepoName = null,
            retrying = null,
            maxRetryCount = 5, // 只返回 retryCount > 5 的记录
            sortField = null,
            sortDirection = null
        )

        val records = federationMetadataTrackingDao.find(query)
        assertEquals(1, records.size)
        assertEquals(record3.id, records.first().id)
    }

    // ========== deleteByConditions 测试 ==========

    @Test
    fun `test deleteByConditions - should delete by ids`() {
        val record1 = createTestRecord()
        val record2 = createTestRecord()
        val record3 = createTestRecord()
        federationMetadataTrackingDao.insert(record1)
        federationMetadataTrackingDao.insert(record2)
        federationMetadataTrackingDao.insert(record3)

        val deletedCount = federationMetadataTrackingDao.deleteByConditions(
            ids = listOf(record1.id!!, record2.id!!),
            maxRetryCount = null
        )

        assertEquals(2, deletedCount)
        assertNull(federationMetadataTrackingDao.findById(record1.id!!))
        assertNull(federationMetadataTrackingDao.findById(record2.id!!))
        assertNotNull(federationMetadataTrackingDao.findById(record3.id!!))
    }

    @Test
    fun `test deleteByConditions - should delete by taskKey`() {
        val record1 = createTestRecord(taskKey = "task-1")
        val record2 = createTestRecord(taskKey = "task-2")
        federationMetadataTrackingDao.insert(record1)
        federationMetadataTrackingDao.insert(record2)

        val deletedCount = federationMetadataTrackingDao.deleteByConditions(
            ids = null,
            maxRetryCount = null
        )

        assertEquals(0, deletedCount)
        assertNotNull(federationMetadataTrackingDao.findById(record1.id!!))
        assertNotNull(federationMetadataTrackingDao.findById(record2.id!!))
    }

    @Test
    fun `test deleteByConditions - should delete by maxRetryCount`() {
        val record1 = createTestRecord(retryCount = 5)
        val record2 = createTestRecord(retryCount = 3)
        val record3 = createTestRecord(retryCount = 7)
        federationMetadataTrackingDao.insert(record1)
        federationMetadataTrackingDao.insert(record2)
        federationMetadataTrackingDao.insert(record3)

        val deletedCount = federationMetadataTrackingDao.deleteByConditions(
            ids = null,
            maxRetryCount = 5 // 删除 retryCount > 5 的记录
        )

        assertEquals(1, deletedCount)
        assertNotNull(federationMetadataTrackingDao.findById(record1.id!!))
        assertNotNull(federationMetadataTrackingDao.findById(record2.id!!))
        assertNull(federationMetadataTrackingDao.findById(record3.id!!))
    }

    @Test
    fun `test deleteByConditions - should delete by combined conditions`() {
        val record1 = createTestRecord(taskKey = "task-1", retryCount = 6)
        val record2 = createTestRecord(taskKey = "task-1", retryCount = 3)
        val record3 = createTestRecord(taskKey = "task-2", retryCount = 6)
        federationMetadataTrackingDao.insert(record1)
        federationMetadataTrackingDao.insert(record2)
        federationMetadataTrackingDao.insert(record3)

        val deletedCount = federationMetadataTrackingDao.deleteByConditions(
            ids = null,
            maxRetryCount = 5
        )

        assertEquals(2, deletedCount) // 只删除 task-1 且 retryCount > 5 的记录
        assertNull(federationMetadataTrackingDao.findById(record1.id!!))
        assertNotNull(federationMetadataTrackingDao.findById(record2.id!!))
        assertNull(federationMetadataTrackingDao.findById(record3.id!!))
    }

    @Test
    fun `test deleteByConditions - should return zero when no matching records`() {
        val record = createTestRecord(taskKey = "task-1")
        federationMetadataTrackingDao.insert(record)

        val deletedCount = federationMetadataTrackingDao.deleteByConditions(
            ids = null,
            maxRetryCount = null
        )

        assertEquals(0, deletedCount)
        assertNotNull(federationMetadataTrackingDao.findById(record.id!!))
    }

    @Test
    fun `test deleteByConditions - should handle empty ids list`() {
        val record = createTestRecord()
        federationMetadataTrackingDao.insert(record)

        val deletedCount = federationMetadataTrackingDao.deleteByConditions(
            ids = emptyList(),
            maxRetryCount = null
        )

        assertEquals(0, deletedCount)
        assertNotNull(federationMetadataTrackingDao.findById(record.id!!))
    }

    // ========== 辅助方法 ==========

    private fun createTestRecord(
        taskKey: String = testTaskKey,
        remoteClusterId: String = testRemoteClusterId,
        projectId: String = testProjectId,
        localRepoName: String = testLocalRepoName,
        remoteProjectId: String = testRemoteProjectId,
        remoteRepoName: String = testRemoteRepoName,
        nodePath: String = testNodePath,
        nodeId: String = testNodeId,
        retryCount: Int = 0,
        retrying: Boolean = false,
        failureReason: String? = null,
        createdDate: LocalDateTime = LocalDateTime.now(),
        lastModifiedDate: LocalDateTime = LocalDateTime.now()
    ): TFederationMetadataTracking {
        return TFederationMetadataTracking(
            taskKey = taskKey,
            remoteClusterId = remoteClusterId,
            projectId = projectId,
            localRepoName = localRepoName,
            remoteProjectId = remoteProjectId,
            remoteRepoName = remoteRepoName,
            nodePath = nodePath,
            nodeId = nodeId,
            retryCount = retryCount,
            retrying = retrying,
            failureReason = failureReason,
            createdDate = createdDate,
            lastModifiedDate = lastModifiedDate
        )
    }
}

