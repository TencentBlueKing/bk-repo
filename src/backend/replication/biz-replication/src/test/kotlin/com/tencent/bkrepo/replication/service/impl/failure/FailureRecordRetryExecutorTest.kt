package com.tencent.bkrepo.replication.service.impl.failure

import com.tencent.bkrepo.replication.model.TReplicaFailureRecord
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.service.ReplicaRetryService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("失败记录重试执行器测试")
class FailureRecordRetryExecutorTest {

    private val replicaRetryService: ReplicaRetryService = mock()
    private val failureRecordRepository: FailureRecordRepository = mock()

    private lateinit var failureRecordRetryExecutor: FailureRecordRetryExecutor
    private val testTaskKey = "test-task-key"
    private val testRemoteClusterId = "test-remote-cluster-id"
    private val testProjectId = "test-project-id"
    private val testLocalRepoName = "test-local-repo"
    private val testRemoteProjectId = "test-remote-project-id"
    private val testRemoteRepoName = "test-remote-repo"

    @BeforeEach
    fun setUp() {
        failureRecordRetryExecutor = FailureRecordRetryExecutor(
            replicaRetryService = replicaRetryService,
            failureRecordRepository = failureRecordRepository
        )
    }

    @Test
    fun `test execute - should return true and delete record when retry succeeds`() {
        val record = createTestRecord(id = "record-id")

        whenever(replicaRetryService.retryFailureRecord(any())).thenReturn(true)

        val result = failureRecordRetryExecutor.execute(record)

        assertTrue(result)
        verify(replicaRetryService, times(1)).retryFailureRecord(record)
        verify(failureRecordRepository, times(1)).deleteById("record-id")
    }

    @Test
    fun `test execute - should return false when retry fails`() {
        val record = createTestRecord(id = "record-id")

        whenever(replicaRetryService.retryFailureRecord(any())).thenReturn(false)

        val result = failureRecordRetryExecutor.execute(record)

        assertFalse(result)
        verify(replicaRetryService, times(1)).retryFailureRecord(record)
        verify(failureRecordRepository, never()).deleteById(any())
    }

    @Test
    fun `test execute - should throw exception when retry service throws exception`() {
        val record = createTestRecord(id = "record-id")
        val exception = RuntimeException("Retry service error")

        whenever(replicaRetryService.retryFailureRecord(any())).thenThrow(exception)

        assertThrows<RuntimeException> {
            failureRecordRetryExecutor.execute(record)
        }

        verify(replicaRetryService, times(1)).retryFailureRecord(record)
        verify(failureRecordRepository, never()).deleteById(any())
    }

    @Test
    fun `test execute - should handle null record id gracefully`() {
        val record = createTestRecord(id = null)

        whenever(replicaRetryService.retryFailureRecord(any())).thenReturn(true)

        // 应该抛出异常，因为 deleteById 需要 id
        assertThrows<NullPointerException> {
            failureRecordRetryExecutor.execute(record)
        }
    }

    @Test
    fun `test execute - should not delete record when retry returns false`() {
        val record = createTestRecord(id = "record-id")

        whenever(replicaRetryService.retryFailureRecord(any())).thenReturn(false)

        val result = failureRecordRetryExecutor.execute(record)

        assertFalse(result)
        verify(failureRecordRepository, never()).deleteById(eq("record-id"))
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

