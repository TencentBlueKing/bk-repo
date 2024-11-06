package com.tencent.bkrepo.archive.core.provider

import com.tencent.bkrepo.common.storage.util.toPath
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.time.Duration

class CacheFileProviderProxyTest {

    private val tempFilePath = System.getProperty("java.io.tmpdir").toPath().resolve("test")
    private val cachePath = System.getProperty("java.io.tmpdir").toPath().resolve("cache")

    @BeforeEach
    fun beforeEach() {
        Files.createFile(tempFilePath)
        Files.createDirectories(cachePath)
    }

    @AfterEach
    fun after() {
        Files.deleteIfExists(tempFilePath)
        cachePath.toFile().deleteRecursively()
    }

    @Test
    fun cacheTest() {
        val fp = mockk<FileProvider<Int>>()
        every { fp.get(any()) } answers {
            Mono.just(tempFilePath.toFile())
        }
        every { fp.key(any()) } returns "key"
        val cacheFp = CacheFileProviderProxy(fp, Duration.ofSeconds(0), cachePath)
        cacheFp.get(1).subscribe()
        Assertions.assertFalse(Files.exists(tempFilePath))
        val cacheFilePath = cachePath.resolve("key")
        Assertions.assertTrue(Files.exists(cacheFilePath))
        Thread.sleep(2000)
        Assertions.assertFalse(Files.exists(cacheFilePath))
    }
}
