package com.tencent.bkrepo.archive.core.compress

import com.tencent.bkrepo.archive.BaseTest
import com.tencent.bkrepo.archive.CompressStatus
import com.tencent.bkrepo.archive.core.TaskResult
import com.tencent.bkrepo.archive.model.TCompressFile
import com.tencent.bkrepo.archive.repository.CompressFileRepository
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.hash.sha256
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.bksync.file.BkSyncDeltaSource.Companion.toBkSyncDeltaSource
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.core.StorageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDateTime
import kotlin.concurrent.thread
import kotlin.random.Random

@DataMongoTest
@ImportAutoConfiguration(StorageAutoConfiguration::class, TaskExecutionAutoConfiguration::class)
@TestPropertySource(locations = ["classpath:compress.properties"])
class BDZipManagerTest @Autowired constructor(
    private val storageService: StorageService,
    private val bdZipManager: BDZipManager,
    private val compressFileRepository: CompressFileRepository,
) : BaseTest() {

    @MockBean
    lateinit var fileReferenceService: FileReferenceService

    private val timeout = Duration.ofSeconds(10)

    @BeforeEach
    fun beforeEach() {
        initMock()
    }

    @AfterEach
    fun afterEach() {
        compressFileRepository.deleteAll()
    }

    @Test
    fun compressTest() {
        with(createCompressFile()) {
            val cf = compressFileRepository.findBySha256AndStorageCredentialsKey(sha256, null)
            Assertions.assertNotNull(cf)
            Assertions.assertEquals(CompressStatus.COMPRESSED, cf!!.status)
            Assertions.assertTrue(cf.compressedSize != -1L)
            // 原文件还在，原文件的删除由单独job处理
            Assertions.assertTrue(storageService.exist(sha256, null))
            // 压缩文件存在
            Assertions.assertTrue(storageService.exist(sha256.plus(".bd"), null))
            storageService.load(sha256.plus(".bd"), Range.full(compressedSize), null)!!.use {
                Assertions.assertDoesNotThrow { it.toBkSyncDeltaSource(createTempFile()) }
            }
        }
    }

    @Test
    fun compressFailedTest() {
        var decrement = 0
        Mockito.`when`(fileReferenceService.decrement(ArgumentMatchers.anyString(), ArgumentMatchers.isNull())).then {
            println("decrement file reference")
            decrement++
            true
        }
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
        bdZipManager.apply(file).blockWithTimeout()
        val cf = compressFileRepository.findBySha256AndStorageCredentialsKey(artifactFile1.getFileSha256(), null)
        Assertions.assertNotNull(cf)
        Assertions.assertEquals(CompressStatus.COMPRESS_FAILED, cf!!.status)
        Assertions.assertEquals(1, decrement)
    }

    @Test
    fun uncompressTest() {
        val compressFile = createCompressFile()
        storageService.delete(compressFile.sha256, null)
        compressFile.status = CompressStatus.WAIT_TO_UNCOMPRESS
        compressFileRepository.save(compressFile)
        bdZipManager.apply(compressFile).blockWithTimeout()
        val cf = compressFileRepository.findBySha256AndStorageCredentialsKey(compressFile.sha256, null)
        Assertions.assertEquals(CompressStatus.UNCOMPRESSED, cf!!.status)
        with(cf) {
            Assertions.assertTrue(storageService.exist(sha256, null))
            Assertions.assertFalse(storageService.exist(sha256.plus(".bd"), null))
            val load = storageService.load(sha256, Range.full(uncompressedSize), null)
            Assertions.assertEquals(sha256, load!!.sha256())
        }
    }

    @Test
    fun uncompressFailedTest() {
        val compressFile = createCompressFile()
        storageService.delete(compressFile.sha256, null)
        compressFile.status = CompressStatus.WAIT_TO_UNCOMPRESS
        compressFileRepository.save(compressFile)
        compressFile.compressedSize = 1 // set error
        bdZipManager.apply(compressFile).blockWithTimeout()
        val cf = compressFileRepository.findBySha256AndStorageCredentialsKey(compressFile.sha256, null)
        Assertions.assertEquals(CompressStatus.UNCOMPRESS_FAILED, cf!!.status)
        with(cf) {
            Assertions.assertFalse(storageService.exist(sha256, null))
            Assertions.assertTrue(storageService.exist(sha256.plus(".bd"), null))
        }
    }

    @Test
    fun uncompressFailedByBaseFileErrorTest() {
        val compressFile = createCompressFile()
        storageService.delete(compressFile.sha256, null)
        compressFile.status = CompressStatus.WAIT_TO_UNCOMPRESS
        compressFileRepository.save(compressFile)
        val data = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        // 修改基文件内容
        storageService.delete(compressFile.baseSha256, null)
        storageService.store(compressFile.baseSha256, createTempArtifactFile(data), null)
        bdZipManager.apply(compressFile).blockWithTimeout()
        val cf = compressFileRepository.findBySha256AndStorageCredentialsKey(compressFile.sha256, null)
        Assertions.assertEquals(CompressStatus.UNCOMPRESS_FAILED, cf!!.status)
        with(cf) {
            Assertions.assertFalse(storageService.exist(sha256, null))
            Assertions.assertTrue(storageService.exist(sha256.plus(".bd"), null))
        }
    }

    @Test
    fun uncompressFailedByBdFileErrorTest() {
        val compressFile = createCompressFile()
        storageService.delete(compressFile.sha256, null)
        compressFile.status = CompressStatus.WAIT_TO_UNCOMPRESS
        compressFileRepository.save(compressFile)
        val data = Random.nextBytes(compressFile.compressedSize.toInt())
        // 修改压缩文件内容
        val bdFileName = compressFile.sha256.plus(".bd")
        storageService.delete(bdFileName, null)
        storageService.store(bdFileName, createTempArtifactFile(data), null)
        bdZipManager.apply(compressFile).blockWithTimeout()
        val cf = compressFileRepository.findBySha256AndStorageCredentialsKey(compressFile.sha256, null)
        Assertions.assertEquals(CompressStatus.UNCOMPRESS_FAILED, cf!!.status)
        with(cf) {
            Assertions.assertFalse(storageService.exist(sha256, null))
            Assertions.assertTrue(storageService.exist(sha256.plus(".bd"), null))
        }
    }

    @Test
    fun concurrentTest() {
        val data1 = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        val artifactFile1 = createTempArtifactFile(data1)
        val baseSha256 = artifactFile1.getFileSha256()
        storageService.store(baseSha256, artifactFile1, null)
        val fileList = mutableListOf<TCompressFile>()
        repeat(5) {
            val data2 = data1.copyOfRange(it + 1, data1.size)
            val artifactFile2 = createTempArtifactFile(data2)
            val file = TCompressFile(
                createdBy = "ut",
                createdDate = LocalDateTime.now(),
                lastModifiedBy = "ut",
                lastModifiedDate = LocalDateTime.now(),
                sha256 = artifactFile2.getFileSha256(),
                baseSha256 = baseSha256,
                uncompressedSize = artifactFile2.getSize(),
                storageCredentialsKey = null,
                status = CompressStatus.CREATED,
                chainLength = 1,

            )
            storageService.store(artifactFile2.getFileSha256(), artifactFile2, null)
            compressFileRepository.save(file)
            fileList.add(file)
        }
        // 并发压缩
        fileList.map { thread { bdZipManager.apply(it).blockWithTimeout() } }.forEach { it.join() }
        fileList.forEach {
            Assertions.assertEquals(CompressStatus.COMPRESSED, it.status)
        }
        // 并发解压
        fileList.map {
            it.status = CompressStatus.WAIT_TO_UNCOMPRESS
            compressFileRepository.save(it)
            thread { bdZipManager.apply(it).blockWithTimeout() }
        }.forEach { it.join() }
        fileList.forEach {
            Assertions.assertEquals(CompressStatus.UNCOMPRESSED, it.status)
        }
    }

    @Test
    fun cascadeUncompress() {
        val data1 = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        var artifactFile1 = createTempArtifactFile(data1)
        storageService.store(artifactFile1.getFileSha256(), artifactFile1, null)
        var needUncompress: TCompressFile? = null
        repeat(6) {
            val data2 = data1.copyOfRange(it + 1, data1.size)
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
            if (needUncompress == null) {
                needUncompress = file
            }
            compressFileRepository.save(file)
            storageService.store(artifactFile2.getFileSha256(), artifactFile2, null)
            bdZipManager.apply(file).blockWithTimeout()
            storageService.delete(artifactFile1.getFileSha256(), null)
            artifactFile1 = artifactFile2
        }
        Assertions.assertNotNull(needUncompress)
        needUncompress!!.status = CompressStatus.WAIT_TO_UNCOMPRESS
        compressFileRepository.save(needUncompress!!)
        val apply = bdZipManager.apply(needUncompress!!)
        Assertions.assertEquals(TaskResult.OK, apply.blockWithTimeout())
        val cf = compressFileRepository.findBySha256AndStorageCredentialsKey(needUncompress!!.sha256, null)
        Assertions.assertEquals(CompressStatus.UNCOMPRESSED, cf!!.status)
        with(cf) {
            Assertions.assertTrue(storageService.exist(sha256, null))
            Assertions.assertFalse(storageService.exist(sha256.plus(".bd"), null))
            val load = storageService.load(sha256, Range.full(uncompressedSize), null)
            Assertions.assertEquals(sha256, load!!.sha256())
        }
    }

    private fun createCompressFile(): TCompressFile {
        val file = createTempCompressFile()
        bdZipManager.apply(file).blockWithTimeout()
        Assertions.assertEquals(CompressStatus.COMPRESSED, file.status)
        return file
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

    private fun <T> Mono<T>.blockWithTimeout(): T? {
        return this.block(timeout)
    }
}
