package com.tencent.bkrepo.common.metadata.service.separation.impl

import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationFailedRecordDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationTaskDao
import com.tencent.bkrepo.common.metadata.model.TSeparationTask
import com.tencent.bkrepo.common.metadata.pojo.separation.NodeFilterInfo
import com.tencent.bkrepo.common.metadata.pojo.separation.SeparationContent
import com.tencent.bkrepo.common.metadata.pojo.separation.task.SeparationTaskRequest
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Duration

/**
 * 降冷任务创建：SEPARATE/RESTORE、归档专用类型与仓库类型校验。
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SeparationTaskServiceImpl 降冷任务")
class SeparationTaskServiceImplColdDataTest {

    @Mock
    private lateinit var dataSeparationConfig: DataSeparationConfig

    @Mock
    private lateinit var repositoryService: RepositoryService

    @Mock
    private lateinit var separationTaskDao: SeparationTaskDao

    @Mock
    private lateinit var separationFailedRecordDao: SeparationFailedRecordDao

    @Mock
    private lateinit var mongoTemplate: MongoTemplate

    private lateinit var service: SeparationTaskServiceImpl

    @BeforeEach
    fun setup() {
        service = SeparationTaskServiceImpl(
            dataSeparationConfig,
            repositoryService,
            separationTaskDao,
            separationFailedRecordDao,
            mongoTemplate,
        )
        whenever(dataSeparationConfig.keepDays).thenReturn(Duration.ofDays(30))
        whenever(
            separationTaskDao.exist(
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(false)
    }

    @Test
    @DisplayName("SEPARATE_ARCHIVED 仅允许 GENERIC 仓库")
    fun createSeparationTask_separateArchived_rejectsNonGeneric() {
        whenever(repositoryService.getRepoDetail(eq("p"), eq("r"), anyOrNull())).thenReturn(repo(RepositoryType.MAVEN))
        whenever(dataSeparationConfig.specialSeparateRepos).thenReturn(mutableListOf("p/r"))

        val req = SeparationTaskRequest(
            projectId = "p",
            repoName = "r",
            type = SeparationTaskServiceImpl.SEPARATE_ARCHIVED,
            separateAt = LEGAL_OLD_DATE,
            content = SeparationContent(),
        )
        assertThrows(BadRequestException::class.java) {
            service.createSeparationTask(req)
        }
    }

    @Test
    @DisplayName("SEPARATE_ARCHIVED + GENERIC 写入任务且 type 正确")
    fun createSeparationTask_separateArchived_savesTask() {
        whenever(repositoryService.getRepoDetail(eq("p"), eq("r"), anyOrNull())).thenReturn(repo(RepositoryType.GENERIC))
        whenever(dataSeparationConfig.specialSeparateRepos).thenReturn(mutableListOf("p/r"))

        val req = SeparationTaskRequest(
            projectId = "p",
            repoName = "r",
            type = SeparationTaskServiceImpl.SEPARATE_ARCHIVED,
            separateAt = LEGAL_OLD_DATE,
            content = SeparationContent(),
        )
        service.createSeparationTask(req)

        val captor = argumentCaptor<TSeparationTask>()
        verify(separationTaskDao).save(captor.capture())
        assertEquals(SeparationTaskServiceImpl.SEPARATE_ARCHIVED, captor.firstValue.type)
        assertEquals("p", captor.firstValue.projectId)
        assertEquals("r", captor.firstValue.repoName)
    }

    @Test
    @DisplayName("RESTORE_ARCHIVED + GENERIC + specialRestoreRepos 写入恢复任务")
    fun createSeparationTask_restoreArchived_savesTask() {
        whenever(repositoryService.getRepoDetail(eq("p"), eq("r"), anyOrNull())).thenReturn(repo(RepositoryType.GENERIC))
        whenever(dataSeparationConfig.specialRestoreRepos).thenReturn(mutableListOf("p/r"))

        val req = SeparationTaskRequest(
            projectId = "p",
            repoName = "r",
            type = SeparationTaskServiceImpl.RESTORE_ARCHIVED,
            separateAt = LEGAL_OLD_DATE,
            content = SeparationContent(
                paths = mutableListOf(NodeFilterInfo(path = "/a/")),
            ),
        )
        service.createSeparationTask(req)

        val captor = argumentCaptor<TSeparationTask>()
        verify(separationTaskDao).save(captor.capture())
        assertEquals(SeparationTaskServiceImpl.RESTORE_ARCHIVED, captor.firstValue.type)
    }

    @Test
    @DisplayName("RESTORE_ARCHIVED 在非 GENERIC 仓库上拒绝")
    fun createSeparationTask_restoreArchived_rejectsNonGeneric() {
        whenever(repositoryService.getRepoDetail(eq("p"), eq("r"), anyOrNull())).thenReturn(repo(RepositoryType.MAVEN))
        whenever(dataSeparationConfig.specialRestoreRepos).thenReturn(mutableListOf("p/r"))

        val req = SeparationTaskRequest(
            projectId = "p",
            repoName = "r",
            type = SeparationTaskServiceImpl.RESTORE_ARCHIVED,
            separateAt = LEGAL_OLD_DATE,
            content = SeparationContent(),
        )
        assertThrows(BadRequestException::class.java) {
            service.createSeparationTask(req)
        }
    }

    private fun repo(type: RepositoryType) = RepositoryDetail(
        projectId = "p",
        name = "r",
        type = type,
        category = RepositoryCategory.LOCAL,
        public = false,
        description = null,
        configuration = LocalConfiguration(),
        storageCredentials = null,
        oldCredentialsKey = null,
        createdBy = "",
        createdDate = "",
        lastModifiedBy = "",
        lastModifiedDate = "",
        quota = null,
        used = null,
    )

    companion object {
        /** 早于 keepDays 窗口，满足 validateSeparateTaskParams */
        private const val LEGAL_OLD_DATE = "2000-01-01T00:00:00"
    }
}
