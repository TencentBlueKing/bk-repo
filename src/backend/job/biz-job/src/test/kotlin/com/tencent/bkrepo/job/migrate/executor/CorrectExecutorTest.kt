/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.CORRECT_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATE_FINISHED
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.createNode
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.removeNodes
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

@DisplayName("数据矫正执行器测试")
class CorrectExecutorTest @Autowired constructor(
    private val executor: CorrectExecutor,
) : ExecutorBaseTest() {
    @AfterAll
    fun afterAll() {
        executor.close(1L, TimeUnit.MINUTES)
    }

    @BeforeEach
    fun beforeEach() {
        initMock()
        migrateRepoStorageTaskDao.remove(Query())
        mongoTemplate.removeNodes()
    }

    @Test
    fun correctSuccess() {
        val now = LocalDateTime.now()
        // 创建待迁移node
        mongoTemplate.createNode(createDate = now.minusMinutes(1L))
        mongoTemplate.createNode(createDate = now)
        mongoTemplate.createNode(createDate = now, archived = true)
        mongoTemplate.createNode(createDate = now.plusMinutes(1L))
        // 创建任务
        var task = createTask()
        updateTask(task.id!!, MIGRATE_FINISHED.name, now)
        val context = executor.execute(buildContext(migrateTaskService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!))!!
        // 执行任务
        Thread.sleep(100L)
        assertTrue(executingTaskRecorder.executing(task.id!!))

        // 等待执行结束
        Thread.sleep(1000L)
        context.waitAllTransferFinished()
        task = migrateTaskService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(CORRECT_FINISHED.name, task.state)
        assertFalse(executingTaskRecorder.executing(task.id!!))
    }

    @Test
    fun testSaveMigrateFailedNode() {
        // mock
        whenever(storageService.copy(anyString(), anyOrNull(), anyOrNull())).then {
            throw FileNotFoundException()
        }
        whenever(fileReferenceService.count(anyString(), anyOrNull())).thenReturn(1L)
        // 创建node用于模拟遍历迁移
        val now = LocalDateTime.now()
        mongoTemplate.createNode(createDate = now.plusMinutes(1L))

        // 执行任务
        val task = createTask()
        updateTask(task.id!!, MIGRATE_FINISHED.name, now)
        val context = executor.execute(buildContext(migrateTaskService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!))!!

        // 等待任务执行完
        Thread.sleep(1000L)
        context.waitAllTransferFinished()
        assertTrue(migrateFailedNodeDao.existsFailedNode(UT_PROJECT_ID, UT_REPO_NAME))
    }
}
