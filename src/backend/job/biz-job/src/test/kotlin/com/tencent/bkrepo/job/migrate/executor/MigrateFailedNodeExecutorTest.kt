/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.job.migrate.executor

import com.tencent.bkrepo.job.UT_MD5
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.migrate.model.TMigrateFailedNode
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.CORRECT_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATE_FAILED_NODE_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATING_FAILED_NODE
import com.tencent.bkrepo.job.migrate.strategy.MigrateFailedNodeAutoFixStrategy
import com.tencent.bkrepo.job.migrate.strategy.MigrateFailedNodeFixer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import java.io.FileNotFoundException
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@DisplayName("迁移失败Node执行器测试")
@Import(MigrateFailedNodeFixer::class)
class MigrateFailedNodeExecutorTest @Autowired constructor(
    private val executor: MigrateFailedNodeExecutor,
) : ExecutorBaseTest() {

    @MockBean
    private lateinit var migrateFailedNodeAutoFixStrategy: MigrateFailedNodeAutoFixStrategy

    @AfterAll
    fun afterAll() {
        executor.close(1L, TimeUnit.MINUTES)
    }

    @BeforeEach
    fun beforeEach() {
        initMock()
        whenever(migrateFailedNodeAutoFixStrategy.fix(any())).thenReturn(true)
        migrateRepoStorageTaskDao.remove(Query())
        migrateFailedNodeDao.remove(Query())
    }

    @Test
    fun testMigrateFailedNodeSuccess() {
        // 创建任务
        var task = createTask()
        updateTask(task.id!!, CORRECT_FINISHED.name, LocalDateTime.now())


        // 创建待迁移的失败节点
        createFailedNode(task.id!!)
        assertTrue(migrateFailedNodeDao.existsFailedNode(UT_PROJECT_ID, UT_REPO_NAME))

        // 执行任务
        val context = executor.execute(buildContext(migrateTaskService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!))!!

        // 任务执行中
        Thread.sleep(500L)
        task = migrateTaskService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(MIGRATING_FAILED_NODE.name, task.state)

        // 任务执行结束
        Thread.sleep(1000L)
        context.waitAllTransferFinished()
        task = migrateTaskService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(MIGRATE_FAILED_NODE_FINISHED.name, task.state)
        assertFalse(migrateFailedNodeDao.existsFailedNode(UT_PROJECT_ID, UT_REPO_NAME))
    }

    @Test
    fun testMigrateFailedNodeFailed() {
        whenever(storageService.copy(anyString(), anyOrNull(), anyOrNull())).then {
            throw FileNotFoundException()
        }

        // 创建任务
        var task = createTask()
        updateTask(task.id!!, CORRECT_FINISHED.name, LocalDateTime.now())
        createFailedNode(task.id!!)

        // 测试达到最大重试次数后自动修复成功将重试次数重置为0
        migrateFailedNodeDao.updateFirst(
            Query(Criteria.where(TMigrateFailedNode::taskId.name).isEqualTo(task.id!!)),
            Update.update(TMigrateFailedNode::retryTimes.name, 2)
        )
        assertEquals(2, migrateFailedNodeDao.findOne(Query())!!.retryTimes)

        // 执行任务
        val context = executor.execute(buildContext(migrateTaskService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!))!!

        // 等待任务执行结束
        Thread.sleep(3000L)
        context.waitAllTransferFinished()
        task = migrateTaskService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(MIGRATING_FAILED_NODE.name, task.state)
        assertTrue(migrateFailedNodeDao.existsFailedNode(UT_PROJECT_ID, UT_REPO_NAME))
        assertEquals(0, migrateFailedNodeDao.findOne(Query())!!.retryTimes)
    }

    private fun createFailedNode(taskId: String) {
        migrateFailedNodeDao.insert(
            TMigrateFailedNode(
                id = null,
                createdDate = LocalDateTime.now(),
                lastModifiedDate = LocalDateTime.now(),
                nodeId = "",
                taskId = taskId,
                projectId = UT_PROJECT_ID,
                repoName = UT_REPO_NAME,
                fullPath = "/a/b/c.txt",
                sha256 = UT_SHA256,
                md5 = UT_MD5,
                size = 100L,
                retryTimes = 0
            )
        )
    }
}
