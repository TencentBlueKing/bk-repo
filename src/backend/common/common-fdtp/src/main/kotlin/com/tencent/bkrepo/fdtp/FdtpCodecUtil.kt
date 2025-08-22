/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.fdtp

import io.netty.buffer.ByteBuf
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer

object FdtpCodecUtil {

    const val FRAME_HEADER_LENGTH = 9
    const val MAX_FRAME_SIZE = 64 * 1024 * 1024

    fun unMarshalHeaders(bytes: ByteArray): Map<String, String> {
        var pos = 0
        var keyLen = 0
        val headers = mutableMapOf<String, String>()
        val byteArrayOutputStream = ByteArrayOutputStream()
        while (pos < bytes.size) {
            val byte = bytes[pos++].toInt()
            if (byte != 0) {
                byteArrayOutputStream.write(byte)
                keyLen++
            } else {
                // get key
                if (keyLen == 0) {
                    throw FdtpError("Negative header key: $keyLen")
                }
                val keyBytes = byteArrayOutputStream.toByteArray()
                val key = String(keyBytes)
                // get value length
                if (bytes.size - pos < HEADER_VALUE_SIZE_LENGTH) {
                    throw FdtpError("Insufficient bytes to get value length")
                }
                val valueLengthBytes = bytes.copyOfRange(pos, pos + HEADER_VALUE_SIZE_LENGTH)
                pos += HEADER_VALUE_SIZE_LENGTH
                val valueLength = BigInteger(valueLengthBytes).toInt()
                // get value
                if (bytes.size - pos < valueLength) {
                    throw FdtpError("Insufficient bytes to get value")
                }
                val valueBytes = bytes.copyOfRange(pos, pos + valueLength)
                val value = String(valueBytes)
                pos += valueLength
                headers[key] = value
                pos++ // 跳过分隔符0
                // go to next k-v
                keyLen = 0
                byteArrayOutputStream.reset()
            }
        }
        return headers
    }

    fun marshalHeaders(headers: Map<String, String>): ByteArray {
        val byteOutputStream = ByteArrayOutputStream()
        headers.forEach { (key, value) ->
            byteOutputStream.write(key.toByteArray())
            byteOutputStream.write(0)
            val valueBytes = value.toByteArray()
            byteOutputStream.write(intToByteArray(valueBytes.size))
            byteOutputStream.write(valueBytes)
            byteOutputStream.write(0)
        }
        return byteOutputStream.toByteArray()
    }

    fun longToByteArray(long: Long): ByteArray {
        return ByteBuffer.allocate(8).putLong(long).array()
    }

    fun intToByteArray(int: Int): ByteArray {
        return ByteBuffer.allocate(4).putInt(int).array()
    }

    fun readUnsignedInt(buf: ByteBuf): Int {
        return buf.readInt() and 0x7fffffff
    }
}
