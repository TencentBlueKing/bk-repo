package com.tencent.bkrepo.common.artifact.stream

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ZeroInputStreamTest {

    @Test
    fun testZeroLength() {
        val zeroInputStream = ZeroInputStream(0)
        Assertions.assertEquals(0, zeroInputStream.available())
        Assertions.assertEquals(-1, zeroInputStream.read())
        Assertions.assertEquals(-1, zeroInputStream.read(ByteArray(100)))
    }

    @Test
    fun testRead() {
        val zeroInputStream = ZeroInputStream(100)
        Assertions.assertEquals(100, zeroInputStream.available())
        Assertions.assertEquals(0, zeroInputStream.read())
        Assertions.assertEquals(99, zeroInputStream.available())
        Assertions.assertEquals(99, zeroInputStream.read(ByteArray(100)))
        Assertions.assertEquals(0, zeroInputStream.available())

        Assertions.assertEquals(-1, zeroInputStream.read(ByteArray(100)))
    }

}