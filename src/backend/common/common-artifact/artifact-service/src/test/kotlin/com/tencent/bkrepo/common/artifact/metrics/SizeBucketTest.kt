package com.tencent.bkrepo.common.artifact.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SizeBucketTest {

    @Test
    fun `of maps bytes to MiB and GiB buckets`() {
        assertEquals(SizeBucket.LE_1MIB, SizeBucket.of(0))
        assertEquals(SizeBucket.LE_1MIB, SizeBucket.of(1L shl 20))
        assertEquals(SizeBucket.LE_10MIB, SizeBucket.of(1L shl 20 + 1))
        assertEquals(SizeBucket.LE_10MIB, SizeBucket.of(10L shl 20))
        assertEquals(SizeBucket.LE_50MIB, SizeBucket.of(10L shl 20 + 1))
        assertEquals(SizeBucket.LE_50MIB, SizeBucket.of(50L shl 20))
        assertEquals(SizeBucket.LE_100MIB, SizeBucket.of(50L shl 20 + 1))
        assertEquals(SizeBucket.LE_100MIB, SizeBucket.of(100L shl 20))
        assertEquals(SizeBucket.LE_1GIB, SizeBucket.of(100L shl 20 + 1))
        assertEquals(SizeBucket.LE_1GIB, SizeBucket.of(1L shl 30))
        assertEquals(SizeBucket.LE_10GIB, SizeBucket.of(1L shl 30 + 1))
        assertEquals(SizeBucket.LE_10GIB, SizeBucket.of(10L shl 30))
        assertEquals(SizeBucket.LE_50GIB, SizeBucket.of(10L shl 30 + 1))
        assertEquals(SizeBucket.LE_50GIB, SizeBucket.of(50L shl 30))
        assertEquals(SizeBucket.LE_100GIB, SizeBucket.of(50L shl 30 + 1))
        assertEquals(SizeBucket.LE_100GIB, SizeBucket.of(100L shl 30))
        assertEquals(SizeBucket.LE_200GIB, SizeBucket.of(100L shl 30 + 1))
        assertEquals(SizeBucket.LE_200GIB, SizeBucket.of(200L shl 30))
        assertEquals(SizeBucket.GT_200GIB, SizeBucket.of(200L shl 30 + 1))
    }
}
