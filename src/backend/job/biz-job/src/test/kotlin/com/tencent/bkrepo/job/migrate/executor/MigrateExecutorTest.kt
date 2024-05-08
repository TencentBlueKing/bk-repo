package com.tencent.bkrepo.job.migrate.executor

import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.UT_USER
import com.tencent.bkrepo.job.migrate.model.TMigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask.Companion.toDto
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState
import com.tencent.bkrepo.job.model.TNode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Query
import java.io.FileNotFoundException
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@DisplayName("迁移执行器测试")
class MigrateExecutorTest @Autowired constructor(
    private val executor: MigrateExecutor,
) : ExecutorBaseTest() {

    @BeforeAll
    fun beforeAll() {
        migrateRepoStorageProperties.updateProgressInterval = 1
    }

    @AfterAll
    fun afterAll() {
        executor.close(1L, TimeUnit.MINUTES)
    }

    @BeforeEach
    fun beforeEach() {
        initMock()
        migrateRepoStorageTaskDao.remove(Query())
        removeNodes()
    }

    @Test
    fun migrateSuccess() {
        // 创建node用于模拟遍历迁移
        val nodeCount = 5L
        for (i in 0 until nodeCount) {
            createNode()
        }
        val context = executor.execute(buildContext(createTask()))!!

        // 确认任务执行中
        val task = migrateTaskService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(MigrateRepoStorageTaskState.MIGRATING.name, task.state)
        assertNotNull(task.startDate)

        // 等待任务执行结束
        Thread.sleep(500L)
        assertTrue(executingTaskRecorder.executing(task.id!!))
        context.waitAllTransferFinished()
        assertTaskFinished(task.id!!, nodeCount)
    }

    @Test
    fun testContinueMigrate() {
        // 创建node用于模拟遍历迁移
        val nodeCount = 50L
        val migratedCount = 23L
        val nodes = ArrayList<TNode>()
        for (i in 0 until nodeCount) {
            nodes.add(createNode())
        }
        // 创建任务
        val now = LocalDateTime.now()
        val task = migrateRepoStorageTaskDao.insert(
            TMigrateRepoStorageTask(
                id = null,
                createdBy = UT_USER,
                createdDate = now,
                lastModifiedBy = UT_USER,
                lastModifiedDate = now,
                startDate = now,
                totalCount = nodeCount,
                migratedCount = migratedCount,
                lastMigratedNodeId = nodes[migratedCount.toInt() - 1].id!!,
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                srcStorageKey = null,
                dstStorageKey = UT_STORAGE_CREDENTIALS_KEY,
                state = MigrateRepoStorageTaskState.PENDING.name,
            )
        ).toDto()
        // 执行任务
        val context = executor.execute(buildContext(task))!!
        Thread.sleep(10000L)
        context.waitAllTransferFinished()
        assertTaskFinished(task.id!!, nodeCount)
    }

    @Test
    fun testSaveMigrateFailedNode() {
        whenever(storageService.copy(anyString(), anyOrNull(), anyOrNull())).then {
            throw FileNotFoundException()
        }
        // 创建node用于模拟遍历迁移
        createNode()
        createNode(sha256 = FAKE_SHA256, fullPath = "/a/b/d.txt")
        createNode(compressed = true, fullPath = "/a/b/e.txt")
        val context = executor.execute(buildContext(createTask()))!!

        // 等待任务执行完
        Thread.sleep(1000L)
        context.waitAllTransferFinished()
        assertEquals(3, migrateFailedNodeDao.count(Query()))
    }

    private fun assertTaskFinished(taskId: String, totalCount: Long) {
        Thread.sleep(1000L)
        assertFalse(executingTaskRecorder.executing(taskId))
        val task = migrateRepoStorageTaskDao.findById(taskId)!!.toDto()
        assertEquals(totalCount, task.totalCount)
        assertEquals(totalCount, task.migratedCount)
        assertEquals(MigrateRepoStorageTaskState.MIGRATE_FINISHED.name, task.state)
    }
}
