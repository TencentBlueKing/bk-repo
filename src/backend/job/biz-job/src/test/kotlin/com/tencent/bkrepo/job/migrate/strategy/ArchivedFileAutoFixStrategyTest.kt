package com.tencent.bkrepo.job.migrate.strategy

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.common.artifact.stream.EmptyInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.migrate.dao.MigrateFailedNodeDao
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.createArchiveFile
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.createNode
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.insertFailedNode
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.mockRepositoryCommonUtils
import com.tencent.bkrepo.job.migrate.utils.MigrateTestUtils.removeNodes
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
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

@DisplayName("归档文件迁移失败自动修复策略测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    ArchivedFileAutoFixStrategy::class,
    ArchiveFileDao::class,
    MigrateFailedNodeDao::class,
    NodeDao::class,
    StorageProperties::class,
)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class ArchivedFileAutoFixStrategyTest @Autowired constructor(
    private val archiveFileDao: ArchiveFileDao,
    private val migrateFailedNodeDao: MigrateFailedNodeDao,
    private val strategy: ArchivedFileAutoFixStrategy,
    private val nodeDao: NodeDao,
) {

    private val srcStorageKey = "src_$UT_STORAGE_CREDENTIALS_KEY"
    private val dstStorageKey = "dst_$UT_STORAGE_CREDENTIALS_KEY"

    @MockitoBean
    private lateinit var storageService: StorageService

    @BeforeEach
    fun beforeEach() {
        nodeDao.removeNodes()
        migrateFailedNodeDao.remove(Query())
        mockRepositoryCommonUtils(dstStorageKey, srcStorageKey)
    }

    @Test
    fun testFixNodeNotArchived() {
        val node = nodeDao.createNode(archived = false, fullPath = "notArchived")
        val failedNode = migrateFailedNodeDao.insertFailedNode(node.fullPath, node.id!!)

        // 无法判断node是否已归档
        cleanAndCreateArchiveFile(srcStorageKey, ArchiveStatus.ARCHIVING)
        createArchiveFile(dstStorageKey, ArchiveStatus.ARCHIVING)
        assertFalse(strategy.fix(failedNode))

        // 存储存在时不将node标记为已归档
        cleanAndCreateArchiveFile(srcStorageKey, ArchiveStatus.COMPLETED)
        createArchiveFile(dstStorageKey, ArchiveStatus.COMPLETED)
        whenever(storageService.exist(anyString(), anyOrNull())).thenReturn(true)
        assertFalse(strategy.fix(failedNode))

        // 存储不存在且存在归档完成记录时标记node为已归档
        whenever(storageService.exist(anyString(), anyOrNull())).thenReturn(false)
        assertTrue(strategy.fix(failedNode))
        assertTrue(nodeDao.findById(node.projectId, node.id!!)?.archived == true)
    }

    @Test
    fun testFixArchiveFailed() {
        val node = nodeDao.createNode(archived = false, fullPath = "notArchived")
        val failedNode = migrateFailedNodeDao.insertFailedNode(node.fullPath, node.id!!)

        // 非归档失败状态不处理
        cleanAndCreateArchiveFile(srcStorageKey, ArchiveStatus.ARCHIVING)
        createArchiveFile(dstStorageKey, ArchiveStatus.ARCHIVING)
        assertFalse(strategy.fix(failedNode))

        // 文件不存在时不处理
        whenever(storageService.load(anyString(), any(), anyOrNull())).thenReturn(null)
        assertFalse(strategy.fix(failedNode))

        // 文件存在时移除失败状态归档记录
        cleanAndCreateArchiveFile(srcStorageKey, ArchiveStatus.ARCHIVE_FAILED)
        createArchiveFile(dstStorageKey, ArchiveStatus.ARCHIVE_FAILED)
        val inputStream = EmptyInputStream.INSTANCE.artifactStream(Range.FULL_RANGE)
        whenever(storageService.load(anyString(), any(), anyOrNull())).thenReturn(inputStream)
        assertTrue(strategy.fix(failedNode))
        assertEquals(0L, archiveFileDao.count(Query()))
    }

    @Test
    fun testCheckArchiveCompleted() {
        val node = nodeDao.createNode(archived = true)
        val failedNode = migrateFailedNodeDao.insertFailedNode(node.fullPath, node.id!!)

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
        createArchiveFile(storageKey, status)
    }

    private fun createArchiveFile(storageKey: String, status: ArchiveStatus) {
        archiveFileDao.createArchiveFile(
            UT_SHA256, storageKey, "archive_$UT_STORAGE_CREDENTIALS_KEY", status
        )
    }
}
