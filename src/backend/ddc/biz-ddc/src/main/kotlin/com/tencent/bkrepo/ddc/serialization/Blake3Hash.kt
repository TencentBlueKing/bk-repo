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
import org.bouncycastle.crypto.digests.Blake3Digest
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 32 bytes blake3 hash
 */
class Blake3Hash(
    private val buffer: ByteBuffer
) : Comparable<Blake3Hash> {

    init {
        if (buffer.remaining() != NUM_BYTES) {
            throw IllegalArgumentException("Blake3Hash must be $NUM_BYTES bytes long")
        }
    }

    fun getBytes(): ByteBuffer {
        return buffer.asReadOnlyBuffer()
    }

    override fun compareTo(other: Blake3Hash): Int {
        val a = buffer.asReadOnlyBuffer()
        val b = other.buffer.asReadOnlyBuffer()

        for (idx in 0 until minOf(a.remaining(), b.remaining())) {
            val compare = a.get().compareTo(b.get())
            if (compare != 0) {
                return compare
            }
        }
        return a.remaining() - b.remaining()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Blake3Hash) return false
        return buffer.array().contentEquals(other.buffer.array())
    }

    override fun hashCode(): Int {
        return buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN).getInt()
    }

    override fun toString(): String {
        return buffer.hex()
    }

    companion object {
        const val NUM_BYTES = 32
        const val NUM_BITS = NUM_BYTES * 8
        val ZERO: Blake3Hash = Blake3Hash(ByteBuffer.allocate(NUM_BYTES))
        fun compute(data: ByteBuffer): Blake3Hash {
            val output = ByteArray(32)
            val digest = Blake3Digest(NUM_BYTES)
            digest.update(data.array(), data.arrayOffset() + data.position(), data.remaining())
            digest.doFinal(output, 0)
            return Blake3Hash(ByteBuffer.wrap(output))
        }
    }
}
