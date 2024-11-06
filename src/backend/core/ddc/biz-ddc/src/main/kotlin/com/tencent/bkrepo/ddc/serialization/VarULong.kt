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

import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.experimental.inv


/**
 * 可变长度Long，具体表示规则参考下方示例
 *
 * 小于等于127的整形只需要一个字节表示, 最大值为0x01111111
 * 大于127的整形需要多个字节表示,第一个字节每一位表示除第一个字节外还需要的字节，例如
 * 129 二进制为0x10000000 0x10000001
 * 65537 二进制为 0x11000001 0x00000000 0x00000001
 * 大于等于2^56需要使用9个字节表示
 * 2^56+1的二进制为
 * 0x11111111 0x00000001 0x00000000 0x00000000 0x00000000 0x00000000 0x00000000 0x00000000 0x00000001
 */
object VarULong {
    /**
     * 判断buffer中的数字需要多少字节进行编码
     *
     * @return 需要的字节数，取值范围[1,9]
     */
    fun measure(buffer: ByteBuffer): Int {
        val b: Byte = buffer.get(buffer.position())
        val i = b.toInt()
        if (i in 0..127) {
            return 1
        }
        return Integer.numberOfLeadingZeros(b.inv().toInt()) - 23
    }

    /**
     * 测算需要多少字节才能表示指定的无符号Long
     *
     * @param unsignedValue 待测算的无符号整形Long
     *
     * @return 所需字节数
     */
    fun measureUnsigned(unsignedValue: Long): Int {
        var count = 0
        var v = unsignedValue
        do {
            v = v ushr 7
            count++

        } while (v != 0L)
        return minOf(count, 9)
    }

    /**
     * 写入一个变长Long到buffer中
     *
     * @param buffer 待写入的buffer
     * @param unsignedValue 待写入的值
     *
     * @return 写入的字节数
     */
    fun writeUnsigned(buffer: ByteBuffer, unsignedValue: Long): Int {
        var v = unsignedValue
        val position = buffer.position()
        val byteCount = measureUnsigned(v)
        for (idx in 1 until byteCount) {
            buffer.put(position + byteCount - idx, v.toByte())
            v = v ushr 8
        }
        val firstByte = ((0xff shl (9 - byteCount)) or v.toInt()).toByte()
        buffer.put(position, firstByte)
        buffer.position(position + byteCount)
        return byteCount
    }

    /**
     * 从buffer中读取变长Long
     *
     * @param buffer 待读取的buffer，会改变buffer position
     *
     * @return buffer中存储的值
     */
    fun readUnsigned(buffer: ByteBuffer): Long {
        val numBytes = measure(buffer)
        var value = (buffer.get() and (0xff ushr numBytes).toByte()).toLong()
        for (i in 1 until numBytes) {
            value = value shl 8
            value = value or (buffer.get().toLong() and 0xff)
        }
        return value
    }

    /**
     * 写入有符号数
     *
     * @param buffer 待写入的buffer
     * @param signedValue 待写入的值
     *
     * @return 写入的字节数
     */
    fun writeSigned(buffer: ByteBuffer, signedValue: Long): Int {
        return writeUnsigned(buffer, encodeSigned(signedValue))
    }

    /**
     * 从buffer中读取变长Long
     *
     * @param buffer 待读取的buffer，会改变buffer position
     *
     * @return buffer中存储的值
     */
    fun readSigned(buffer: ByteBuffer): Long {
        val value = readUnsigned(buffer)
        return decodeSigned(value)
    }

    private fun encodeSigned(value: Long): Long {
        return (value shr 63) xor (value shl 1)
    }

    private fun decodeSigned(value: Long): Long {
        return -(value and 1) xor (value ushr 1)
    }
}
