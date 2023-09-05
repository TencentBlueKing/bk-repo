/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.ddc.serialization

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class VarULongTest {
    @Test
    fun writeUnsigned() {
        val buffer = ByteBuffer.allocate(9)

        // test 127
        var num = 127L
        VarULong.writeUnsigned(buffer, num)
        assertEquals(1, buffer.position())
        buffer.flip()
        assertEquals(1, buffer.remaining())
        assertEquals(0b0111_1111.toByte(), buffer.get(0))
        assertEquals(num, VarULong.readUnsigned(buffer))

        // test 128
        num = 128L
        buffer.clear()
        VarULong.writeUnsigned(buffer, num)
        buffer.flip()
        assertEquals(0b1000_0000.toByte(), buffer.get(0))
        assertEquals(0b1000_0000.toByte(), buffer.get(1))
        assertEquals(num, VarULong.readUnsigned(buffer))

        num = 129
        buffer.clear()
        VarULong.writeUnsigned(buffer, num)
        buffer.flip()
        assertEquals(0b1000_0000.toByte(), buffer.get(0))
        assertEquals(0b1000_0001.toByte(), buffer.get(1))
        assertEquals(num, VarULong.readUnsigned(buffer))

        num = 65537
        buffer.clear()
        VarULong.writeUnsigned(buffer, num)
        buffer.flip()
        assertEquals(0b1100_0001.toByte(), buffer.get(0))
        assertEquals(0b0000_0000.toByte(), buffer.get(1))
        assertEquals(0b0000_0001.toByte(), buffer.get(2))
        assertEquals(num, VarULong.readUnsigned(buffer))

        num = 1L shl 56
        buffer.clear()
        VarULong.writeUnsigned(buffer, num)
        buffer.flip()
        assertEquals(0b1111_1111.toByte(), buffer.get(0))
        assertEquals(0b0000_0001.toByte(), buffer.get(1))
        assertEquals(0b0000_0000.toByte(), buffer.get(2))
        assertEquals(0b0000_0000.toByte(), buffer.get(3))
        assertEquals(0b0000_0000.toByte(), buffer.get(4))
        assertEquals(0b0000_0000.toByte(), buffer.get(5))
        assertEquals(0b0000_0000.toByte(), buffer.get(6))
        assertEquals(0b0000_0000.toByte(), buffer.get(7))
        assertEquals(0b0000_0000.toByte(), buffer.get(8))
        assertEquals(num, VarULong.readUnsigned(buffer))

        // negative
        num = -129 // 0x1111_1111{7} 0x0111_1111
        buffer.clear()
        VarULong.writeUnsigned(buffer, num)
        buffer.flip()
        assertEquals(0b1111_1111.toByte(), buffer.get(0))
        assertEquals(0b1111_1111.toByte(), buffer.get(7))
        assertEquals(0b0111_1111.toByte(), buffer.get(8))
        assertEquals(num, VarULong.readUnsigned(buffer))

        num = Long.MIN_VALUE // 0x1000_0000 0x0000_0000{8}
        buffer.clear()
        VarULong.writeUnsigned(buffer, num)
        buffer.flip()
        assertEquals(0b1111_1111.toByte(), buffer.get(0))
        assertEquals(0b1000_0000.toByte(), buffer.get(1))
        assertEquals(0b0000_0000.toByte(), buffer.get(8))
        assertEquals(num, VarULong.readUnsigned(buffer))
    }

    @Test
    fun writeSigned() {
        val buffer = ByteBuffer.allocate(9)
        testSigned(buffer, 1)
        testSigned(buffer, -1)

        testSigned(buffer, 129L)
        testSigned(buffer, -129L)

        testSigned(buffer, Long.MAX_VALUE)
        testSigned(buffer, Long.MIN_VALUE)
    }

    private fun testSigned(buffer: ByteBuffer, num: Long) {
        buffer.clear()
        VarULong.writeSigned(buffer, num)
        buffer.flip()
        assertEquals(num, VarULong.readSigned(buffer))
    }
}
