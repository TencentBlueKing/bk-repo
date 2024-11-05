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

import com.tencent.bkrepo.common.artifact.resolve.file.stream.StreamArtifactFile
import com.tencent.bkrepo.ddc.serialization.IoHash.Companion.NUM_BYTES
import org.bouncycastle.crypto.digests.Blake3Digest
import org.bouncycastle.util.encoders.Hex
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

object BlakeUtils {
    const val OUT_LEN = NUM_BYTES
    val ZERO = ByteBuffer.allocate(OUT_LEN)
    fun hash(content: String): ByteArray {
        return hash(content.toByteArray())
    }

    fun hash(bytes: ByteArray): ByteArray {
        val digest = Blake3Digest(OUT_LEN)
        digest.update(bytes, 0, bytes.size)
        val hashCode = ByteArray(digest.digestSize)
        digest.doFinal(hashCode, 0)
        return hashCode
    }

    fun hash(byteBuffers: List<ByteBuffer>): ByteArray {
        val digest = Blake3Digest(OUT_LEN)
        byteBuffers.forEach { buffer ->
            digest.update(buffer.array(), buffer.arrayOffset(), buffer.remaining())
        }
        val hashCode = ByteArray(digest.digestSize)
        digest.doFinal(hashCode, 0)
        return hashCode
    }

    fun ByteArray.hex(): String = Hex.toHexString(this)

    fun ByteBuffer.hex(): String {
        val arr = ByteBuffer.allocate(remaining()).put(duplicate()).array()
        return Hex.toHexString(arr, 0, arr.size)
    }

    fun InputStream.toBlake3InputStream() = Blake3InputStream(this)

    // TODO 改为在读文件时就计算哈希，避免重复读流
    fun StreamArtifactFile.blake3(): ByteArray {
        val digest = Blake3Digest(OUT_LEN)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        getInputStream().use {
            var size = it.read(buffer)
            while (size != -1) {
                digest.update(buffer, 0, size)
                size = it.read(buffer)
            }
        }
        val hashCode = ByteArray(digest.digestSize)
        digest.doFinal(hashCode, 0)
        return hashCode
    }

    class Blake3InputStream(inputStream: InputStream) : FilterInputStream(inputStream) {

        private val digest by lazy { Blake3Digest(OUT_LEN) }

        @Throws(IOException::class)
        override fun read(): Int {
            val b = `in`.read()
            if (b != -1) {
                digest.update(b.toByte())
            }
            return b
        }

        @Throws(IOException::class)
        override fun read(bytes: ByteArray, off: Int, len: Int): Int {
            val numOfBytesRead = `in`.read(bytes, off, len)
            if (numOfBytesRead != -1) {
                digest.update(bytes, off, numOfBytesRead)
            }
            return numOfBytesRead
        }

        override fun markSupported(): Boolean {
            return false
        }

        override fun mark(readlimit: Int) {}

        @Throws(IOException::class)
        override fun reset() {
            throw IOException("reset not supported")
        }

        fun hash(): ByteArray {
            val hashCode = ByteArray(digest.digestSize)
            digest.doFinal(hashCode, 0)
            return hashCode
        }
    }
}
