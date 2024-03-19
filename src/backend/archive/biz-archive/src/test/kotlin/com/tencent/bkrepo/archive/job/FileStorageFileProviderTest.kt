package com.tencent.bkrepo.archive.job

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.archive.utils.ArchiveUtils
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.util.StorageUtils
import com.tencent.bkrepo.common.storage.util.createFile
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.Ordered
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class FileStorageFileProviderTest {
    private val tempDir = System.getProperty("java.io.tmpdir")

    @AfterEach
    fun afterEach() {
        unmockkObject(StorageUtils.Companion)
    }

    private val fp = FileStorageFileProvider(
        Paths.get(tempDir),
        highWaterMark = Long.MAX_VALUE,
        lowWaterMark = Long.MAX_VALUE - 1,
        ArchiveUtils.newFixedAndCachedThreadPool(
            1,
            ThreadFactoryBuilder().setNameFormat("ut-%d").build(),
            PriorityBlockingQueue(),
        ),
        Duration.ofSeconds(60),
    )

    @Test
    fun priorityTest() {
        mockkObject(StorageUtils.Companion)
        every { StorageUtils.downloadUseLocalPath(any(), any(), any(), any()) } answers {
            Paths.get(tempDir).resolve("1").createFile()
            Paths.get(tempDir).resolve("2").createFile()
            Paths.get(tempDir).resolve("3").createFile()
            Paths.get(tempDir).resolve("4").createFile()
            Paths.get(tempDir).resolve("5").createFile()
            Paths.get(tempDir).resolve("100").createFile()
            Thread.sleep(1000)
        }
        val list = Collections.synchronizedList(mutableListOf<String>())
        for (i in 1..5) {
            Files.deleteIfExists(Paths.get(tempDir).resolve("$i"))
            fp.get("$i", Range.FULL_RANGE, InnerCosCredentials()).subscribe { list.add(it.name) }
        }
        // 最高优先级，插队下载
        Files.deleteIfExists(Paths.get(tempDir).resolve("100"))
        fp.get("100", Range.FULL_RANGE, InnerCosCredentials(), Ordered.HIGHEST_PRECEDENCE)
            .subscribe { list.add(it.name) }
        // 等待异步执行
        Thread.sleep(2000)
        Assertions.assertEquals("100", list[1])
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
            fp.get("$it", Range.FULL_RANGE, InnerCosCredentials()).subscribe()
        }
        Thread.sleep(1000)
        // 删除目录 不健康
        println("delete dir")
        tempPath.toFile().deleteRecursively()
        Thread.sleep(2000)
        repeat(3) {
            Files.deleteIfExists(tempPath.resolve("$it"))
            fp.get("$it", Range.FULL_RANGE, InnerCosCredentials()).subscribe()
        }
        Thread.sleep(2000)
        // 再次恢复目录健康
        println("recover dir")
        Files.createDirectories(tempPath)
        Thread.sleep(2000)
    }
}
