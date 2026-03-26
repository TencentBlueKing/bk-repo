package com.tencent.bkrepo.common.metadata.service.separation.impl

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
import com.tencent.bkrepo.common.metadata.pojo.separation.query.NodeBaseInfo
import com.tencent.bkrepo.common.metadata.pojo.separation.query.PackageInfo
import com.tencent.bkrepo.common.metadata.pojo.separation.record.SeparationContext
import com.tencent.bkrepo.common.metadata.util.SeparationUtils
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@DisplayName("DataSeparatorImpl 仓库类型路由")
class DataSeparatorImplRoutingTest {

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

    private lateinit var impl: DataSeparatorImpl

    @BeforeEach
    fun setup() {
        whenever(dataSeparationConfig.batchSize).thenReturn(1000)
        impl = DataSeparatorImpl(
            mongoTemplate,
            separationPackageVersionDao,
            separationPackageDao,
            separationFailedRecordDao,
            separationNodeDao,
            dataSeparationConfig,
            separationTaskDao,
        )
    }

    @Test
    @DisplayName("GENERIC 仓库走节点热表 collection")
    fun repoSeparator_generic_queriesNodeCollection() {
        whenever(
            mongoTemplate.find(
                any<Query>(),
                eq(NodeBaseInfo::class.java),
                eq(SeparationUtils.getNodeCollectionName("p")),
            ),
        ).thenReturn(emptyList())
        val ctx = ctx(RepositoryType.GENERIC)
        impl.repoSeparator(ctx)
        verify(mongoTemplate).find(any<Query>(), eq(NodeBaseInfo::class.java), eq(SeparationUtils.getNodeCollectionName("p")))
        verify(mongoTemplate, never()).find(any<Query>(), eq(PackageInfo::class.java), eq(SeparationTaskServiceImpl.PACKAGE_COLLECTION_NAME))
    }

    @Test
    @DisplayName("非 GENERIC 仓库走 package 热表")
    fun repoSeparator_maven_queriesPackageCollection() {
        whenever(
            mongoTemplate.find(
                any<Query>(),
                eq(PackageInfo::class.java),
                eq(SeparationTaskServiceImpl.PACKAGE_COLLECTION_NAME),
            ),
        ).thenReturn(emptyList())
        val ctx = ctx(RepositoryType.MAVEN)
        impl.repoSeparator(ctx)
        verify(mongoTemplate).find(any<Query>(), eq(PackageInfo::class.java), eq(SeparationTaskServiceImpl.PACKAGE_COLLECTION_NAME))
        verify(mongoTemplate, never()).find(any<Query>(), eq(NodeBaseInfo::class.java), eq(SeparationUtils.getNodeCollectionName("p")))
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
            type = SeparationTaskServiceImpl.SEPARATE,
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
