package com.tencent.bkrepo.common.bksync.file

import com.tencent.bkrepo.common.bksync.file.BkSyncDeltaSource.Companion.toBkSyncDeltaSource
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.random.Random
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BDUtilsTest {
    private val worDir = Paths.get(System.getProperty("java.io.tmpdir"), "bksync-ut")

    @BeforeEach
    fun init() {
        Files.createDirectories(worDir)
    }

    @AfterEach
    fun cleanup() {
        worDir.toFile().deleteRecursively()
    }

    @Test
    fun deltaAndPatchTest() {
        val data1 = Random.nextBytes(Random.nextInt(1024, 1 shl 20))
        val data2 = data1.copyOfRange(Random.nextInt(1, 10), data1.size)
        val tempFile1 = createTempFile()
        tempFile1.writeBytes(data1)
        val tempFile2 = createTempFile()
        tempFile2.writeBytes(data2)
        val bdFile = BDUtils.delta(tempFile1, tempFile2, "srcKey", "destKey", worDir, 0.8f)
        val deltaFile = createTempFile()
        val bdSource = bdFile.toBkSyncDeltaSource(deltaFile)
        Assertions.assertEquals("srcKey", bdSource.src)
        Assertions.assertEquals("destKey", bdSource.dest)
        Assertions.assertArrayEquals(MessageDigest.getInstance("MD5").digest(data1), bdSource.md5Bytes)

        // 正常恢复
        val srcFile = BDUtils.patch(bdFile, tempFile2, worDir)
        Assertions.assertArrayEquals(data1, srcFile.readBytes())

        // 恢复出错
        Assertions.assertThrows(IllegalStateException::class.java) {
            BDUtils.patch(
                bdFile,
                tempFile1,
                worDir,
            )
        }
    }
}
