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
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.config.MigrateRepoStorageProperties
import com.tencent.bkrepo.job.migrate.dao.MigrateRepoStorageTaskDao
import com.tencent.bkrepo.job.migrate.pojo.CreateMigrateRepoStorageTaskRequest
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState
import com.tencent.bkrepo.job.migrate.utils.ExecutingTaskRecorder
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.buildRepo
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query

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
) {
    @MockBean
    private lateinit var repositoryClient: RepositoryClient

    @MockBean
    private lateinit var storageCredentialsClient: StorageCredentialsClient

    @BeforeAll
    fun beforeAll() {
        whenever(repositoryClient.getRepoDetail(anyString(), anyString(), anyString())).thenReturn(
            Response(0, "", buildRepo())
        )
    }

    @BeforeEach
    fun beforeEach() {
        migrateRepoStorageTaskDao.remove(Query())
    }

    @Test
    fun testCreateTask() {
        val request = CreateMigrateRepoStorageTaskRequest(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            dstCredentialsKey = null
        )
        // 不允许dst key与src key相同
        assertThrows<ErrorCodeException> { migrateRepoStorageService.createTask(request) }

        // 创建成功
        assertFalse(migrateRepoStorageService.migrating(UT_PROJECT_ID, UT_REPO_NAME))
        val task = migrateRepoStorageService.createTask(request.copy(dstCredentialsKey = UT_STORAGE_CREDENTIALS_KEY))
        assertEquals(MigrateRepoStorageTaskState.PENDING.name, task.state)
        assertTrue(migrateRepoStorageService.migrating(UT_PROJECT_ID, UT_REPO_NAME))
    }

    @Test
    fun testDuplicateTaskFailed() {
        val request = CreateMigrateRepoStorageTaskRequest(
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            dstCredentialsKey = UT_STORAGE_CREDENTIALS_KEY
        )
        migrateRepoStorageService.createTask(request)
        // 创建重复任务失败
        assertThrows<ErrorCodeException> { migrateRepoStorageService.createTask(request) }
    }
}
