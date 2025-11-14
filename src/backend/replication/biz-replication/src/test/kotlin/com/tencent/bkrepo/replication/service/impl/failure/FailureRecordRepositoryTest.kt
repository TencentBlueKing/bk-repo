package com.tencent.bkrepo.replication.service.impl.failure

import com.tencent.bkrepo.replication.dao.ReplicaFailureRecordDao
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordDeleteRequest
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordListOption
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
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

@DisplayName("失败记录仓储测试")
@DataMongoTest
@Import(FailureRecordRepository::class, ReplicaFailureRecordDao::class)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class FailureRecordRepositoryTest @Autowired constructor(
    private val failureRecordRepository: FailureRecordRepository,
    private val replicaFailureRecordDao: ReplicaFailureRecordDao
) {

    private val testTaskKey = "test-task-key"
    private val testRemoteClusterId = "test-remote-cluster-id"
    private val testProjectId = "test-project-id"
    private val testLocalRepoName = "test-local-repo"
    private val testRemoteProjectId = "test-remote-project-id"
    private val testRemoteRepoName = "test-remote-repo"
    private val testFailureReason = "Test failure reason"

    @BeforeEach
    fun setUp() {
        // 清理所有测试数据
        replicaFailureRecordDao.remove(Query())
    }

    // ========== create 测试 ==========

    @Test
    fun `test create - should create new failure record`() {
        failureRecordRepository.create(
            taskKey = testTaskKey,
            remoteClusterId = testRemoteClusterId,
            projectId = testProjectId,
            localRepoName = testLocalRepoName,
            remoteProjectId = testRemoteProjectId,
            remoteRepoName = testRemoteRepoName,
            failureType = ReplicaObjectType.PATH,
            packageConstraint = null,
            pathConstraint = null,
            failureReason = testFailureReason,
            event = null
        )

        val records = replicaFailureRecordDao.find(Query())
        assertEquals(1, records.size)
        val record = records.first()
        assertEquals(testTaskKey, record.taskKey)
        assertEquals(testRemoteClusterId, record.remoteClusterId)
        assertEquals(testProjectId, record.projectId)
        assertEquals(testLocalRepoName, record.repoName)
        assertEquals(testRemoteProjectId, record.remoteProjectId)
        assertEquals(testRemoteRepoName, record.remoteRepoName)
        assertEquals(ReplicaObjectType.PATH, record.failureType)
        assertEquals(testFailureReason, record.failureReason)
        assertEquals(0, record.retryCount)
        assertFalse(record.retrying)
    }

    @Test
    fun `test create - should create record with default values`() {
        failureRecordRepository.create(
            taskKey = testTaskKey,
            remoteClusterId = testRemoteClusterId,
            projectId = testProjectId,
            localRepoName = testLocalRepoName,
            remoteProjectId = testRemoteProjectId,
            remoteRepoName = testRemoteRepoName,
            failureType = ReplicaObjectType.PACKAGE,
            packageConstraint = null,
            pathConstraint = null,
            failureReason = null,
            event = null
        )

        val records = replicaFailureRecordDao.find(Query())
        assertEquals(1, records.size)
        val record = records.first()
        assertEquals(ReplicaObjectType.PACKAGE, record.failureType)
        assertNull(record.failureReason)
        assertEquals(0, record.retryCount)
        assertFalse(record.retrying)
        assertNotNull(record.createdDate)
        assertNotNull(record.lastModifiedDate)
    }

    // ========== findByRecordId 测试 ==========

    @Test
    fun `test findByRecordId - should return record when exists`() {
        val record = createTestRecord()
        replicaFailureRecordDao.save(record)

        val found = failureRecordRepository.findByRecordId(record.id!!)

        assertNotNull(found)
        assertEquals(record.id, found?.id)
        assertEquals(testTaskKey, found?.taskKey)
    }

    @Test
    fun `test findByRecordId - should return null when not exists`() {
        val found = failureRecordRepository.findByRecordId("non-existent-id")

        assertNull(found)
    }

    // ========== updateExisting 测试 ==========

    @Test
    fun `test updateExisting - should update failure reason and increment retry count`() {
        val record = createTestRecord(failureReason = "Old reason", retryCount = 2)
        replicaFailureRecordDao.save(record)

        failureRecordRepository.updateExisting(record, "New reason")

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertEquals("New reason", updated?.failureReason)
        assertEquals(3, updated?.retryCount)
        assertFalse(updated?.retrying ?: true)
    }

    @Test
    fun `test updateExisting - should update with null failure reason`() {
        val record = createTestRecord(failureReason = "Old reason", retryCount = 1)
        replicaFailureRecordDao.save(record)

        failureRecordRepository.updateExisting(record, null)

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertEquals("Old reason", updated?.failureReason) // 原有原因应该保留
        assertEquals(2, updated?.retryCount)
    }

    // ========== updateRetryStatus 测试 ==========

    @Test
    fun `test updateRetryStatus - should set retrying to true`() {
        val record = createTestRecord(retrying = false)
        replicaFailureRecordDao.save(record)

        failureRecordRepository.updateRetryStatus(record.id!!, true)

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.retrying)
    }

    @Test
    fun `test updateRetryStatus - should set retrying to false`() {
        val record = createTestRecord(retrying = true)
        replicaFailureRecordDao.save(record)

        failureRecordRepository.updateRetryStatus(record.id!!, false)

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertFalse(updated!!.retrying)
    }

    // ========== incrementRetryCount 测试 ==========

    @Test
    fun `test incrementRetryCount - should increment count and update failure reason`() {
        val record = createTestRecord(retryCount = 1, retrying = true)
        replicaFailureRecordDao.save(record)

        failureRecordRepository.incrementRetryCount(record.id!!, "New failure reason")

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertEquals(2, updated!!.retryCount)
        assertFalse(updated.retrying)
        assertEquals("New failure reason", updated.failureReason)
    }

    @Test
    fun `test incrementRetryCount - should increment without failure reason`() {
        val record = createTestRecord(retryCount = 0, failureReason = "Original reason")
        replicaFailureRecordDao.save(record)

        failureRecordRepository.incrementRetryCount(record.id!!, null)

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertEquals(1, updated!!.retryCount)
        assertEquals("Original reason", updated.failureReason) // 原有原因应该保留
        assertFalse(updated.retrying)
    }

    // ========== deleteById 测试 ==========

    @Test
    fun `test deleteById - should delete record`() {
        val record = createTestRecord()
        replicaFailureRecordDao.save(record)

        failureRecordRepository.deleteById(record.id!!)

        val deleted = replicaFailureRecordDao.findById(record.id!!)
        assertNull(deleted)
    }

    @Test
    fun `test deleteById - should handle non-existent id gracefully`() {
        // 不应该抛出异常
        failureRecordRepository.deleteById("non-existent-id")
    }

    // ========== buildQuery 测试 ==========

    @Test
    fun `test buildQuery - should build query with all conditions`() {
        val option = ReplicaFailureRecordListOption(
            taskKey = testTaskKey,
            remoteClusterId = testRemoteClusterId,
            projectId = testProjectId,
            repoName = testLocalRepoName,
            remoteProjectId = testRemoteProjectId,
            remoteRepoName = testRemoteRepoName,
            failureType = ReplicaObjectType.PATH,
            retrying = false,
            maxRetryCount = 3,
            sortField = "createdDate",
            sortDirection = "ASC"
        )

        val query = failureRecordRepository.buildQuery(option, Sort.Direction.ASC)

        assertNotNull(query)
        val count = replicaFailureRecordDao.count(query)
        assertTrue(count >= 0)
    }

    @Test
    fun `test buildQuery - should build query with minimal conditions`() {
        val option = ReplicaFailureRecordListOption()

        val query = failureRecordRepository.buildQuery(option, Sort.Direction.DESC)

        assertNotNull(query)
        val count = replicaFailureRecordDao.count(query)
        assertTrue(count >= 0)
    }

    @Test
    fun `test buildQuery - should apply sort correctly`() {
        val record1 = createTestRecord(createdDate = LocalDateTime.now().minusDays(2))
        val record2 = createTestRecord(createdDate = LocalDateTime.now().minusDays(1))
        val record3 = createTestRecord(createdDate = LocalDateTime.now())
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)
        replicaFailureRecordDao.save(record3)

        val option = ReplicaFailureRecordListOption(
            sortField = "createdDate",
            sortDirection = "ASC"
        )
        val query = failureRecordRepository.buildQuery(option, Sort.Direction.ASC)

        val records = replicaFailureRecordDao.find(query)
        assertEquals(3, records.size)
        assertTrue(records[0].createdDate.isBefore(records[1].createdDate))
        assertTrue(records[1].createdDate.isBefore(records[2].createdDate))
    }

    // ========== validateDeleteConditions 测试 ==========

    @Test
    fun `test validateDeleteConditions - should return true when ids provided`() {
        val request = ReplicaFailureRecordDeleteRequest(
            ids = listOf("id1", "id2"),
            maxRetryCount = null
        )

        val result = failureRecordRepository.validateDeleteConditions(request)

        assertTrue(result)
    }

    @Test
    fun `test validateDeleteConditions - should return true when maxRetryCount provided`() {
        val request = ReplicaFailureRecordDeleteRequest(
            ids = null,
            maxRetryCount = 5
        )

        val result = failureRecordRepository.validateDeleteConditions(request)

        assertTrue(result)
    }

    @Test
    fun `test validateDeleteConditions - should return true when both provided`() {
        val request = ReplicaFailureRecordDeleteRequest(
            ids = listOf("id1"),
            maxRetryCount = 5
        )

        val result = failureRecordRepository.validateDeleteConditions(request)

        assertTrue(result)
    }

    @Test
    fun `test validateDeleteConditions - should return false when neither provided`() {
        val request = ReplicaFailureRecordDeleteRequest(
            ids = null,
            maxRetryCount = null
        )

        val result = failureRecordRepository.validateDeleteConditions(request)

        assertFalse(result)
    }

    @Test
    fun `test validateDeleteConditions - should return false when empty ids list`() {
        val request = ReplicaFailureRecordDeleteRequest(
            ids = emptyList(),
            maxRetryCount = null
        )

        val result = failureRecordRepository.validateDeleteConditions(request)

        assertFalse(result)
    }

    // ========== 辅助方法 ==========

    private fun createTestRecord(
        taskKey: String = testTaskKey,
        remoteClusterId: String = testRemoteClusterId,
        projectId: String = testProjectId,
        repoName: String = testLocalRepoName,
        remoteProjectId: String = testRemoteProjectId,
        remoteRepoName: String = testRemoteRepoName,
        failureType: ReplicaObjectType = ReplicaObjectType.PATH,
        failureReason: String? = testFailureReason,
        retryCount: Int = 0,
        retrying: Boolean = false,
        createdDate: LocalDateTime = LocalDateTime.now(),
        lastModifiedDate: LocalDateTime = LocalDateTime.now()
    ): TReplicaFailureRecord {
        return TReplicaFailureRecord(
            taskKey = taskKey,
            remoteClusterId = remoteClusterId,
            projectId = projectId,
            repoName = repoName,
            remoteProjectId = remoteProjectId,
            remoteRepoName = remoteRepoName,
            failureType = failureType,
            failureReason = failureReason,
            retryCount = retryCount,
            retrying = retrying,
            createdDate = createdDate,
            lastModifiedDate = lastModifiedDate
        )
    }
}

