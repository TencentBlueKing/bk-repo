package com.tencent.bkrepo.job.batch.task.archive

import com.tencent.bkrepo.archive.api.ArchiveClient
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.service.log.OperateLogService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.UT_USER
import com.tencent.bkrepo.job.batch.JobBaseTest
import com.tencent.bkrepo.job.batch.context.NodeContext
import com.tencent.bkrepo.job.batch.utils.NodeCommonUtils
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.config.properties.ArchivedNodeCompleteJobProperties
import com.tencent.bkrepo.job.migrate.MigrateRepoStorageService
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.createNode
import io.mockk.every
import io.mockk.mockkObject
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("归档结束任务测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchivedNodeCompleteJobTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val nodeDao: NodeDao,
    private val job: ArchivedNodeCompleteJob,
    private val properties: ArchivedNodeCompleteJobProperties,
) : JobBaseTest() {

    @MockitoBean
    private lateinit var archiveClient: ArchiveClient

    @MockitoBean
    private lateinit var nodeService: NodeService

    @MockitoBean
    private lateinit var storageService: StorageService

    @MockitoBean
    lateinit var operateLogService: OperateLogService

    @MockitoBean
    lateinit var migrateRepoStorageService: MigrateRepoStorageService

    @MockitoBean
    lateinit var repositoryService: RepositoryService

    @MockitoBean
    lateinit var storageCredentialService: StorageCredentialService

    @BeforeAll
    fun beforeAll() {
        // mock
        val storageCredentials = FileSystemCredentials(key = UT_STORAGE_CREDENTIALS_KEY)
        NodeCommonUtils.mongoTemplate = mongoTemplate
        NodeCommonUtils.migrateRepoStorageService = migrateRepoStorageService
        mockkObject(RepositoryCommonUtils)
        every { RepositoryCommonUtils.getRepositoryDetail(any(), any(), anyNullable()) }
            .returns(MigrateTestUtils.buildRepo(storageCredentials = storageCredentials))
        every { RepositoryCommonUtils.getStorageCredentials(anyNullable()) }
            .returns(storageCredentials)
    }

    @Test
    fun testAccessInterval() {
        // mock data
        val now = LocalDateTime.now()
        properties.minAccessInterval = Duration.ofDays(3L)
        nodeDao.createNode(fullPath = "/a/b/c.txt", lastAccessDate = now.minusDays(2L))
        nodeDao.createNode(fullPath = "/a/b/d.txt", lastAccessDate = now.minusDays(8L))
        val context = NodeContext()
        val archiveFile = ArchivedNodeRestoreJob.ArchiveFile(
            id = "",
            sha256 = UT_SHA256,
            size = 1024,
            storageCredentialsKey = UT_STORAGE_CREDENTIALS_KEY,
            lastModifiedBy = UT_USER
        )

        // 存在最近被访问过的同sha256 node时不删除文件，仅标记最近无访问的node为已归档
        job.run(archiveFile, "", context)
        verify(storageService, times(0)).delete(anyString(), anyOrNull())
        verify(nodeService, times(1)).archiveNode(any())
        reset(storageService, nodeService)

        // 所有node均未被访问过时删除文件
        properties.minAccessInterval = Duration.ofDays(1L)
        job.run(archiveFile, "", context)
        verify(storageService, times(1)).delete(anyString(), anyOrNull())
        verify(nodeService, times(2)).archiveNode(any())
    }

}
