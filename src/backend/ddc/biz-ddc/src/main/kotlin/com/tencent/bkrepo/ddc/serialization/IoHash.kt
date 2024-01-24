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

import com.tencent.bkrepo.ddc.utils.BlakeUtils.hex
import java.nio.ByteBuffer
import kotlin.math.sign

/**
 * 20 bytes Blake3 hash
 */
data class IoHash(
    val a: Long,
    val b: Long,
    val c: Int
) : Comparable<IoHash> {
    companion object {
        const val NUM_BYTES = 20
        const val NUM_BITS = NUM_BYTES * 8
        val ZERO = IoHash(0L, 0L, 0)
    }

    constructor(bytes: ByteBuffer) : this(bytes.getLong(), bytes.getLong(), bytes.getInt())

    fun toByteArray(): ByteArray {
        val arr = ByteArray(NUM_BYTES)
        val buffer = ByteBuffer.wrap(arr)
        copyTo(buffer)
        return arr
    }

    fun copyTo(buffer: ByteBuffer) {
        buffer.putLong(a)
        buffer.putLong(b)
        buffer.putInt(c)
    }

    override fun compareTo(other: IoHash): Int {
        return when {
            a != other.a -> (a - other.a).sign
            b != other.b -> (b - other.b).sign
            else -> (c - other.c).sign
        }
    }

    override fun toString(): String {
        val buffer = ByteBuffer.allocate(NUM_BYTES)
        copyTo(buffer)
        buffer.flip()
        return buffer.hex()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IoHash) return false
        return a == other.a && b == other.b && c == other.c
    }

    override fun hashCode(): Int {
        return a.toInt()
    }
}
