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

import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.model.TMigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.CreateMigrateRepoStorageTaskRequest
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrationContext
import com.tencent.bkrepo.job.migrate.utils.ExecutingTaskRecorder
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    TaskExecutionAutoConfiguration::class,
    MigrateRepoStorageProperties::class,
    RepositoryCommonUtils::class,
    StorageProperties::class,
)
@ComponentScan(basePackages = ["com.tencent.bkrepo.job.migrate"])
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
open class ExecutorBaseTest {
    @Autowired
    protected lateinit var migrateRepoStorageProperties: MigrateRepoStorageProperties

    @Autowired
    protected lateinit var migrateTaskService: MigrateRepoStorageService

    @Autowired
    protected lateinit var mongoTemplate: MongoTemplate

    @Autowired
    protected lateinit var migrateRepoStorageTaskDao: MigrateRepoStorageTaskDao

    @Autowired
    protected lateinit var migrateFailedNodeDao: MigrateFailedNodeDao

    @Autowired
    protected lateinit var executingTaskRecorder: ExecutingTaskRecorder

    @MockBean
    protected lateinit var fileReferenceService: FileReferenceService

    @MockBean
    protected lateinit var repositoryService: RepositoryService

    @MockBean
    protected lateinit var storageCredentialService: StorageCredentialService

    @MockBean
    protected lateinit var storageService: StorageService

    fun initMock() {
        whenever(fileReferenceService.increment(any(), anyOrNull(), any())).thenReturn(true)
        whenever(fileReferenceService.decrement(any(), anyOrNull())).thenReturn(true)
        whenever(fileReferenceService.count(anyString(), anyOrNull())).thenReturn(0)

        whenever(repositoryService.getRepoDetail(anyString(), anyString(), anyOrNull()))
            .thenReturn(MigrateTestUtils.buildRepo())
        whenever(repositoryService.updateStorageCredentialsKey(anyString(), anyString(), anyString())).then {  }
        whenever(repositoryService.unsetOldStorageCredentialsKey(anyString(), anyString())).then {  }
        whenever(storageCredentialService.findByKey(anyString()))
            .thenReturn(FileSystemCredentials())
        whenever(storageService.copy(anyString(), anyOrNull(), anyOrNull())).then {
            Thread.sleep(1000L)
        }
        whenever(storageService.exist(anyString(), anyOrNull())).thenReturn(false)
    }

    protected fun createTask(repoName: String = UT_REPO_NAME): MigrateRepoStorageTask {
        return migrateTaskService.createTask(
            CreateMigrateRepoStorageTaskRequest(UT_PROJECT_ID, repoName, UT_STORAGE_CREDENTIALS_KEY)
        )
    }

    protected fun updateTask(id: String, dstState: String, now: LocalDateTime) {
        val update = Update()
            .set(TMigrateRepoStorageTask::state.name, dstState)
            .set(TMigrateRepoStorageTask::startDate.name, now)
            .set(TMigrateRepoStorageTask::lastModifiedDate.name, now)
            .set(TMigrateRepoStorageTask::migratedCount.name, 1L)
            .set(TMigrateRepoStorageTask::totalCount.name, 1L)
        migrateRepoStorageTaskDao.updateFirst(Query(Criteria.where(ID).isEqualTo(id)), update)
    }

    protected fun buildContext(task: MigrateRepoStorageTask): MigrationContext = MigrationContext(
        task, null, FileSystemCredentials(key = UT_STORAGE_CREDENTIALS_KEY)
    )
}
