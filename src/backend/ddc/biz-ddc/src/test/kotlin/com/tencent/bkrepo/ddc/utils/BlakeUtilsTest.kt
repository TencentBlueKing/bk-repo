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

package com.tencent.bkrepo.ddc.utils

import com.tencent.bkrepo.ddc.utils.BlakeUtils.hex
import com.tencent.bkrepo.ddc.utils.BlakeUtils.toBlake3InputStream
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class BlakeUtilsTest {

    @Test
    fun hash() {
        val content = "Hello world"
        assertEquals("e7e6fb7d2869d109b62cdb1227208d4016cdaa0a", BlakeUtils.hash(content).hex())
    }

    @Test
    fun hashBuffer() {
        val buffer = ByteBuffer.allocate(11)
        buffer.put("Hello ".toByteArray())
        val buffer2 = buffer.slice()
        buffer2.put("world".toByteArray())
        val buffers = listOf(
            buffer.flip() as ByteBuffer,
            buffer2.flip() as ByteBuffer
        )
        assertEquals("e7e6fb7d2869d109b62cdb1227208d4016cdaa0a", BlakeUtils.hash(buffers).hex())
    }

    @Test
    fun hashInputStream() {
        val blake3InputStream = ByteArrayInputStream("Hello world".toByteArray()).toBlake3InputStream()
        blake3InputStream.reader().readText()
        assertEquals("e7e6fb7d2869d109b62cdb1227208d4016cdaa0a", blake3InputStream.hash().hex())
    }
}
