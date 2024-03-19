package com.tencent.bkrepo.archive.job.archive

import com.tencent.bkrepo.archive.ArchiveStatus
import com.tencent.bkrepo.archive.BaseTest
import com.tencent.bkrepo.archive.config.ArchiveProperties
import com.tencent.bkrepo.archive.job.FileProvider
import com.tencent.bkrepo.archive.model.TArchiveFile
import com.tencent.bkrepo.archive.repository.ArchiveFileDao
import com.tencent.bkrepo.archive.repository.ArchiveFileRepository
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.innercos.response.PutObjectResponse
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import reactor.core.publisher.Mono
import java.io.File
import java.time.LocalDateTime
import kotlin.random.Random

@DataMongoTest
@ImportAutoConfiguration(StorageAutoConfiguration::class, TaskExecutionAutoConfiguration::class)
class ArchiveManagerTest @Autowired constructor(
    private val archiveManager: ArchiveManager,
    private val archiveFileRepository: ArchiveFileRepository,
    private val storageService: StorageService,
    private val archiveFileDao: ArchiveFileDao,
    private val archiveProperties: ArchiveProperties,
) : BaseTest() {
    private val cosClient = mockk<CosClient>()

    @MockBean
    lateinit var storageCredentialsClient: StorageCredentialsClient

    @MockBean
    lateinit var repositoryClient: RepositoryClient

    init {
        archiveManager.cosClient = cosClient
    }

    @BeforeEach
    fun beforeEach() {
        initMock()
        every { cosClient.putFileObject(any(), any(), any()) }.answers { PutObjectResponse("") }
    }

    @Test
    fun archiveTest() {
        var upload = 0
        every { cosClient.putFileObject(any(), any(), any()) }.answers {
            upload++
            PutObjectResponse("")
        }
        with(createArchiveFile()) {
            val af = archiveFileRepository.findBySha256AndStorageCredentialsKey(sha256, null)
            Assertions.assertNotNull(af)
            Assertions.assertEquals(ArchiveStatus.ARCHIVED, af!!.status)
            // 默认未进行压缩
            Assertions.assertEquals(size, af.compressedSize)
            Assertions.assertEquals(1, upload)
            // 原文件还在，原文件的删除由单独job处理
            Assertions.assertTrue(storageService.exist(sha256, null))
        }
    }

    @Test
    fun archiveFailedTest() {
        every { cosClient.putFileObject(any(), any(), any()) } throws Exception("ut error")
        val artifactFile = createTempArtifactFile(Random.nextBytes(Random.nextInt(1024, 1 shl 20)))
        storageService.store(artifactFile.getFileSha256(), artifactFile, null)
        val archiveFile = TArchiveFile(
            createdBy = "ut",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "ut",
            lastModifiedDate = LocalDateTime.now(),
            sha256 = artifactFile.getFileSha256(),
            storageCredentialsKey = null,
            size = artifactFile.getSize(),
            status = ArchiveStatus.CREATED,
            archiver = EmptyArchiver.NAME,
        )
        archiveFileRepository.save(archiveFile)
        archiveManager.apply(archiveFile).block()
        Assertions.assertEquals(ArchiveStatus.ARCHIVE_FAILED, archiveFile!!.status)
        // 默认未进行压缩
        Assertions.assertEquals(-1, archiveFile.compressedSize)
        // 原文件还在，原文件的删除由单独job处理
        Assertions.assertTrue(storageService.exist(archiveFile.sha256, null))
    }

    @Test
    fun restoreTest() {
        val data = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        val archiveManager = ArchiveManager(
            archiveProperties,
            createFileProvider(data),
            archiveFileDao,
            archiveFileRepository,
            storageService,
        )
        archiveManager.cosClient = cosClient
        every { cosClient.checkObjectRestore(any()) } returns true
        with(createArchiveFile(data)) {
            storageService.delete(sha256, null)
            this.status = ArchiveStatus.WAIT_TO_RESTORE
            archiveFileRepository.save(this)
            archiveManager.apply(this).block()
            val af = archiveFileRepository.findBySha256AndStorageCredentialsKey(sha256, null)
            Assertions.assertNotNull(af)
            Assertions.assertEquals(ArchiveStatus.RESTORED, af!!.status)
            Assertions.assertTrue(storageService.exist(sha256, null))
        }
    }

    @Test
    fun restoreFailedTest() {
        val data = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        val archiveManager = ArchiveManager(
            archiveProperties,
            createFileProvider(data.copyOfRange(1, data.size)),
            archiveFileDao,
            archiveFileRepository,
            storageService,
        )
        archiveManager.cosClient = cosClient
        every { cosClient.checkObjectRestore(any()) } returns true
        with(createArchiveFile(data)) {
            storageService.delete(sha256, null)
            this.status = ArchiveStatus.WAIT_TO_RESTORE
            archiveFileRepository.save(this)
            archiveManager.apply(this).block()
            val af = archiveFileRepository.findBySha256AndStorageCredentialsKey(sha256, null)
            Assertions.assertNotNull(af)
            Assertions.assertEquals(ArchiveStatus.RESTORE_FAILED, af!!.status)
            Assertions.assertFalse(storageService.exist(sha256, null))
        }
    }

    private fun createArchiveFile(data: ByteArray = Random.nextBytes(Random.nextInt(1024, 1 shl 20))): TArchiveFile {
        val artifactFile = createTempArtifactFile(data)
        storageService.store(artifactFile.getFileSha256(), artifactFile, null)
        val archiveFile = TArchiveFile(
            createdBy = "ut",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "ut",
            lastModifiedDate = LocalDateTime.now(),
            sha256 = artifactFile.getFileSha256(),
            storageCredentialsKey = null,
            size = artifactFile.getSize(),
            status = ArchiveStatus.CREATED,
            archiver = EmptyArchiver.NAME,
        )
        archiveFileRepository.save(archiveFile)
        archiveManager.apply(archiveFile).block()
        Assertions.assertEquals(ArchiveStatus.ARCHIVED, archiveFile.status)
        return archiveFile
    }

    private fun createTempArtifactFile(data: ByteArray): ArtifactFile {
        val tempFile = createTempFile()
        tempFile.writeBytes(data)
        return FileSystemArtifactFile(tempFile)
    }

    private fun createFileProvider(data: ByteArray): FileProvider {
        return object : FileProvider {
            override fun get(sha256: String, range: Range, storageCredentials: StorageCredentials): Mono<File> {
                return Mono.just(createTempArtifactFile(data).getFile()!!)
            }
        }
    }
}
