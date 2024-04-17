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
import com.tencent.bkrepo.job.migrate.utils.ExecutingTaskRecorder
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.buildRepo
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

@DisplayName("Node遍历工具测试")
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
    private val migrateRepoStorageService: MigrateRepoStorageService
) {
    @MockBean
    private lateinit var repositoryClient: RepositoryClient

    @MockBean
    private lateinit var storageCredentialsClient: StorageCredentialsClient

    @Test
    fun testCreateTask() {
        whenever(repositoryClient.getRepoDetail(anyString(), anyString(), anyString())).thenReturn(
            Response(0, "", buildRepo())
        )
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
        assertTrue(migrateRepoStorageService.migrating(UT_PROJECT_ID, UT_REPO_NAME))

        // 创建重复任务失败
        assertThrows<ErrorCodeException> { migrateRepoStorageService.createTask(request) }
    }
}
