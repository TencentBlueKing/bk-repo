/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.storage.core.cache

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.hash.sha256
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.common.storage.filesystem.FileSystemStorage
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.random.Random
import org.springframework.util.StreamUtils

@ExtendWith(SpringExtension::class)
@ImportAutoConfiguration(StorageAutoConfiguration::class, TaskExecutionAutoConfiguration::class)
@TestPropertySource(locations = ["classpath:storage-cache-fs.properties"])
internal class CacheStorageServiceTest {

    @Autowired
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var fileStorage: FileStorage

    @Autowired
    private lateinit var fileLocator: FileLocator

    @Autowired
    private lateinit var storageProperties: StorageProperties

    private val cacheClient by lazy { FileSystemClient(storageProperties.filesystem.cache.path) }

    @BeforeEach
    fun beforeEach() {
        // before each
    }

    @AfterEach
    fun afterEach() {
        FileUtils.deleteDirectory(File(storageProperties.filesystem.cache.path))
        FileUtils.deleteDirectory(File(storageProperties.filesystem.upload.location))
        FileUtils.deleteDirectory(File(storageProperties.filesystem.path))
    }

    @Test
    fun `should create correct storage service`() {
        Assertions.assertTrue(storageService is CacheStorageService)
        Assertions.assertTrue(fileStorage is FileSystemStorage)
    }

    @Test
    fun `should save to cache when store`() {
        val size = 1024L
        val artifactFile = createTempArtifactFile(size)
        val sha256 = artifactFile.getFileSha256()
        val path = fileLocator.locate(sha256)
        storageService.store(sha256, artifactFile, null)

        // wait to async store
        Thread.sleep(500)

        // check persist
        Assertions.assertTrue(storageService.exist(sha256, null))

        // check cache
        Assertions.assertTrue(cacheClient.exist(path, sha256))

        // should load from cache
        val artifactInputStream = storageService.load(sha256, Range.full(size), null)
        Assertions.assertNotNull(artifactInputStream)
        Assertions.assertEquals(artifactInputStream!!.sha256(), sha256)
    }

    @Test
    fun `should save to cache when load`() {
        val size = 10240L
        val artifactFile = createTempArtifactFile(size)
        val sha256 = artifactFile.getFileSha256()
        val path = fileLocator.locate(sha256)
        storageService.store(sha256, artifactFile, null)

        // wait to async store
        Thread.sleep(500)

        // check persist
        Assertions.assertTrue(storageService.exist(sha256, null))

        // check cache
        Assertions.assertTrue(cacheClient.exist(path, sha256))

        // remove cache
        cacheClient.delete(path, sha256)
        Assertions.assertFalse(cacheClient.exist(path, sha256))

        // should load from persist
        val artifactInputStream = storageService.load(sha256, Range.full(size.toLong()), null)
        Assertions.assertNotNull(artifactInputStream)
        Assertions.assertEquals(artifactInputStream!!.sha256(), sha256)

        // check cache
        Assertions.assertTrue(cacheClient.exist(path, sha256))
        Assertions.assertEquals(sha256, cacheClient.load(path, sha256)?.sha256())
    }

    @Test
    fun `should cache once when loading concurrently`() {
        val size = 1024L
        val artifactFile = createTempArtifactFile(size)
        val sha256 = artifactFile.getFileSha256()
        val path = fileLocator.locate(sha256)
        storageService.store(sha256, artifactFile, null)

        // wait to async store
        Thread.sleep(500)

        // check cache
        Assertions.assertTrue(cacheClient.exist(path, sha256))

        // check persist
        Assertions.assertTrue(storageService.exist(sha256, null))

        // remove cache
        cacheClient.delete(path, sha256)
        Assertions.assertFalse(cacheClient.exist(path, sha256))

        val count = 10
        val cyclicBarrier = CyclicBarrier(count)
        val threadList = mutableListOf<Thread>()
        repeat(count) {
            val thread = thread {
                cyclicBarrier.await()
                val artifactInputStream = storageService.load(sha256, Range.full(size), null)
                Assertions.assertEquals(artifactInputStream!!.sha256(), sha256)
            }
            threadList.add(thread)
        }
        threadList.forEach { it.join() }
        // check cache
        Assertions.assertTrue(cacheClient.exist(path, sha256))
        Assertions.assertEquals(sha256, cacheClient.load(path, sha256)?.sha256())
    }

    @Test
    fun `should not cache when loading partial content`() {
        val size = 10240L
        val rangeSize = 10
        val artifactFile = createTempArtifactFile(size)
        val buffer = ByteArray(rangeSize)
        artifactFile.getInputStream().use { it.read(buffer, 0, rangeSize) }
        val partialSha256 = buffer.toString(Charset.defaultCharset()).sha256()
        val sha256 = artifactFile.getFileSha256()
        val path = fileLocator.locate(sha256)
        storageService.store(sha256, artifactFile, null)
        // wait to async store
        Thread.sleep(500)
        // remove cache
        cacheClient.delete(path, sha256)

        val count = 10
        val cyclicBarrier = CyclicBarrier(count)
        val threadList = mutableListOf<Thread>()
        repeat(count) {
            val thread = thread {
                cyclicBarrier.await()
                val range = Range(0, rangeSize.toLong() - 1, size)
                val artifactInputStream = storageService.load(sha256, range, null)
                Assertions.assertEquals(artifactInputStream!!.sha256(), partialSha256)
            }
            threadList.add(thread)
        }
        threadList.forEach { it.join() }

        // check cache
        Assertions.assertFalse(cacheClient.exist(path, sha256))
    }

