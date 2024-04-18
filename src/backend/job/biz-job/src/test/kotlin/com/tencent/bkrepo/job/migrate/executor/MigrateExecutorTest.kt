package com.tencent.bkrepo.job.migrate.executor

import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState
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
        val context = buildContext(createTask())
        // 创建node用于模拟遍历迁移
        val nodeCount = 5L
        for (i in 0 until nodeCount) {
            createNode()
        }
        assertTrue(executor.execute(context))

        // 确认任务执行中
        var task = migrateTaskService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(MigrateRepoStorageTaskState.MIGRATING.name, task.state)
        assertNotNull(task.startDate)
        assertTrue(executingTaskRecorder.executing(task.id!!))

        // 等待任务执行结束
        Thread.sleep(2000L)
        context.waitAllTransferFinished()
        task = migrateTaskService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertFalse(executingTaskRecorder.executing(task.id!!))
        assertEquals(nodeCount, task.totalCount)
        assertEquals(nodeCount, task.migratedCount)
        assertEquals(MigrateRepoStorageTaskState.MIGRATE_FINISHED.name, task.state)
    }

    @Test
    fun testSaveMigrateFailedNode() {
        whenever(storageService.copy(anyString(), anyOrNull(), anyOrNull())).then {
            throw FileNotFoundException()
        }
        val context = buildContext(createTask())
        // 创建node用于模拟遍历迁移
        createNode()
        assertTrue(executor.execute(context))

        // 等待任务执行完
        Thread.sleep(1000L)
        context.waitAllTransferFinished()
        assertTrue(migrateFailedNodeDao.existsFailedNode(UT_PROJECT_ID, UT_REPO_NAME))
    }
}
