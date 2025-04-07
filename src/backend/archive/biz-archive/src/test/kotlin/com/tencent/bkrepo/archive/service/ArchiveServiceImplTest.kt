package com.tencent.bkrepo.archive.service

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.BaseTest
import com.tencent.bkrepo.archive.UT_ARCHIVE_CREDENTIALS_KEY
import com.tencent.bkrepo.archive.UT_SHA256
import com.tencent.bkrepo.archive.UT_STORAGE_CREDENTIALS_KEY
import com.tencent.bkrepo.archive.UT_USER
import com.tencent.bkrepo.archive.constant.ArchiveStorageClass
import com.tencent.bkrepo.archive.core.archive.EmptyArchiver
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.archive.request.ArchiveFileRequest
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.core.StorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.mongodb.core.query.Query
import java.time.LocalDateTime

@DataMongoTest
@ImportAutoConfiguration(StorageAutoConfiguration::class, TaskExecutionAutoConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchiveServiceImplTest @Autowired constructor(
    private val archiveService: ArchiveService,
    private val archiveFileDao: ArchiveFileDao,
) : BaseTest() {

    @MockBean
    private lateinit var storageService: StorageService

    @BeforeAll
    fun beforeAll() {
        whenever(storageService.delete(anyString(), anyOrNull())).then { }
        whenever(storageService.restore(anyString(), any(), anyString(), anyOrNull())).then { }
    }

    @Test
    fun testDelete() {
        val storageKey1 = UT_STORAGE_CREDENTIALS_KEY + 1
        val storageKey2 = UT_STORAGE_CREDENTIALS_KEY + 2
        mockData(storageKey1)

        // 归档文件不存在时什么都不做
        archiveService.delete(ArchiveFileRequest(sha256 = UT_SHA256, storageCredentialsKey = storageKey2))
        assertEquals(1, archiveFileDao.count(Query()))

        // mock data
        mockData(storageKey2)
        assertEquals(2L, archiveFileDao.count(Query()))

        // 仅删除归档文件数据库记录，不删除数
        archiveService.delete(ArchiveFileRequest(sha256 = UT_SHA256, storageCredentialsKey = storageKey1))
        assertEquals(1L, archiveFileDao.count(Query()))
        assertNull(archiveFileDao.findByStorageKeyAndSha256(storageKey1, UT_SHA256))
        verify(storageService, times(0)).delete(anyString(), anyOrNull())

        // 删除归档文件与实际数据
        archiveService.delete(ArchiveFileRequest(sha256 = UT_SHA256, storageCredentialsKey = storageKey2))
        assertEquals(0L, archiveFileDao.count(Query()))
        verify(storageService, times(1)).delete(anyString(), anyOrNull())
    }

    private fun mockData(storageCredentialsKey: String = UT_STORAGE_CREDENTIALS_KEY) {
        val now = LocalDateTime.now()
        archiveFileDao.insert(
            TArchiveFile(
                id = null,
                createdBy = UT_USER,
                createdDate = now,
                lastModifiedBy = UT_USER,
                lastModifiedDate = now,
                sha256 = UT_SHA256,
                size = 1024,
                storageCredentialsKey = storageCredentialsKey,
                status = ArchiveStatus.COMPLETED,
                archiver = EmptyArchiver.NAME,
                archiveCredentialsKey = UT_ARCHIVE_CREDENTIALS_KEY,
                storageClass = ArchiveStorageClass.DEEP_ARCHIVE,
            )
        )
    }
}
