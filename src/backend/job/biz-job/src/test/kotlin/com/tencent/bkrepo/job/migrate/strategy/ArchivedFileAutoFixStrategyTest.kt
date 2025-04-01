package com.tencent.bkrepo.job.migrate.strategy

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.job.UT_PROJECT_ID
import com.tencent.bkrepo.job.UT_REPO_NAME
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.createArchiveFile
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.createNode
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.insertFailedNode
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource

@DisplayName("归档文件迁移失败自动修复策略测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    ArchivedFileAutoFixStrategy::class,
    ArchiveFileDao::class,
    MigrateFailedNodeDao::class,
    RepositoryCommonUtils::class,
)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class ArchivedFileAutoFixStrategyTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val archiveFileDao: ArchiveFileDao,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val strategy: ArchivedFileAutoFixStrategy,
    private val repositoryCommonUtils: RepositoryCommonUtils,
) {

    private val srcStorageKey = "src_$UT_STORAGE_CREDENTIALS_KEY"
    private val dstStorageKey = "dst_$UT_STORAGE_CREDENTIALS_KEY"

    @MockBean
    private lateinit var repositoryService: RepositoryService

    @MockBean
    private lateinit var storageCredentialsService: StorageCredentialService

    @BeforeEach
    fun beforeEach() {
        whenever(repositoryService.getRepoDetail(anyString(), anyString(), anyOrNull())).thenReturn(
            RepositoryDetail(
                projectId = UT_PROJECT_ID,
                name = UT_REPO_NAME,
                storageCredentials = InnerCosCredentials(key = dstStorageKey),
                type = RepositoryType.GENERIC,
                category = RepositoryCategory.LOCAL,
                public = false,
                description = "",
                configuration = LocalConfiguration(),
                createdBy = "",
                createdDate = "",
                lastModifiedBy = "",
                lastModifiedDate = "",
                oldCredentialsKey = srcStorageKey,
                quota = 0,
                used = 0,
            )
        )
    }

    @Test
    fun test() {
        val node = mongoTemplate.createNode(archived = true)
        val failedNode = migrateFailedNodeDao.insertFailedNode(node.fullPath)

        // node未归档时，无法继续迁移
        val node2 = mongoTemplate.createNode(archived = false, fullPath = "notArchived")
        val failedNode2 = migrateFailedNodeDao.insertFailedNode(node2.fullPath)
        assertFalse(strategy.fix(failedNode2))

        // 源存储文件正在归档中,无法继续迁移
        cleanAndCreateArchiveFile(srcStorageKey, ArchiveStatus.ARCHIVING)
        assertFalse(strategy.fix(failedNode))

        // 目标存储文件正在归档中，无法继续迁移
        cleanAndCreateArchiveFile(dstStorageKey, ArchiveStatus.ARCHIVING)
        assertFalse(strategy.fix(failedNode))

        // 无归档文件，无法继续迁移
        archiveFileDao.remove(Query())
        assertFalse(strategy.fix(failedNode))

        // 源存储归档完成，可继续迁移
        cleanAndCreateArchiveFile(srcStorageKey, ArchiveStatus.COMPLETED)
        assertTrue(strategy.fix(failedNode))

        // 目标存储归档文件，可继续迁移
        cleanAndCreateArchiveFile(dstStorageKey, ArchiveStatus.COMPLETED)
        assertTrue(strategy.fix(failedNode))
    }

    private fun cleanAndCreateArchiveFile(storageKey: String, status: ArchiveStatus) {
        archiveFileDao.remove(Query())
        archiveFileDao.createArchiveFile(
            UT_SHA256, storageKey, "archive_$UT_STORAGE_CREDENTIALS_KEY", status
        )
    }
}
