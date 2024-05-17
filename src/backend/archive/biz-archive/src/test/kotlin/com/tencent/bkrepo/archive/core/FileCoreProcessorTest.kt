package com.tencent.bkrepo.archive.core

import com.tencent.bkrepo.archive.BaseTest
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.FileReferenceClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import kotlin.random.Random

@DataMongoTest
@ImportAutoConfiguration(StorageAutoConfiguration::class, TaskExecutionAutoConfiguration::class)
@TestPropertySource(locations = ["classpath:file-core-processor.properties"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileCoreProcessorTest @Autowired constructor(
    private val fileCoreProcessor: FileCoreProcessor,
    private val storageService: StorageService,
    private val compressFileRepository: CompressFileRepository,
) : BaseTest() {

    @MockBean
    lateinit var fileReferenceClient: FileReferenceClient

    @MockBean
    lateinit var storageCredentialsClient: StorageCredentialsClient

    @MockBean
    lateinit var repositoryClient: RepositoryClient

    @BeforeAll
    fun beforeAll() {
        initMock()
    }

    @AfterAll
    fun afterAll() {
        fileCoreProcessor.shutdown()
    }

    @Test
    fun pushTest() {
        val file = createTempCompressFile()
        fileCoreProcessor.listen(FileEntityEvent(file.sha256, file))
        Thread.sleep(1000)
        val cf = compressFileRepository.findBySha256AndStorageCredentialsKey(file.sha256, null)
        Assertions.assertNotNull(cf)
        Assertions.assertEquals(CompressStatus.COMPRESSED, cf!!.status)
    }

    @Test
    fun pushContinueWhenError() {
        val data1 = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        val data2 = data1.copyOfRange(Random.nextInt(1, 10), data1.size)
        val artifactFile1 = createTempArtifactFile(data1)
        val artifactFile2 = createTempArtifactFile(data2)
        val file = TCompressFile(
            createdBy = "ut",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "ut",
            lastModifiedDate = LocalDateTime.now(),
            sha256 = artifactFile1.getFileSha256(),
            baseSha256 = artifactFile2.getFileSha256(),
            uncompressedSize = 1, // set error
            storageCredentialsKey = null,
            status = CompressStatus.CREATED,
            chainLength = 1,
        )
        storageService.store(artifactFile1.getFileSha256(), artifactFile1, null)
        storageService.store(artifactFile2.getFileSha256(), artifactFile2, null)
        compressFileRepository.save(file)
        fileCoreProcessor.listen(FileEntityEvent(file.sha256, file))
        Thread.sleep(3000)
        val cf = compressFileRepository.findBySha256AndStorageCredentialsKey(file.sha256, null)
        Assertions.assertNotNull(cf)
        Assertions.assertEquals(CompressStatus.COMPRESS_FAILED, cf!!.status)
        pushTest()
    }

    private fun createTempCompressFile(): TCompressFile {
        val data1 = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        val data2 = data1.copyOfRange(Random.nextInt(1, 10), data1.size)

        val artifactFile1 = createTempArtifactFile(data1)
        val artifactFile2 = createTempArtifactFile(data2)
        val file = TCompressFile(
            createdBy = "ut",
            createdDate = LocalDateTime.now(),
            lastModifiedBy = "ut",
            lastModifiedDate = LocalDateTime.now(),
            sha256 = artifactFile1.getFileSha256(),
            baseSha256 = artifactFile2.getFileSha256(),
            uncompressedSize = artifactFile1.getSize(),
            storageCredentialsKey = null,
            status = CompressStatus.CREATED,
            chainLength = 1,
        )
        storageService.store(artifactFile1.getFileSha256(), artifactFile1, null)
        storageService.store(artifactFile2.getFileSha256(), artifactFile2, null)
        compressFileRepository.save(file)
        return file
    }

    private fun createTempArtifactFile(data: ByteArray): ArtifactFile {
        val tempFile = createTempFile()
        tempFile.writeBytes(data)
        return FileSystemArtifactFile(tempFile)
    }
}
