package com.tencent.bkrepo.replication.service.impl.tracking

import com.tencent.bkrepo.replication.dao.FederationMetadataTrackingDao
import com.tencent.bkrepo.replication.model.TFederationMetadataTracking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@DisplayName("重试状态管理器测试")
@DataMongoTest
@Import(FederationMetadataTrackingDao::class)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class RetryStateManagerTest @Autowired constructor(
    private val federationMetadataTrackingDao: FederationMetadataTrackingDao
) {

    private lateinit var retryStateManager: RetryStateManager

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
        retryStateManager = RetryStateManager(federationMetadataTrackingDao)
    }

    // ========== setRetrying 测试 ==========

    @Test
    fun `test setRetrying - should set retrying to true without incrementing count`() {
        val record = createTestRecord(retrying = false, retryCount = 2)
        federationMetadataTrackingDao.insert(record)

        retryStateManager.setRetrying(record.id!!, true, incrementRetryCount = false)

        val updated = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.retrying)
        assertEquals(2, updated.retryCount) // 重试次数未增加
    }

    @Test
    fun `test setRetrying - should set retrying to true and increment count`() {
        val record = createTestRecord(retrying = false, retryCount = 2)
        federationMetadataTrackingDao.insert(record)

        retryStateManager.setRetrying(record.id!!, true, incrementRetryCount = true)

        val updated = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.retrying)
        assertEquals(3, updated.retryCount) // 重试次数增加
    }

    @Test
    fun `test setRetrying - should set retrying to false`() {
        val record = createTestRecord(retrying = true, retryCount = 2)
        federationMetadataTrackingDao.insert(record)

        retryStateManager.setRetrying(record.id!!, false, incrementRetryCount = false)

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
        retryStateManager.setRetrying(record.id!!, true, incrementRetryCount = false)

        val updated = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.lastModifiedDate.isAfter(originalDate))
    }

    // ========== updateRetryInfo 测试 ==========

    @Test
    fun `test updateRetryInfo - should update without failure reason`() {
        val record = createTestRecord(retryCount = 0)
        val savedRecord = federationMetadataTrackingDao.insert(record)

        retryStateManager.updateRetryInfo(savedRecord.id!!, null)

        val updated = federationMetadataTrackingDao.findById(savedRecord.id!!)
        assertNotNull(updated)
        assertEquals(0, updated!!.retryCount)
        assertNull(updated.failureReason)
        assertFalse(updated.retrying)
    }

    @Test
    fun `test updateRetryInfo - should update lastModifiedDate`() {
        val record = createTestRecord(retryCount = 0)
        federationMetadataTrackingDao.insert(record)
        val originalDate = record.lastModifiedDate

        Thread.sleep(10)
        retryStateManager.updateRetryInfo(record.id!!, "Failure reason")

        val updated = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(updated)
        assertTrue(updated!!.lastModifiedDate.isAfter(originalDate))
    }

    // ========== handleRetryFailure 测试 ==========

    @Test
    fun `test handleRetryFailure - should update failure reason`() {
        val record = createTestRecord(retryCount = 2)
        val savedRecord = federationMetadataTrackingDao.insert(record)

        retryStateManager.handleRetryFailure(savedRecord, "Connection timeout")

        val updated = federationMetadataTrackingDao.findById(savedRecord.id!!)
        assertNotNull(updated)
        assertEquals(2, updated!!.retryCount) // 重试次数应该增加
        assertEquals("Connection timeout", updated.failureReason)
    }

    @Test
    fun `test handleRetryFailure - should handle empty error message`() {
        val record = createTestRecord(retryCount = 1, failureReason = "Original reason")
        val savedRecord = federationMetadataTrackingDao.insert(record)

        retryStateManager.handleRetryFailure(savedRecord, "")

        val updated = federationMetadataTrackingDao.findById(savedRecord.id!!)
        assertNotNull(updated)
        assertEquals(1, updated!!.retryCount)
        assertEquals("", updated.failureReason)
    }

    // ========== executeWithRetryState 测试 ==========

    @Test
    fun `test executeWithRetryState - should set retrying to true before execution`() {
        val record = createTestRecord(retrying = false)
        federationMetadataTrackingDao.insert(record)

        var wasRetryingDuringExecution = false
        val result = retryStateManager.executeWithRetryState(record, incrementRetryCount = false) {
            val currentRecord = federationMetadataTrackingDao.findById(record.id!!)
            assertNotNull(currentRecord)
            wasRetryingDuringExecution = currentRecord!!.retrying
            true // 返回成功
        }

        assertTrue(wasRetryingDuringExecution)
        assertTrue(result)
        // 执行完成后状态应被重置为 false
        val finalRecord = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(finalRecord)
        assertFalse(finalRecord!!.retrying)
    }

    @Test
    fun `test executeWithRetryState - should reset retrying to false after success`() {
        val record = createTestRecord(retrying = false)
        federationMetadataTrackingDao.insert(record)

        val result = retryStateManager.executeWithRetryState(record, incrementRetryCount = false) {
            true
        }

        assertTrue(result)
        val finalRecord = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(finalRecord)
        assertFalse(finalRecord!!.retrying)
        assertEquals(0, finalRecord.retryCount) // 成功时不增加重试次数
    }

    @Test
    fun `test executeWithRetryState - should reset retrying to false after failure`() {
        val record = createTestRecord(retrying = false, retryCount = 1)
        val savedRecord = federationMetadataTrackingDao.insert(record)

        val result = retryStateManager.executeWithRetryState(savedRecord, incrementRetryCount = false) {
            false
        }

        assertFalse(result)
        val finalRecord = federationMetadataTrackingDao.findById(savedRecord.id!!)
        assertNotNull(finalRecord)
        assertFalse(finalRecord!!.retrying)
        assertEquals(1, finalRecord.retryCount) // incrementRetryCount=false时，失败也不增加
        assertNotNull(finalRecord.failureReason)
        assertEquals("File transfer failed", finalRecord.failureReason)
    }

    @Test
    fun `test executeWithRetryState - should increment retry count when incrementRetryCount is true`() {
        val record = createTestRecord(retrying = false, retryCount = 1)
        val savedRecord = federationMetadataTrackingDao.insert(record)

        val result = retryStateManager.executeWithRetryState(savedRecord, incrementRetryCount = true) {
            false
        }

        assertFalse(result)
        val finalRecord = federationMetadataTrackingDao.findById(savedRecord.id!!)
        assertNotNull(finalRecord)
        assertEquals(2, finalRecord!!.retryCount) // incrementRetryCount=true时，重试次数增加
    }

    @Test
    fun `test executeWithRetryState - should handle exception and reset retrying to false`() {
        val record = createTestRecord(retrying = false, retryCount = 1)
        val savedRecord = federationMetadataTrackingDao.insert(record)

        val exceptionMessage = "Test exception"
        retryStateManager.executeWithRetryState(savedRecord, incrementRetryCount = false) {
            throw RuntimeException(exceptionMessage)
        }

        val finalRecord = federationMetadataTrackingDao.findById(savedRecord.id!!)
        assertNotNull(finalRecord)
        assertFalse(finalRecord!!.retrying)
        assertEquals(1, finalRecord.retryCount)
        assertNotNull(finalRecord.failureReason)
        assertTrue(finalRecord.failureReason!!.contains("RuntimeException"))
        assertTrue(finalRecord.failureReason!!.contains(exceptionMessage))
    }

    @Test
    fun `test executeWithRetryState - should always reset retrying in finally block even on exception`() {
        val record = createTestRecord(retrying = false)
        federationMetadataTrackingDao.insert(record)

        try {
            retryStateManager.executeWithRetryState(record, incrementRetryCount = false) {
                throw IllegalStateException("State error")
            }
        } catch (e: Exception) {
            // 忽略异常，验证 finally 块是否执行
        }

        val finalRecord = federationMetadataTrackingDao.findById(record.id!!)
        assertNotNull(finalRecord)
        assertFalse(finalRecord!!.retrying) // 确保 finally 块执行，状态被重置
    }

    @Test
    fun `test executeWithRetryState - should handle multiple concurrent retries`() {
        val record1 = createTestRecord(taskKey = "task-1", retrying = false)
        val record2 = createTestRecord(taskKey = "task-2", retrying = false)
        federationMetadataTrackingDao.insert(record1)
        federationMetadataTrackingDao.insert(record2)

        val result1 = retryStateManager.executeWithRetryState(record1, incrementRetryCount = false) { true }
        val result2 = retryStateManager.executeWithRetryState(record2, incrementRetryCount = false) { false }

        assertTrue(result1)
        assertFalse(result2)

        val finalRecord1 = federationMetadataTrackingDao.findById(record1.id!!)
        val finalRecord2 = federationMetadataTrackingDao.findById(record2.id!!)
        assertNotNull(finalRecord1)
        assertNotNull(finalRecord2)
        assertFalse(finalRecord1!!.retrying)
        assertFalse(finalRecord2!!.retrying)
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
