package com.tencent.bkrepo.common.metadata.util

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.TooManyRequestsException
import com.tencent.bkrepo.common.metadata.config.RepositoryProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("节点删除并发控制测试")
class NodeDeleteHelperTest {

    @BeforeEach
    fun resetRunningCount() {
        // 重置共享并发计数，保证用例间隔离
        NodeDeleteHelper.runningDeleteCount.set(0)
    }

    @Test
    @DisplayName("并发删除超过上限时快速失败")
    fun `should reject when concurrent deleteNodes exceed limit`() {
        val concurrency = 1
        val firstAcquired = CountDownLatch(1)
        val release = CountDownLatch(1)

        // 第一个删除操作占用唯一名额并阻塞在 updateMulti 中，模拟长耗时删除
        val running = CompletableFuture.runAsync {
            deleteNodes(concurrency) { _, _ ->
                firstAcquired.countDown()
                release.await()
                0L
            }
        }

        try {
            // 确保第一个操作已占用名额
            assertTrue(firstAcquired.await(WAIT_SECONDS, TimeUnit.SECONDS))

            // 第二个操作应被拒绝
            assertThrows<TooManyRequestsException> {
                deleteNodes(concurrency) { _, _ -> 0L }
            }
        } finally {
            // 释放第一个操作，避免线程泄漏
            release.countDown()
            running.join()
        }
    }

    @Test
    @DisplayName("操作结束后释放名额，后续删除可正常执行")
    fun `should release permit after deleteNodes finished`() {
        val concurrency = 1
        // 串行重复执行多次，每次都能正常占用并释放名额
        repeat(3) {
            val deleted = deleteNodes(concurrency) { _, _ -> 1L }
            assertEquals(1L, deleted)
        }
    }

    @Test
    @DisplayName("concurrency 小于等于 0 时不限制并发")
    fun `should not limit when concurrency is not positive`() {
        val firstAcquired = CountDownLatch(1)
        val release = CountDownLatch(1)

        // 第一个操作阻塞执行，不限制并发时不应占用名额
        val running = CompletableFuture.runAsync {
            deleteNodes(concurrency = 0) { _, _ ->
                firstAcquired.countDown()
                release.await()
                0L
            }
        }

        try {
            assertTrue(firstAcquired.await(WAIT_SECONDS, TimeUnit.SECONDS))
            // 第二个操作即便与第一个重叠也不应被拒绝
            assertEquals(1L, deleteNodes(concurrency = 0) { _, _ -> 1L })
        } finally {
            release.countDown()
            running.join()
        }
    }

    @Test
    @DisplayName("节点数量超过 maxDeleteNodeCount 时拒绝删除")
    fun `should reject when node count exceeds maxDeleteNodeCount`() {
        assertThrows<ErrorCodeException> {
            deleteNodes(maxDeleteNodeCount = 100, countResult = 200) { _, _ -> 0L }
        }
    }

    @Test
    @DisplayName("节点数量未超过 maxDeleteNodeCount 时正常删除")
    fun `should allow delete when node count within maxDeleteNodeCount`() {
        val deleted = deleteNodes(maxDeleteNodeCount = 100, countResult = 50) { _, _ -> 50L }
        assertEquals(50L, deleted)
    }

    @Test
    @DisplayName("maxDeleteNodeCount 小于等于 0 时不限制删除数量")
    fun `should not limit when maxDeleteNodeCount is not positive`() {
        val deleted = deleteNodes(maxDeleteNodeCount = 0, countResult = Long.MAX_VALUE) { _, _ -> 1L }
        assertEquals(1L, deleted)
    }

    private inline fun deleteNodes(
        concurrency: Int = 0,
        maxDeleteNodeCount: Long = 0,
        countResult: Long = 0,
        crossinline updateMulti: (Query, Update) -> Long,
    ): Long {
        return NodeDeleteHelper.deleteNodes(
            query = Query(),
            deleteMode = RepositoryProperties.DELETE_MODE_UPDATE,
            batchSize = 100,
            concurrency = concurrency,
            maxDeleteNodeCount = maxDeleteNodeCount,
            operator = "ut",
            deleteTime = LocalDateTime.now(),
            findByQuery = { emptyList() },
            updateMulti = { q, u -> updateMulti(q, u) },
            countByQuery = { countResult }
        )
    }

    companion object {
        private const val WAIT_SECONDS = 5L
    }
}
