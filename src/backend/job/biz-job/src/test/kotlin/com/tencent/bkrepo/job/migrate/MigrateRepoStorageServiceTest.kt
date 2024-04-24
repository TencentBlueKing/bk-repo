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

package com.tencent.bkrepo.job.migrate

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.service.actuator.ActuatorConfiguration
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.executor.TaskExecutor
import com.tencent.bkrepo.job.migrate.model.TMigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.CreateMigrateRepoStorageTaskRequest
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATE_FINISHED
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.MIGRATING
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState.PENDING
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.utils.ExecutingTaskRecorder
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.buildRepo
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.buildTask
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDateTime

@DisplayName("迁移服务测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    MigrateRepoStorageService::class,
    MigrateRepoStorageTaskDao::class,
    MigrateRepoStorageProperties::class,
    RepositoryCommonUtils::class,
    ExecutingTaskRecorder::class,
    StorageProperties::class,
)
class MigrateRepoStorageServiceTest @Autowired constructor(
    private val migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao,
    private val migrateRepoStorageService: MigrateRepoStorageService,
    private val executingTaskRecorder: ExecutingTaskRecorder,
    private val properties: MigrateRepoStorageProperties,
) {
    @Value(ActuatorConfiguration.SERVICE_INSTANCE_ID)
    private lateinit var instanceId: String

    @MockBean
    private lateinit var repositoryClient: RepositoryClient

    @MockBean
    private lateinit var storageCredentialsClient: StorageCredentialsClient

    @MockBean(name = "migrateExecutor")
    private lateinit var mockMigrateExecutor: TaskExecutor

    @MockBean(name = "correctExecutor")
    private lateinit var mockCorrectExecutor: TaskExecutor

    @BeforeEach
    fun beforeEach() {
        initMock()
        migrateRepoStorageTaskDao.remove(Query())
    }

    @Test
    fun testCreateTask() {
        val request = buildCreateRequest(null)
        // 不允许dst key与src key相同
        assertThrows<ErrorCodeException> { migrateRepoStorageService.createTask(request) }

        // 创建成功
        assertNull(migrateRepoStorageService.findTask(UT_PROJECT_ID, UT_REPO_NAME))
        val task = migrateRepoStorageService.createTask(request.copy(dstCredentialsKey = UT_STORAGE_CREDENTIALS_KEY))
        assertEquals(PENDING.name, task.state)
        assertNotNull(migrateRepoStorageService.findTask(UT_PROJECT_ID, UT_REPO_NAME))
    }

    @Test
    fun testDuplicateTaskFailed() {
        val request = buildCreateRequest()
        migrateRepoStorageService.createTask(request)
        // 创建重复任务失败
        assertThrows<ErrorCodeException> { migrateRepoStorageService.createTask(request) }
    }

    @Test
    fun testRollbackInterruptedTaskState() {
        migrateRepoStorageService.createTask(buildCreateRequest())
        var task = migrateRepoStorageService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!
        migrateRepoStorageTaskDao.updateState(task.id!!, task.state, MIGRATING.name, task.lastModifiedDate, instanceId)
        // 未超时
        migrateRepoStorageService.rollbackInterruptedTaskState()
        task = migrateRepoStorageService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(MIGRATING.name, task.state)

        // 修改任务时间使其超时
        val update = Update().set(
            TMigrateRepoStorageTask::lastModifiedDate.name, LocalDateTime.now().minus(properties.timeout)
        )
        migrateRepoStorageTaskDao.updateFirst(Query(Criteria.where(ID).isEqualTo(task.id!!)), update)

        // 正在执行时不回滚
        executingTaskRecorder.record(task.id!!)
        migrateRepoStorageService.rollbackInterruptedTaskState()
        task = migrateRepoStorageService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(MIGRATING.name, task.state)

        // 不在执行时回滚
        executingTaskRecorder.remove(task.id!!)
        migrateRepoStorageService.rollbackInterruptedTaskState()
        task = migrateRepoStorageService.findTask(UT_PROJECT_ID, UT_REPO_NAME)!!
        assertEquals(PENDING.name, task.state)
    }

    @Test
    fun testTryExecuteTask() {
        migrateRepoStorageService.createTask(buildCreateRequest())
        assertFalse(migrateRepoStorageService.migrating(UT_PROJECT_ID, UT_REPO_NAME))

        // 成功执行
        var task = migrateRepoStorageService.tryExecuteTask()
        assertNotNull(task)
        val taskId = task!!.id!!

        // 未达到migrate与correct时间间隔时不执行
        migrateRepoStorageTaskDao.updateFirst(
            Query(Criteria.where(ID).isEqualTo(taskId)),
            Update().set(TMigrateRepoStorageTask::state.name, MIGRATE_FINISHED.name)
                .set(TMigrateRepoStorageTask::startDate.name, LocalDateTime.now())
        )
        task = migrateRepoStorageService.tryExecuteTask()
        assertNull(task)
        assertTrue(migrateRepoStorageService.migrating(UT_PROJECT_ID, UT_REPO_NAME))

        // 达到时间间隔
        migrateRepoStorageTaskDao.updateFirst(
            Query(Criteria.where(ID).isEqualTo(taskId)),
            Update().set(TMigrateRepoStorageTask::startDate.name, LocalDateTime.now().minus(properties.correctInterval))
        )
        task = migrateRepoStorageService.tryExecuteTask()
        assertNotNull(task)
    }

    private fun buildCreateRequest(dstKey: String? = UT_STORAGE_CREDENTIALS_KEY) = CreateMigrateRepoStorageTaskRequest(
        projectId = UT_PROJECT_ID,
        repoName = UT_REPO_NAME,
        dstCredentialsKey = dstKey
    )

    private fun initMock() {
        whenever(repositoryClient.getRepoDetail(anyString(), anyString(), anyOrNull())).thenReturn(
            Response(0, "", buildRepo())
        )
        whenever(storageCredentialsClient.findByKey(anyString()))
            .thenReturn(Response(0, data = FileSystemCredentials()))
        whenever(mockMigrateExecutor.execute(any())).thenReturn(MigrationContext(buildTask(), null, null))
        whenever(mockCorrectExecutor.execute(any())).thenReturn(MigrationContext(buildTask(), null, null))
    }
}
