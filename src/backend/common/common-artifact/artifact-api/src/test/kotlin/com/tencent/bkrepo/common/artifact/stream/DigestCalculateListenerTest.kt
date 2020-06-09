package com.tencent.bkrepo.common.artifact.stream

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class DigestCalculateListenerTest {

    private val content = "Hello, world!"
    private val md5 = "6cd3556deb0da54bca060b4c39479839"
    private val sha256 = "315f5bdb76d078c43b8ac0064e4a0164612b1fce77c869345bfc94c75894edd3"

    @Test
    fun testCalculate() {
        val listener = DigestCalculateListener()
        copyTo(content.byteInputStream(), ByteArrayOutputStream(), listener)
        Assertions.assertEquals(md5, listener.md5)
        Assertions.assertEquals(sha256, listener.sha256)

    }

    private fun copyTo(input: InputStream, out: OutputStream, listener: StreamReceiveListener, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long {
        var bytesCopied: Long = 0
        val buffer = ByteArray(bufferSize)
        var bytes = input.read(buffer)
        while (bytes >= 0) {
            listener.data(buffer, 0, bytes)
            out.write(buffer, 0, bytes)
            bytesCopied += bytes
            bytes = input.read(buffer)
        }
        listener.finished()
        return bytesCopied
    }

}