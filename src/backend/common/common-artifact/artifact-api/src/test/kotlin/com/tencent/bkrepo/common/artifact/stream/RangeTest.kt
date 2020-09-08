package com.tencent.bkrepo.common.artifact.stream

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class RangeTest {

    @Test
    fun testRange() {
        var range = Range(1, 1, 100)
        Assertions.assertEquals(1, range.length)
        Assertions.assertEquals(100, range.total)
        Assertions.assertTrue(range.isPartialContent())

        range = Range(1, 0, 100)
        Assertions.assertEquals(0, range.length)
        Assertions.assertEquals(100, range.total)
        Assertions.assertTrue(range.isPartialContent())

        range = Range(0, -1, 0)
        Assertions.assertEquals(0, range.length)
        Assertions.assertEquals(0, range.total)
        Assertions.assertFalse(range.isPartialContent())

        range = Range(0, 2505730872, 2505730873)
        Assertions.assertFalse(range.isPartialContent())
    }
}
