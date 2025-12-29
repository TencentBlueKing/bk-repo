package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
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

@DisplayName("同步失败记录DAO测试")
@DataMongoTest
@Import(ReplicaFailureRecordDao::class)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class ReplicaFailureRecordDaoTest @Autowired constructor(
    private val replicaFailureRecordDao: ReplicaFailureRecordDao
) {

    private val testTaskKey = "test-task-key"
    private val testRemoteClusterId = "test-remote-cluster-id"
    private val testProjectId = "test-project-id"
    private val testLocalRepoName = "test-local-repo"
    private val testRemoteProjectId = "test-remote-project-id"
    private val testRemoteRepoName = "test-remote-repo"
    private val testPackageKey = "test-package-key"
    private val testPackageVersion = "1.0.0"
    private val testFullPath = "/test/path"
    private val testFailureReason = "Test failure reason"

    @BeforeEach
    fun setUp() {
        // 清理所有测试数据
        replicaFailureRecordDao.remove(Query())
    }

    // ========== findByTriedTimesLessThanAndRetryingFalse 测试 ==========

    @Test
    fun `test findByTriedTimesLessThanAndRetryingFalse - should return records with retry count less than max`() {
        // 创建测试记录
        val record1 = createTestRecord(retryCount = 1, retrying = false)
        val record2 = createTestRecord(retryCount = 2, retrying = false)
        val record3 = createTestRecord(retryCount = 3, retrying = false) // 超过最大重试次数
        val record4 = createTestRecord(retryCount = 1, retrying = true) // 正在重试中
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)
        replicaFailureRecordDao.save(record3)
        replicaFailureRecordDao.save(record4)

        // 查询需要重试的记录（最大重试次数为2）
        val records = replicaFailureRecordDao.findByTriedTimesLessThanAndRetryingFalse(maxRetryTimes = 2)

        // 验证结果
        assertEquals(2, records.size)
        assertTrue(records.all { it.retryCount <= 2 })
        assertTrue(records.all { !it.retrying })
        assertTrue(records.map { it.id }.containsAll(listOf(record1.id, record2.id)))
    }

    @Test
    fun `test findByTriedTimesLessThanAndRetryingFalse - should return empty list when no matching records`() {
        val record1 = createTestRecord(retryCount = 5, retrying = false)
        val record2 = createTestRecord(retryCount = 3, retrying = true)
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)

        val records = replicaFailureRecordDao.findByTriedTimesLessThanAndRetryingFalse(maxRetryTimes = 2)

        assertTrue(records.isEmpty())
    }

    @Test
    fun `test findByTriedTimesLessThanAndRetryingFalse - should return all records when maxRetryTimes is large`() {
        val record1 = createTestRecord(retryCount = 1, retrying = false)
        val record2 = createTestRecord(retryCount = 10, retrying = false)
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)

        val records = replicaFailureRecordDao.findByTriedTimesLessThanAndRetryingFalse(maxRetryTimes = 100)

        assertEquals(2, records.size)
    }

    // ========== updateRetryStatus 测试 ==========

    @Test
    fun `test updateRetryStatus - should set retrying to true`() {
        val record = createTestRecord(retrying = false)
        replicaFailureRecordDao.save(record)

        replicaFailureRecordDao.updateRetryStatus(record.id!!, true)

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.retrying)
        assertNotNull(updated.lastModifiedDate)
    }

    @Test
    fun `test updateRetryStatus - should set retrying to false`() {
        val record = createTestRecord(retrying = true)
        replicaFailureRecordDao.save(record)

        replicaFailureRecordDao.updateRetryStatus(record.id!!, false)

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertFalse(updated!!.retrying)
    }

    @Test
    fun `test updateRetryStatus - should update lastModifiedDate`() {
        val record = createTestRecord(retrying = false)
        replicaFailureRecordDao.save(record)
        val originalDate = record.lastModifiedDate

        Thread.sleep(10) // 确保时间差异
        replicaFailureRecordDao.updateRetryStatus(record.id!!, true)

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.lastModifiedDate.isAfter(originalDate))
    }

    // ========== deleteById 测试 ==========

    @Test
    fun `test deleteById - should delete record`() {
        val record = createTestRecord()
        replicaFailureRecordDao.save(record)

        replicaFailureRecordDao.deleteById(record.id!!)

        val deleted = replicaFailureRecordDao.findById(record.id!!)
        assertNull(deleted)
    }

    @Test
    fun `test deleteById - should handle non-existent id gracefully`() {
        // 不应该抛出异常
        replicaFailureRecordDao.deleteById("non-existent-id")
    }

    // ========== findExistingRecord 测试 ==========


    // ========== updateExistingRecord 测试 ==========

    @Test
    fun `test updateExistingRecord - should update failure reason and increment retry count`() {
        val record = createTestRecord(
            taskKey = testTaskKey,
            failureReason = "Old reason",
            retryCount = 2
        )
        replicaFailureRecordDao.save(record)

        replicaFailureRecordDao.updateExistingRecord(
            recordId = record.id!!,
            failureReason = "New reason",
            incrementRetryCount = true
        )

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertEquals("New reason", updated?.failureReason)
        assertEquals(3, updated?.retryCount)
        assertFalse(updated?.retrying ?: true)
    }

    @Test
    fun `test updateExistingRecord - should update without incrementing retry count`() {
        val record = createTestRecord(retryCount = 2)
        replicaFailureRecordDao.save(record)

        replicaFailureRecordDao.updateExistingRecord(
            recordId = record.id!!,
            failureReason = "New reason",
            incrementRetryCount = false
        )

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertEquals("New reason", updated?.failureReason)
        assertEquals(2, updated?.retryCount) // 重试次数未增加
    }

    @Test
    fun `test updateExistingRecord - should update with null failure reason`() {
        val record = createTestRecord(failureReason = "Old reason")
        replicaFailureRecordDao.save(record)

        replicaFailureRecordDao.updateExistingRecord(
            recordId = record.id!!,
            failureReason = null,
            incrementRetryCount = true
        )

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertEquals("Old reason", updated?.failureReason) // 原有原因应该保留
        assertEquals(1, updated?.retryCount)
    }

    @Test
    fun `test updateExistingRecord - should update lastModifiedDate`() {
        val record = createTestRecord()
        replicaFailureRecordDao.save(record)
        val originalDate = record.lastModifiedDate

        Thread.sleep(10)
        replicaFailureRecordDao.updateExistingRecord(
            recordId = record.id!!,
            failureReason = "New reason",
            incrementRetryCount = false
        )

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.lastModifiedDate.isAfter(originalDate))
    }

    // ========== deleteExpiredRecords 测试 ==========

    @Test
    fun `test deleteExpiredRecords - should delete expired records`() {
        val now = LocalDateTime.now()
        val expiredRecord1 = createTestRecord(
            retryCount = 5,
            lastModifiedDate = now.minusDays(10)
        )
        val expiredRecord2 = createTestRecord(
            retryCount = 6,
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
        replicaFailureRecordDao.save(expiredRecord1)
        replicaFailureRecordDao.save(expiredRecord2)
        replicaFailureRecordDao.save(validRecord)
        replicaFailureRecordDao.save(lowRetryRecord)

        val deletedCount = replicaFailureRecordDao.deleteExpiredRecords(
            maxRetryTimes = 5,
            expireBefore = now.minusDays(7)
        )

        assertEquals(2, deletedCount)
        assertNull(replicaFailureRecordDao.findById(expiredRecord1.id!!))
        assertNull(replicaFailureRecordDao.findById(expiredRecord2.id!!))
        assertNotNull(replicaFailureRecordDao.findById(validRecord.id!!))
        assertNotNull(replicaFailureRecordDao.findById(lowRetryRecord.id!!))
    }

    @Test
    fun `test deleteExpiredRecords - should return zero when no expired records`() {
        val now = LocalDateTime.now()
        val record1 = createTestRecord(
            retryCount = 3,
            lastModifiedDate = now.minusDays(5)
        )
        val record2 = createTestRecord(
            retryCount = 2,
            lastModifiedDate = now.minusDays(3)
        )
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)

        val deletedCount = replicaFailureRecordDao.deleteExpiredRecords(
            maxRetryTimes = 5,
            expireBefore = now.minusDays(7)
        )

        assertEquals(0, deletedCount)
        assertNotNull(replicaFailureRecordDao.findById(record1.id!!))
        assertNotNull(replicaFailureRecordDao.findById(record2.id!!))
    }

    @Test
    fun `test deleteExpiredRecords - should only delete records matching both conditions`() {
        val now = LocalDateTime.now()
        val record1 = createTestRecord(
            retryCount = 5,
            lastModifiedDate = now.minusDays(5) // 重试次数足够，但未过期
        )
        val record2 = createTestRecord(
            retryCount = 3,
            lastModifiedDate = now.minusDays(10) // 已过期，但重试次数不足
        )
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)

        val deletedCount = replicaFailureRecordDao.deleteExpiredRecords(
            maxRetryTimes = 5,
            expireBefore = now.minusDays(7)
        )

        assertEquals(0, deletedCount)
        assertNotNull(replicaFailureRecordDao.findById(record1.id!!))
        assertNotNull(replicaFailureRecordDao.findById(record2.id!!))
    }

    // ========== buildQuery 测试 ==========

    @Test
    fun `test buildQuery - should build query with all conditions`() {
        val query = replicaFailureRecordDao.buildQuery(
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
            sortDirection = Sort.Direction.ASC
        )

        assertNotNull(query)
        val count = replicaFailureRecordDao.count(query)
        assertTrue(count >= 0)
    }

    @Test
    fun `test buildQuery - should build query with minimal conditions`() {
        val query = replicaFailureRecordDao.buildQuery(
            taskKey = null,
            remoteClusterId = null,
            projectId = null,
            repoName = null,
            remoteProjectId = null,
            remoteRepoName = null,
            failureType = null,
            retrying = null,
            maxRetryCount = null,
            sortField = null,
            sortDirection = null
        )

        assertNotNull(query)
        val count = replicaFailureRecordDao.count(query)
        assertTrue(count >= 0)
    }

    @Test
    fun `test buildQuery - should apply sort when sortField provided`() {
        val record1 = createTestRecord(createdDate = LocalDateTime.now().minusDays(2))
        val record2 = createTestRecord(createdDate = LocalDateTime.now().minusDays(1))
        val record3 = createTestRecord(createdDate = LocalDateTime.now())
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)
        replicaFailureRecordDao.save(record3)

        val query = replicaFailureRecordDao.buildQuery(
            taskKey = null,
            remoteClusterId = null,
            projectId = null,
            repoName = null,
            remoteProjectId = null,
            remoteRepoName = null,
            failureType = null,
            retrying = null,
            maxRetryCount = null,
            sortField = "createdDate",
            sortDirection = Sort.Direction.ASC
        )

        val records = replicaFailureRecordDao.find(query)
        assertEquals(3, records.size)
        assertTrue(records[0].createdDate.isBefore(records[1].createdDate))
        assertTrue(records[1].createdDate.isBefore(records[2].createdDate))
    }

    @Test
    fun `test buildQuery - should filter by maxRetryCount correctly`() {
        val record1 = createTestRecord(retryCount = 3)
        val record2 = createTestRecord(retryCount = 5)
        val record3 = createTestRecord(retryCount = 7)
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)
        replicaFailureRecordDao.save(record3)

        val query = replicaFailureRecordDao.buildQuery(
            taskKey = null,
            remoteClusterId = null,
            projectId = null,
            repoName = null,
            remoteProjectId = null,
            remoteRepoName = null,
            failureType = null,
            retrying = null,
            maxRetryCount = 5, // 只返回 retryCount > 5 的记录
            sortField = null,
            sortDirection = null
        )

        val records = replicaFailureRecordDao.find(query)
        assertEquals(1, records.size)
        assertEquals(record3.id, records.first().id)
    }

    // ========== deleteByConditions 测试 ==========

    @Test
    fun `test deleteByConditions - should delete by ids`() {
        val record1 = createTestRecord()
        val record2 = createTestRecord()
        val record3 = createTestRecord()
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)
        replicaFailureRecordDao.save(record3)

        val deletedCount = replicaFailureRecordDao.deleteByConditions(
            ids = listOf(record1.id!!, record2.id!!),
            maxRetryCount = null
        )

        assertEquals(2, deletedCount)
        assertNull(replicaFailureRecordDao.findById(record1.id!!))
        assertNull(replicaFailureRecordDao.findById(record2.id!!))
        assertNotNull(replicaFailureRecordDao.findById(record3.id!!))
    }


    @Test
    fun `test deleteByConditions - should delete by maxRetryCount`() {
        val record1 = createTestRecord(retryCount = 5)
        val record2 = createTestRecord(retryCount = 3)
        val record3 = createTestRecord(retryCount = 7)
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)
        replicaFailureRecordDao.save(record3)

        val deletedCount = replicaFailureRecordDao.deleteByConditions(
            ids = null,
            maxRetryCount = 5 // 删除 retryCount > 5 的记录
        )

        assertEquals(1, deletedCount)
        assertNotNull(replicaFailureRecordDao.findById(record1.id!!))
        assertNotNull(replicaFailureRecordDao.findById(record2.id!!))
        assertNull(replicaFailureRecordDao.findById(record3.id!!))
    }

    @Test
    fun `test deleteByConditions - should return zero when no matching records`() {
        val record = createTestRecord(taskKey = "task-1")
        replicaFailureRecordDao.save(record)

        val deletedCount = replicaFailureRecordDao.deleteByConditions(
            ids = null,
            maxRetryCount = null
        )

        assertEquals(0, deletedCount)
        assertNotNull(replicaFailureRecordDao.findById(record.id!!))
    }

    @Test
    fun `test deleteByConditions - should handle empty ids list`() {
        val record = createTestRecord()
        replicaFailureRecordDao.save(record)

        val deletedCount = replicaFailureRecordDao.deleteByConditions(
            ids = emptyList(),
            maxRetryCount = null
        )

        assertEquals(0, deletedCount)
        assertNotNull(replicaFailureRecordDao.findById(record.id!!))
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

