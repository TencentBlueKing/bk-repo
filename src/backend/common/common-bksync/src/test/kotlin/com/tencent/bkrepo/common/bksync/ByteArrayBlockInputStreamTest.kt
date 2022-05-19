package com.tencent.bkrepo.common.bksync

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ByteArrayBlockInputStreamTest {
    @Test
    fun getBlocks() {
        val data = byteArrayOf(
            1, 3, 4, 5,
            6, 7, 13, 8,
            9, 10, 11, 12,
            15
        )
        val blockInputStream = ByteArrayBlockInputStream(data, "")

        val getData1 = blockInputStream.getBlock(0, 2, 4)
        val data1 = byteArrayOf(
            1, 3, 4, 5,
            6, 7, 13, 8,
            9, 10, 11, 12
        )
        Assertions.assertArrayEquals(data1, getData1)

        val getData2 = blockInputStream.getBlock(2, 3, 4)
        val data2 = byteArrayOf(
            9, 10, 11, 12,
            15
        )
        Assertions.assertArrayEquals(data2, getData2)

        val getData3 = blockInputStream.getBlock(3, 3, 4)
        val data3 = byteArrayOf(15)
        Assertions.assertArrayEquals(data3, getData3)
    }
}
