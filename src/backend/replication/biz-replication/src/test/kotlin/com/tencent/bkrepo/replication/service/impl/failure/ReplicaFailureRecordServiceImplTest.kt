package com.tencent.bkrepo.replication.service.impl.failure

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.replication.dao.ReplicaFailureRecordDao
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordDeleteRequest
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordListOption
import com.tencent.bkrepo.replication.pojo.failure.ReplicaFailureRecordRetryRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.service.ReplicaRetryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime

@DisplayName("同步失败记录服务实现测试")
@DataMongoTest
@Import(ReplicaFailureRecordDao::class)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class ReplicaFailureRecordServiceImplTest @Autowired constructor(
    private val replicaFailureRecordDao: ReplicaFailureRecordDao
) {

    @MockitoBean
    private lateinit var replicaRetryService: ReplicaRetryService

    private lateinit var replicaFailureRecordService: ReplicaFailureRecordServiceImpl
    private lateinit var failureRecordRepository: FailureRecordRepository
    private lateinit var failureRecordRetryExecutor: FailureRecordRetryExecutor
    private lateinit var retryStateManager: FailureRecordRetryStateManager

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

        // 初始化依赖
        failureRecordRepository = FailureRecordRepository(replicaFailureRecordDao)
        failureRecordRetryExecutor = FailureRecordRetryExecutor(
            replicaRetryService = replicaRetryService,
            failureRecordRepository = failureRecordRepository
        )
        retryStateManager = FailureRecordRetryStateManager(failureRecordRepository)

        // 创建服务实例
        replicaFailureRecordService = ReplicaFailureRecordServiceImpl(
            failureRecordRepository = failureRecordRepository,
            failureRecordRetryExecutor = failureRecordRetryExecutor,
            retryStateManager = retryStateManager
        )
    }

    // ========== recordFailure 测试 ==========

    @Test
    fun `test recordFailure - should create new record when not exists`() {
        failureRecordRepository.recordFailure(
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
            event = null,
            failedRecordId = null
        )

        val records = replicaFailureRecordDao.find(Query())
        assertEquals(1, records.size)
        val record = records.first()
        assertEquals(testTaskKey, record.taskKey)
        assertEquals(testFailureReason, record.failureReason)
        assertEquals(0, record.retryCount)
    }

    @Test
    fun `test recordFailure - should update existing record when failedRecordId provided`() {
        val existingRecord = createTestRecord(failureReason = "Old reason", retryCount = 1)
        replicaFailureRecordDao.save(existingRecord)

        failureRecordRepository.recordFailure(
            taskKey = testTaskKey,
            remoteClusterId = testRemoteClusterId,
            projectId = testProjectId,
            localRepoName = testLocalRepoName,
            remoteProjectId = testRemoteProjectId,
            remoteRepoName = testRemoteRepoName,
            failureType = ReplicaObjectType.PATH,
            packageConstraint = null,
            pathConstraint = null,
            failureReason = "New reason",
            event = null,
            failedRecordId = existingRecord.id
        )

        val records = replicaFailureRecordDao.find(Query())
        assertEquals(1, records.size)
        val record = records.first()
        assertEquals("New reason", record.failureReason)
        assertEquals(2, record.retryCount) // 应该增加了1
    }

    @Test
    fun `test recordFailure - should create new record when failedRecordId is empty`() {
        failureRecordRepository.recordFailure(
            taskKey = testTaskKey,
            remoteClusterId = testRemoteClusterId,
            projectId = testProjectId,
            localRepoName = testLocalRepoName,
            remoteProjectId = testRemoteProjectId,
            remoteRepoName = testRemoteRepoName,
            failureType = ReplicaObjectType.PACKAGE,
            packageConstraint = null,
            pathConstraint = null,
            failureReason = testFailureReason,
            event = null,
            failedRecordId = ""
        )

        val records = replicaFailureRecordDao.find(Query())
        assertEquals(1, records.size)
        assertEquals(ReplicaObjectType.PACKAGE, records.first().failureType)
    }

    // ========== getRecordsForRetry 测试 ==========

    @Test
    fun `test getRecordsForRetry - should return records with retry count less than max`() {
        val record1 = createTestRecord(retryCount = 1, retrying = false)
        val record2 = createTestRecord(retryCount = 2, retrying = false)
        val record3 = createTestRecord(retryCount = 3, retrying = false)
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)
        replicaFailureRecordDao.save(record3)

        val records = replicaFailureRecordService.getRecordsForRetry(maxRetryTimes = 2)

        assertEquals(2, records.size)
        assertTrue(records.all { it.retryCount <= 2 })
        assertTrue(records.all { !it.retrying })
    }

    // ========== updateRetryStatus 测试 ==========

    @Test
    fun `test updateRetryStatus - should update retry status`() {
        val record = createTestRecord(retrying = false)
        replicaFailureRecordDao.save(record)

        replicaFailureRecordService.updateRetryStatus(record.id!!, true)

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.retrying)
    }

    // ========== incrementRetryCount 测试 ==========

    @Test
    fun `test incrementRetryCount - should increment retry count`() {
        val record = createTestRecord(retryCount = 1)
        replicaFailureRecordDao.save(record)

        replicaFailureRecordService.incrementRetryCount(record.id!!, "New failure")

        val updated = replicaFailureRecordDao.findById(record.id!!)
        assertNotNull(updated)
        assertEquals(2, updated!!.retryCount)
        assertEquals("New failure", updated.failureReason)
    }

    // ========== deleteRecord 测试 ==========

    @Test
    fun `test deleteRecord - should delete record`() {
        val record = createTestRecord()
        replicaFailureRecordDao.save(record)

        replicaFailureRecordService.deleteRecord(record.id!!)

        val deleted = replicaFailureRecordDao.findById(record.id!!)
        assertNull(deleted)
    }

    // ========== cleanExpiredRecords 测试 ==========

    @Test
    fun `test cleanExpiredRecords - should delete expired records`() {
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
        replicaFailureRecordDao.save(expiredRecord1)
        replicaFailureRecordDao.save(expiredRecord2)
        replicaFailureRecordDao.save(validRecord)

        val deletedCount = replicaFailureRecordService.cleanExpiredRecords(
            maxRetryNum = 5,
            retentionDays = 7
        )

        assertEquals(2, deletedCount)
        assertNull(replicaFailureRecordDao.findById(expiredRecord1.id!!))
        assertNull(replicaFailureRecordDao.findById(expiredRecord2.id!!))
        assertNotNull(replicaFailureRecordDao.findById(validRecord.id!!))
    }

    // ========== listPage 测试 ==========

    @Test
    fun `test listPage - should return paginated results`() {
        // 创建多条记录
        repeat(5) { index ->
            val record = createTestRecord(taskKey = "task-$index")
            replicaFailureRecordDao.save(record)
        }

        val option = ReplicaFailureRecordListOption(
            pageNumber = 1,
            pageSize = 2,
            taskKey = null
        )

        val page = replicaFailureRecordService.listPage(option)

        assertEquals(5, page.totalRecords)
        assertEquals(2, page.records.size)
    }

    @Test
    fun `test listPage - should filter by taskKey`() {
        val record1 = createTestRecord(taskKey = "task-1")
        val record2 = createTestRecord(taskKey = "task-2")
        replicaFailureRecordDao.save(record1)
        replicaFailureRecordDao.save(record2)

        val option = ReplicaFailureRecordListOption(
            pageNumber = 1,
            pageSize = 10,
            taskKey = "task-1"
        )

        val page = replicaFailureRecordService.listPage(option)

        assertEquals(1, page.totalRecords)
        assertEquals("task-1", page.records.first().taskKey)
    }

    @Test
    fun `test listPage - should handle invalid sort direction`() {
        val record = createTestRecord()
        replicaFailureRecordDao.save(record)

        val option = ReplicaFailureRecordListOption(
            pageNumber = 1,
            pageSize = 10,
            sortDirection = "INVALID"
        )

        // 应该使用默认的 DESC 方向
        val page = replicaFailureRecordService.listPage(option)

        assertEquals(1, page.totalRecords)
    }

    // ========== findById 测试 ==========

    @Test
    fun `test findById - should return record when exists`() {
        val record = createTestRecord()
        replicaFailureRecordDao.save(record)

        val found = replicaFailureRecordService.findById(record.id!!)

        assertNotNull(found)
        assertEquals(record.id, found?.id)
    }

    @Test
    fun `test findById - should return null when not exists`() {
        val found = replicaFailureRecordService.findById("non-existent-id")

        assertNull(found)
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

        val request = ReplicaFailureRecordDeleteRequest(
            ids = listOf(record1.id!!, record2.id!!),
            maxRetryCount = null
        )

        val deletedCount = replicaFailureRecordService.deleteByConditions(request)

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

        val request = ReplicaFailureRecordDeleteRequest(
            ids = null,
            maxRetryCount = 5
        )

        val deletedCount = replicaFailureRecordService.deleteByConditions(request)

        assertEquals(1, deletedCount)
        assertNotNull(replicaFailureRecordDao.findById(record1.id!!))
        assertNotNull(replicaFailureRecordDao.findById(record2.id!!))
        assertNull(replicaFailureRecordDao.findById(record3.id!!))
    }

    @Test
    fun `test deleteByConditions - should throw exception when no conditions provided`() {
        val request = ReplicaFailureRecordDeleteRequest(
            ids = null,
            maxRetryCount = null
        )

        assertThrows<ErrorCodeException> {
            replicaFailureRecordService.deleteByConditions(request)
        }
    }

    @Test
    fun `test deleteByConditions - should throw exception when empty ids list`() {
        val request = ReplicaFailureRecordDeleteRequest(
            ids = emptyList(),
            maxRetryCount = null
        )

        assertThrows<ErrorCodeException> {
            replicaFailureRecordService.deleteByConditions(request)
        }
    }

    // ========== retryRecord 测试 ==========

    @Test
    fun `test retryRecord - should return true when retry succeeds`() {
        val record = createTestRecord(id = "record-id")
        replicaFailureRecordDao.save(record)

        whenever(replicaRetryService.retryFailureRecord(any())).thenReturn(true)

        val request = ReplicaFailureRecordRetryRequest(id = "record-id")
        val result = replicaFailureRecordService.retryRecord(request)

        assertTrue(result)
        verify(replicaRetryService, times(1)).retryFailureRecord(any())
    }

    @Test
    fun `test retryRecord - should return false when retry fails`() {
        val record = createTestRecord(id = "record-id")
        replicaFailureRecordDao.save(record)

        whenever(replicaRetryService.retryFailureRecord(any())).thenReturn(false)

        val request = ReplicaFailureRecordRetryRequest(id = "record-id")
        val result = replicaFailureRecordService.retryRecord(request)

        assertFalse(result)
        verify(replicaRetryService, times(1)).retryFailureRecord(any())
    }

    @Test
    fun `test retryRecord - should throw exception when record not found`() {
        val request = ReplicaFailureRecordRetryRequest(id = "non-existent-id")

        assertThrows<ErrorCodeException> {
            replicaFailureRecordService.retryRecord(request)
        }
    }

    @Test
    fun `test retryRecord - should handle exception during retry`() {
        val record = createTestRecord(id = "record-id")
        replicaFailureRecordDao.save(record)

        whenever(replicaRetryService.retryFailureRecord(any())).thenThrow(RuntimeException("Retry error"))

        val request = ReplicaFailureRecordRetryRequest(id = "record-id")
        val result = replicaFailureRecordService.retryRecord(request)

        assertFalse(result)
        verify(replicaRetryService, times(1)).retryFailureRecord(any())
    }

    // ========== 辅助方法 ==========

    private fun createTestRecord(
        id: String? = null,
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
            id = id,
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

