package com.tencent.bkrepo.job.service.impl

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationNodeDao
import com.tencent.bkrepo.common.metadata.pojo.separation.task.SeparationTaskRequest
import com.tencent.bkrepo.common.metadata.service.separation.SeparationTaskService
import com.tencent.bkrepo.common.metadata.service.separation.impl.SeparationTaskServiceImpl
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.batch.task.archive.IdleNodeArchiveJob
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.pojo.ArchiveRestoreRequest
import com.tencent.bkrepo.job.service.MigrateArchivedFileService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime

@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ArchiveJobServiceImpl::class, NodeCommonUtils::class)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
@DisplayName("ArchiveJobServiceImpl 降冷恢复任务")
class ArchiveJobServiceImplColdRestoreTest @Autowired constructor(
    private val service: ArchiveJobServiceImpl,
    @Suppress("unused") mongoTemplate: MongoTemplate,
) {
    @MockitoBean
    private lateinit var archiveJob: IdleNodeArchiveJob

    @MockitoBean
    private lateinit var archiveClient: ArchiveClient

    @MockitoBean
    private lateinit var migrateRepoStorageService: MigrateRepoStorageService

    @MockitoBean
    private lateinit var migrateArchivedFileService: MigrateArchivedFileService

    @MockitoBean
    private lateinit var separationTaskService: SeparationTaskService

    @MockitoBean
    private lateinit var separationNodeDao: SeparationNodeDao

    @MockitoBean
    private lateinit var dataSeparationConfig: DataSeparationConfig

    @Test
    fun restore_createsColdRestoreTask_whenSeparationConfigured() {
        val d = LocalDateTime.of(2024, 1, 1, 0, 0)
        whenever(dataSeparationConfig.specialSeparateRepos).thenReturn(mutableListOf("$UT_PROJECT_ID/$UT_REPO_NAME"))
        whenever(separationTaskService.findDistinctSeparationDate(UT_PROJECT_ID, UT_REPO_NAME)).thenReturn(setOf(d))
        whenever(separationNodeDao.findByQuery(any(), eq(d))).thenReturn(
            listOf(
                mutableMapOf(
                    "repoName" to UT_REPO_NAME,
                    "fullPath" to "/cold/a.txt",
                    "archived" to "true",
                ),
            ),
        )
        service.restore(
            ArchiveRestoreRequest(projectId = UT_PROJECT_ID, repoName = UT_REPO_NAME, prefix = null),
        )
        val captor = argumentCaptor<SeparationTaskRequest>()
        verify(separationTaskService).createSeparationTask(captor.capture())
        assertEquals(SeparationTaskServiceImpl.RESTORE_ARCHIVED, captor.firstValue.type)
    }
}
