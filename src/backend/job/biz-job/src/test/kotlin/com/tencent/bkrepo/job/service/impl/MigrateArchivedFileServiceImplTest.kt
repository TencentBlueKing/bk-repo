package com.tencent.bkrepo.job.service.impl

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.job.UT_SHA256
import com.tencent.bkrepo.job.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.job.UT_USER
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@DisplayName("迁移归档文件测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ArchiveFileDao::class, MigrateArchivedFileServiceImpl::class)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class MigrateArchivedFileServiceImplTest @Autowired constructor(
    private val migrateArchivedFileService: MigrateArchivedFileServiceImpl,
    private val archiveFileDao: ArchiveFileDao,
) {
    @Test
    fun testMigrateArchivedFile() {
        val srcStorageKey = "src_$UT_STORAGE_CREDENTIALS_KEY"
        val dstStorageKey = UT_STORAGE_CREDENTIALS_KEY
        val archiveStorageKey = "archive_$UT_STORAGE_CREDENTIALS_KEY"

        // 归档文件不存在
        assertFalse(migrateArchivedFileService.migrateArchivedFile(srcStorageKey, dstStorageKey, UT_SHA256))

        // 原存储正在归档中时不允许迁移
        var archiveFile = mockArchiveFile(srcStorageKey, archiveStorageKey, ArchiveStatus.ARCHIVING)
        assertThrows<IllegalStateException> {
            migrateArchivedFileService.migrateArchivedFile(srcStorageKey, dstStorageKey, UT_SHA256)
        }
        archiveFileDao.removeById(archiveFile.id!!)

        // 目标存储正在归档中时不允许迁移
        archiveFile = mockArchiveFile(dstStorageKey, archiveStorageKey, ArchiveStatus.ARCHIVING)
        assertThrows<IllegalStateException> {
            migrateArchivedFileService.migrateArchivedFile(srcStorageKey, dstStorageKey, UT_SHA256)
        }
        archiveFileDao.removeById(archiveFile.id!!)

        // 目标存储已存在归档文件时直接复用
        archiveFile = mockArchiveFile(dstStorageKey, archiveStorageKey, ArchiveStatus.COMPLETED)
        assertTrue(migrateArchivedFileService.migrateArchivedFile(srcStorageKey, dstStorageKey, UT_SHA256))
        archiveFileDao.removeById(archiveFile.id!!)

        // 迁移归档文件
        archiveFile = mockArchiveFile(srcStorageKey, archiveStorageKey, ArchiveStatus.COMPLETED)
        assertNull(archiveFileDao.findByStorageKeyAndSha256(dstStorageKey, UT_SHA256))
        assertTrue(migrateArchivedFileService.migrateArchivedFile(srcStorageKey, dstStorageKey, UT_SHA256))
        assertNotNull(archiveFileDao.findByStorageKeyAndSha256(dstStorageKey, UT_SHA256))
        archiveFileDao.removeById(archiveFile.id!!)
    }

    @Test
    fun testArchivedFileCompleted() {
        // 归档文件不存在时返回false
        assertFalse(migrateArchivedFileService.archivedFileCompleted(UT_STORAGE_CREDENTIALS_KEY, UT_SHA256))

        // 正在归档中时抛出异常
        var archiveFile = mockArchiveFile(
            UT_STORAGE_CREDENTIALS_KEY, "archive_$UT_STORAGE_CREDENTIALS_KEY", ArchiveStatus.ARCHIVING
        )
        assertThrows<IllegalStateException> {
            migrateArchivedFileService.archivedFileCompleted(UT_STORAGE_CREDENTIALS_KEY, UT_SHA256)
        }
        archiveFileDao.removeById(archiveFile.id!!)

        // 归档完成时返回true
        archiveFile = mockArchiveFile(
            UT_STORAGE_CREDENTIALS_KEY, "archive_$UT_STORAGE_CREDENTIALS_KEY", ArchiveStatus.COMPLETED
        )
        assertTrue(migrateArchivedFileService.archivedFileCompleted(UT_STORAGE_CREDENTIALS_KEY, UT_SHA256))
        archiveFileDao.removeById(archiveFile.id!!)
    }

    private fun mockArchiveFile(storageKey: String?, archiveStorageKey: String?, status: ArchiveStatus): TArchiveFile {
        val now = LocalDateTime.now()
        val archiveFile = TArchiveFile(
            id = null,
            createdBy = UT_USER,
            createdDate = now,
            lastModifiedBy = UT_USER,
            lastModifiedDate = now,
            sha256 = UT_SHA256,
            size = 1024L,
            storageCredentialsKey = storageKey,
            status = status,
            archiver = "",
            storageClass = ArchiveStorageClass.DEEP_ARCHIVE,
            archiveCredentialsKey = archiveStorageKey
        )
        return archiveFileDao.insert(archiveFile)
    }
}
