package com.tencent.bkrepo.replication.service.impl.failure

import com.tencent.bkrepo.replication.dao.ReplicaFailureRecordDao
import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@DisplayName("失败记录重试状态管理器测试")
class FailureRecordRetryStateManagerTest {

    private val replicaFailureRecordDao: ReplicaFailureRecordDao = mock()

    private lateinit var failureRecordRetryStateManager: FailureRecordRetryStateManager
    private val testTaskKey = "test-task-key"
    private val testRemoteClusterId = "test-remote-cluster-id"
    private val testProjectId = "test-project-id"
    private val testLocalRepoName = "test-local-repo"
    private val testRemoteProjectId = "test-remote-project-id"
    private val testRemoteRepoName = "test-remote-repo"

    @BeforeEach
    fun setUp() {
        failureRecordRetryStateManager = FailureRecordRetryStateManager(
            replicaFailureRecordDao = replicaFailureRecordDao
        )
    }

    @Test
    fun `test executeWithRetryState - should set retry status to true and reset to false on success`() {
        val record = createTestRecord(id = "record-id")
        var blockExecuted = false

        val result = failureRecordRetryStateManager.executeWithRetryState(record) {
            blockExecuted = true
            true // 返回成功
        }

        assertTrue(result)
        assertTrue(blockExecuted)
        // 应该先设置为 true，然后因为成功，记录被删除，不需要重置
        verify(replicaFailureRecordDao, times(1)).updateRetryStatus(eq("record-id"), eq(true))
        verify(replicaFailureRecordDao, never()).updateRetryStatus(eq("record-id"), eq(false))
        verify(replicaFailureRecordDao, never()).incrementRetryCount(any(), any())
    }

    @Test
    fun `test executeWithRetryState - should set retry status and increment count on failure`() {
        val record = createTestRecord(id = "record-id")
        var blockExecuted = false

        val result = failureRecordRetryStateManager.executeWithRetryState(record) {
            blockExecuted = true
            false // 返回失败
        }

        assertFalse(result)
        assertTrue(blockExecuted)
        // 应该先设置为 true，然后因为失败，重置为 false，并增加重试次数
        verify(replicaFailureRecordDao, times(1)).updateRetryStatus(eq("record-id"), eq(true))
        verify(replicaFailureRecordDao, times(1)).updateRetryStatus(eq("record-id"), eq(false))
        verify(replicaFailureRecordDao, times(1)).incrementRetryCount(eq("record-id"), eq("Retry failed"))
    }

    @Test
    fun `test executeWithRetryState - should handle exception and reset status`() {
        val record = createTestRecord(id = "record-id")
        var blockExecuted = false
        val exception = RuntimeException("Test exception")

        val result = failureRecordRetryStateManager.executeWithRetryState(record) {
            blockExecuted = true
            throw exception
        }

        assertFalse(result)
        assertTrue(blockExecuted)
        // 应该先设置为 true，然后因为异常，重置为 false，并增加重试次数
        verify(replicaFailureRecordDao, times(1)).updateRetryStatus(eq("record-id"), eq(true))
        verify(replicaFailureRecordDao, times(1)).updateRetryStatus(eq("record-id"), eq(false))
        verify(replicaFailureRecordDao, times(1)).incrementRetryCount(
            eq("record-id"),
            eq("RuntimeException: Test exception")
        )
    }

    @Test
    fun `test executeWithRetryState - should handle exception with null message`() {
        val record = createTestRecord(id = "record-id")
        val exception = RuntimeException()

        val result = failureRecordRetryStateManager.executeWithRetryState(record) {
            throw exception
        }

        assertFalse(result)
        verify(replicaFailureRecordDao, times(1)).incrementRetryCount(
            eq("record-id"),
            eq("RuntimeException: Unknown error")
        )
    }

    @Test
    fun `test executeWithRetryState - should not reset status when block succeeds`() {
        val record = createTestRecord(id = "record-id")

        val result = failureRecordRetryStateManager.executeWithRetryState(record) {
            true // 成功
        }

        assertTrue(result)
        // 成功时不应该重置状态（因为记录已被删除）
        verify(replicaFailureRecordDao, times(1)).updateRetryStatus(eq("record-id"), eq(true))
        verify(replicaFailureRecordDao, never()).updateRetryStatus(eq("record-id"), eq(false))
    }

    @Test
    fun `test executeWithRetryState - should reset status when block fails`() {
        val record = createTestRecord(id = "record-id")

        val result = failureRecordRetryStateManager.executeWithRetryState(record) {
            false // 失败
        }

        assertFalse(result)
        // 失败时应该重置状态
        verify(replicaFailureRecordDao, times(1)).updateRetryStatus(eq("record-id"), eq(true))
        verify(replicaFailureRecordDao, times(1)).updateRetryStatus(eq("record-id"), eq(false))
    }

    // ========== 辅助方法 ==========

    private fun createTestRecord(
        id: String? = "test-record-id",
        taskKey: String = testTaskKey,
        remoteClusterId: String = testRemoteClusterId,
        projectId: String = testProjectId,
        repoName: String = testLocalRepoName,
        remoteProjectId: String = testRemoteProjectId,
        remoteRepoName: String = testRemoteRepoName,
        failureType: ReplicaObjectType = ReplicaObjectType.PATH,
        retryCount: Int = 0,
        retrying: Boolean = false
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
            retryCount = retryCount,
            retrying = retrying
        )
    }
}

