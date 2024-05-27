package com.tencent.bkrepo.archive.core

import com.tencent.bkrepo.archive.core.provider.FileStorageFileProvider
import com.tencent.bkrepo.archive.core.provider.FileTask
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.util.StorageUtils
import com.tencent.bkrepo.common.storage.util.createFile
import com.tencent.bkrepo.common.storage.util.toPath
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class FileStorageFileProviderTest {
    private val tempDir = System.getProperty("java.io.tmpdir").toPath().resolve("fp-test")
    private val timeout = Duration.ofSeconds(10)

    @AfterEach
    fun afterEach() {
        unmockkObject(StorageUtils.Companion)
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun diskHealthyTest() {
        val tempPath = createTempDir().toPath()
        val fp = FileStorageFileProvider(
            tempPath,
            highWaterMark = 2,
            lowWaterMark = 1,
            ThreadPoolExecutor(3, 3, 0L, TimeUnit.SECONDS, LinkedBlockingQueue()),
            Duration.ofMillis(1000),
        )
        mockkObject(StorageUtils.Companion)
        every { StorageUtils.downloadUseLocalPath(any(), any(), any(), any()) } answers {
            tempPath.resolve("0").createFile()
            tempPath.resolve("1").createFile()
            tempPath.resolve("2").createFile()
        }
        repeat(3) {
            Files.deleteIfExists(tempPath.resolve("$it"))
            fp.getWithTimeout(FileTask("$it", Range.FULL_RANGE, InnerCosCredentials())).subscribe()
        }
        Thread.sleep(1000)
        // 删除目录 不健康
        println("delete dir")
        tempPath.toFile().deleteRecursively()
        Thread.sleep(2000)
        repeat(3) {
            Files.deleteIfExists(tempPath.resolve("$it"))
            fp.getWithTimeout(FileTask("$it", Range.FULL_RANGE, InnerCosCredentials())).subscribe()
        }
        Thread.sleep(2000)
        // 再次恢复目录健康
        println("recover dir")
        Files.createDirectories(tempPath)
        Thread.sleep(2000)
    }

    private fun FileStorageFileProvider.getWithTimeout(file: FileTask): Mono<File> {
        return this.get(file).timeout(timeout)
    }
}
