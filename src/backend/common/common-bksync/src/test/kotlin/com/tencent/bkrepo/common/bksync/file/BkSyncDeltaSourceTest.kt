package com.tencent.bkrepo.common.bksync.file

import com.tencent.bkrepo.common.api.stream.readInt
import com.tencent.bkrepo.common.api.stream.readLong
import com.tencent.bkrepo.common.bksync.file.BkSyncDeltaSource.Companion.toBkSyncDeltaSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import kotlin.random.Random
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BkSyncDeltaSourceTest {
    @Test
    fun writeTest() {
        val data = Random.nextBytes(Random.nextInt(8 shl 20))
        val srcMd5bytes = Random.nextBytes(16)
        val bd = ByteArrayBkSyncDeltaSource(
            src = "bd-test-src",
            dest = "bk-test-dest",
            srcMd5 = srcMd5bytes,
            bytes = data,
        )
        val outputStream = ByteArrayOutputStream()
        bd.writeTo(outputStream)
        val encodedData = outputStream.toByteArray()
        // 校验大小
        Assertions.assertTrue(bd.getSize() == encodedData.size.toLong())
        // 校验header
        val inputStream = ByteArrayInputStream(encodedData)
        // magic number
        Assertions.assertEquals(BkSyncDeltaSource.BD_MAGIC, inputStream.readInt())

        // key
        val nameLen = bd.src.length.shl(16) or bd.dest.length
        Assertions.assertEquals(nameLen, inputStream.readInt())
        val srcLen = nameLen.ushr(16)
        val destLen = 0xFFFF and nameLen
        Assertions.assertEquals(bd.src.length, srcLen)
        Assertions.assertEquals(bd.dest.length, destLen)
        var bytes = ByteArray(srcLen + destLen)
        val read = inputStream.read(bytes)
        Assertions.assertEquals(bytes.size, read)
        val src = String(bytes, 0, srcLen)
        val dest = String(bytes, srcLen, destLen)
        Assertions.assertEquals(bd.src, src)
        Assertions.assertEquals(bd.dest, dest)

        // md5
        val md5Bytes = ByteArray(16)
        inputStream.read(md5Bytes)
        Assertions.assertArrayEquals(srcMd5bytes, md5Bytes)

        // 校验body
        Assertions.assertEquals(data.size.toLong(), inputStream.readLong())
        // extra
        Assertions.assertEquals(0, inputStream.read())
        bytes = ByteArray(data.size)
        inputStream.read(bytes)
        Assertions.assertArrayEquals(data, bytes)

        // 校验trailer
        val crC32 = CRC32()
        crC32.update(bytes)
        Assertions.assertEquals(crC32.value, inputStream.readLong())
    }

    @Test
    fun readTest() {
        val data = Random.nextBytes(Random.nextInt(8 shl 20))
        val srcMd5bytes = Random.nextBytes(16)
        val bd = ByteArrayBkSyncDeltaSource(
            src = "bd-test-src",
            dest = "bk-test-dest",
            srcMd5 = srcMd5bytes,
            bytes = data,
        )
        val outputStream = ByteArrayOutputStream()
        bd.writeTo(outputStream)
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val file = createTempFile()
        try {
            val readBdSource = inputStream.toBkSyncDeltaSource(file)
            Assertions.assertEquals(bd.src, readBdSource.src)
            Assertions.assertEquals(bd.dest, readBdSource.dest)
            Assertions.assertArrayEquals(bd.md5Bytes, readBdSource.md5Bytes)
            Assertions.assertEquals(bd.getSize(), readBdSource.getSize())
            Assertions.assertEquals(bd.contentLength(), readBdSource.contentLength())
            Assertions.assertEquals(bd.contentLength(), file.length())
            val data1 = ByteArray(data.size)
            readBdSource.content().use { it.read(data1) }
            Assertions.assertArrayEquals(data, data1)
        } finally {
            file.delete()
        }
    }
}