    @Test
    fun cancelStoreTest() {
        val size = 1024L
        val artifactFile = createTempArtifactFile(size)
        val sha256 = artifactFile.getFileSha256()
        val cancel = AtomicBoolean(false)
        cancel.set(true)
        assertDoesNotThrow { storageService.store(sha256, artifactFile, null, cancel) }
    }

    @Test
    fun deltaStoreTest() {
        val data1 = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        val data2 = data1.copyOfRange(Random.nextInt(1, 10), data1.size)
        val artifactFile1 = createTempArtifactFile(data1)
        val artifactFile2 = createTempArtifactFile(data2)
        val originFileSize = artifactFile1.getSize()
        try {
            val digest1 = artifactFile1.getFileSha256()
            val digest2 = artifactFile2.getFileSha256()
            println(artifactFile1.getFileMd5())
            storageService.store(digest1, artifactFile1, null)
            storageService.store(digest2, artifactFile2, null)

            // 增量存储
            val compressedSize = storageService.compress(
                digest1,
                data1.size.toLong(),
                digest2,
                data2.size.toLong(),
                null,
            )
            // 压缩后，源文件已经不在
            Assertions.assertNull(storageService.load(digest1, Range.full(originFileSize), null))

            // 确定压缩后，实际存储变小
            val compressFileName = digest1.plus(".bd")
            val compressFilepath = fileLocator.locate(compressFileName)
            fileStorage.load(
                compressFilepath,
                compressFileName,
                Range.FULL_RANGE,
                storageProperties.defaultStorageCredentials(),
            ).use {
                val compressedFileSize = StreamUtils.drain(it!!).toLong()
                Assertions.assertEquals(compressedSize, compressedFileSize)
                Assertions.assertTrue(compressedFileSize < originFileSize)
            }

            // 恢复文件
            val restored = storageService.uncompress(
                digest1,
                compressedSize,
                digest2,
                data2.size.toLong(),
                null,
            )
            Assertions.assertEquals(1, restored)
            // 恢复后，数据不变
            val newLoad = storageService.load(digest1, Range.full(originFileSize), null)
                ?.use { it.readBytes() }
            Assertions.assertArrayEquals(data1, newLoad)
        } finally {
            artifactFile1.delete()
            artifactFile2.delete()
        }
    }

    @Test
    fun cascadeDeltaStore() {
        val data1 = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        val data2 = data1.copyOfRange(Random.nextInt(1, 10), data1.size)
        val data3 = data1.copyOfRange(Random.nextInt(11, 100), Random.nextInt(data1.size - 10))
        val artifactFile1 = createTempArtifactFile(data1)
        val artifactFile2 = createTempArtifactFile(data2)
        val artifactFile3 = createTempArtifactFile(data3)
        val file1Len = artifactFile1.getSize()
        val file2Len = artifactFile2.getSize()
        val file3Len = artifactFile3.getSize()
        try {
            val digest1 = artifactFile1.getFileSha256()
            val digest2 = artifactFile2.getFileSha256()
            val digest3 = artifactFile3.getFileSha256()
            storageService.store(digest1, artifactFile1, null)
            storageService.store(digest2, artifactFile2, null)
            storageService.store(digest3, artifactFile3, null)
            val digest1CompressedSize = storageService.compress(
                digest1,
                file1Len,
                digest2,
                file2Len,
                null,
            )
            val digest2CompressedSize = storageService.compress(
                digest2,
                file2Len,
                digest3,
                file3Len,
                null,
            )
            // 压缩后，源文件已经不在
            // 压缩后，源文件已经不在
            Assertions.assertNull(storageService.load(digest1, Range.full(file1Len), null))
            Assertions.assertNull(storageService.load(digest2, Range.full(file2Len), null))
            // 级联恢复
            storageService.uncompress(
                digest2,
                digest2CompressedSize,
                digest3,
                file3Len,
                null,
            )
            val restored = storageService.uncompress(
                digest1,
                digest1CompressedSize,
                digest2,
                file2Len,
                null,
            )
            Assertions.assertEquals(1, restored)
            // 恢复后，数据不变
            val file1Data = storageService.load(digest1, Range.full(file1Len), null)
                ?.use { it.readBytes() }
            Assertions.assertArrayEquals(data1, file1Data)
            val file2Data = storageService.load(digest2, Range.full(file1Len), null)
                ?.use { it.readBytes() }
            Assertions.assertArrayEquals(data2, file2Data)
        } finally {
            artifactFile1.delete()
            artifactFile2.delete()
            artifactFile3.delete()
        }
    }

    @Test
    fun concurrentCompressTest() {
        val data1 = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        val artifactFile1 = createTempArtifactFile(data1)
        val digest = artifactFile1.getFileSha256()
        storageService.store(digest, artifactFile1, null)
        val cyclicBarrier = CyclicBarrier(10)
        repeat(10) {
            val data = data1.copyOfRange(it + 1, data1.size)
            val artifactFile = createTempArtifactFile(data)
            val sha256 = artifactFile.getFileSha256()
            storageService.store(sha256, artifactFile, null)
            thread {
                // 等待异步存储完成
                Thread.sleep(1000)
                cyclicBarrier.await()
                storageService.compress(sha256, data.size.toLong(), digest, data1.size.toLong(), null)
            }
        }
        // 等待执行
        Thread.sleep(2000)
    }

    private fun createTempArtifactFile(size: Long): ArtifactFile {
        val tempFile = createTempFile()
        val content = StringPool.randomString(size.toInt())
        content.byteInputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return FileSystemArtifactFile(tempFile)
    }

    private fun createTempArtifactFile(data: ByteArray): ArtifactFile {
        val tempFile = createTempFile()
        tempFile.writeBytes(data)
        return FileSystemArtifactFile(tempFile)
    }
}
