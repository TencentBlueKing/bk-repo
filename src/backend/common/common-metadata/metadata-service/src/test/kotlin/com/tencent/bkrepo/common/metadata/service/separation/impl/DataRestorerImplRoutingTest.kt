package com.tencent.bkrepo.common.metadata.service.separation.impl

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.metadata.config.DataSeparationConfig
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationFailedRecordDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationNodeDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationPackageDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationPackageVersionDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationTaskDao
import com.tencent.bkrepo.common.metadata.model.TSeparationTask
import com.tencent.bkrepo.common.metadata.pojo.separation.SeparationContent
import com.tencent.bkrepo.common.metadata.pojo.separation.record.SeparationContext
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("DataRestorerImpl 仓库类型路由")
class DataRestorerImplRoutingTest {

    @Mock
    private lateinit var mongoTemplate: MongoTemplate

    @Mock
    private lateinit var separationPackageVersionDao: SeparationPackageVersionDao

    @Mock
    private lateinit var separationPackageDao: SeparationPackageDao

    @Mock
    private lateinit var separationFailedRecordDao: SeparationFailedRecordDao

    @Mock
    private lateinit var separationNodeDao: SeparationNodeDao

    @Mock
    private lateinit var dataSeparationConfig: DataSeparationConfig

    @Mock
    private lateinit var separationTaskDao: SeparationTaskDao

    @Mock
    private lateinit var archiveClient: ArchiveClient

    private lateinit var impl: DataRestorerImpl

    @BeforeEach
    fun setup() {
        whenever(dataSeparationConfig.batchSize).thenReturn(1000)
        impl = DataRestorerImpl(
            mongoTemplate,
            separationPackageVersionDao,
            separationPackageDao,
            separationFailedRecordDao,
            separationNodeDao,
            dataSeparationConfig,
            separationTaskDao,
            archiveClient,
        )
    }

    @Test
    @DisplayName("GENERIC 仓库从冷节点表分页恢复")
    fun repoRestorer_generic_usesSeparationNodeDao() {
        whenever(separationNodeDao.find(any())).thenReturn(emptyList())
        impl.repoRestorer(ctx(RepositoryType.GENERIC))
        verify(separationNodeDao).find(any())
        verify(separationPackageDao, never()).find(any())
    }

    @Test
    @DisplayName("MAVEN 仓库从冷 package 分页恢复")
    fun repoRestorer_maven_usesSeparationPackageDao() {
        whenever(separationPackageDao.find(any())).thenReturn(emptyList())
        impl.repoRestorer(ctx(RepositoryType.MAVEN))
        verify(separationPackageDao).find(any())
        verify(separationNodeDao, never()).find(any())
    }

    private fun ctx(type: RepositoryType): SeparationContext {
        val task = TSeparationTask(
            id = "tid",
            projectId = "p",
            repoName = "r",
            createdBy = "u",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "u",
            lastModifiedDate = LocalDateTime.now(),
            separationDate = LocalDateTime.now(),
            content = SeparationContent(),
            type = SeparationTaskServiceImpl.RESTORE,
        )
        val repo = RepositoryDetail(
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
        return SeparationContext(task, repo)
    }
}
