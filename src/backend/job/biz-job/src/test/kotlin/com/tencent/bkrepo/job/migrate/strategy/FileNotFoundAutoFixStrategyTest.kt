package com.tencent.bkrepo.job.migrate.strategy

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.job.batch.task.archive.NodeCompressedJob
import com.tencent.bkrepo.job.batch.utils.RepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.dao.ArchiveMigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.createNode
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.insertFailedNode
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.removeNodes
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource

@DisplayName("文件找不到错误自动修复策略测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    FileNotFoundAutoFixStrategy::class,
    StorageProperties::class,
    RepositoryCommonUtils::class,
    MigrateFailedNodeDao::class,
    ArchiveMigrateFailedNodeDao::class,
)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class FileNotFoundAutoFixStrategyTest @Autowired constructor(
    private val mongoTemplate: MongoTemplate,
    private val strategy: FileNotFoundAutoFixStrategy,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val archiveMigrateFailedNodeDao: ArchiveMigrateFailedNodeDao,
) {
    @MockBean
    private lateinit var storageCredentialsClient: StorageCredentialsClient

    @MockBean
    private lateinit var fileReferenceClient: FileReferenceClient

    @MockBean
    private lateinit var repositoryClient: RepositoryClient

    @MockBean
    private lateinit var storageService: StorageService

    @BeforeEach
    fun beforeEach() {
        whenever(storageCredentialsClient.list(anyOrNull()))
            .thenReturn(Response(0, data = listOf(FileSystemCredentials())))
        whenever(storageCredentialsClient.findByKey(anyString()))
            .thenReturn(Response(0, data = FileSystemCredentials()))
        whenever(fileReferenceClient.increment(anyString(), anyOrNull(), any()))
            .thenReturn(Response(0, data = true))
        whenever(repositoryClient.getRepoDetail(anyString(), anyString(), anyOrNull()))
            .thenReturn(Response(0, "", MigrateTestUtils.buildRepo()))
        whenever(storageService.exist(anyString(), anyOrNull())).thenReturn(false)
        migrateFailedNodeDao.remove(Query())
        archiveMigrateFailedNodeDao.remove(Query())
        mongoTemplate.removeNodes()
    }

    @Test
    fun testNodeExists() {
        whenever(storageService.exist(anyString(), anyOrNull())).thenReturn(true)
        val node = migrateFailedNodeDao.insertFailedNode()
        assertFalse(strategy.fix(node))
    }

    @Test
    fun testNodeCompressedOrArchived() {
        // node compressed or archived
        var node = mongoTemplate.createNode(archived = true, compressed = true)
        val failedNode = migrateFailedNodeDao.insertFailedNode(node.fullPath)
        assertFalse(strategy.fix(failedNode))

        // exists compressed or archived record
        mongoTemplate.removeNodes()
        node = mongoTemplate.createNode()
        mongoTemplate.insert(
            NodeCompressedJob.CompressFile(
                id = null,
                sha256 = node.sha256,
                storageCredentialsKey = null,
                lastModifiedBy = "",
                uncompressedSize = node.size
            ),
            "compress_file"
        )
        assertFalse(strategy.fix(failedNode))
        mongoTemplate.remove(Query(), "compress_file")
    }

    @Test
    fun testCopyFromOtherStorage() {
        whenever(storageService.load(anyString(), any(), anyOrNull()))
            .thenReturn(ByteInputStream(ByteArray(1), 1).artifactStream(Range.full(1)))
        doNothing().whenever(storageService).copy(anyString(), anyOrNull(), anyOrNull())
        val node = mongoTemplate.createNode()
        val failedNode = migrateFailedNodeDao.insertFailedNode(node.fullPath)
        assertTrue(strategy.fix(failedNode))
        verify(fileReferenceClient, times(1)).increment(any(), anyOrNull(), any())
    }

    @Test
    fun testArchiveMigrateFailedNode() {
        whenever(storageService.load(anyString(), any(), anyOrNull())).thenReturn(null)
        val node = mongoTemplate.createNode()
        val failedNode = migrateFailedNodeDao.insertFailedNode(node.fullPath)
        assertTrue(strategy.fix(failedNode))
        assertEquals(0, migrateFailedNodeDao.count(Query()))
        assertEquals(1, archiveMigrateFailedNodeDao.count(Query()))
    }
}
