/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.hash

import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest

sealed class HashAlgorithm(
    private val algorithm: String,
    private val hashLength: Int
) {

    fun hash(file: File) = hash(file.inputStream().buffered())

    fun digest(inputStream: InputStream): ByteArray {
        val digest = MessageDigest.getInstance(algorithm)
        inputStream.use {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var sizeRead = it.read(buffer)
            while (sizeRead != -1) {
                digest.update(buffer, 0, sizeRead)
                sizeRead = it.read(buffer)
            }
        }

        return digest.digest()
    }

    fun hash(inputStream: InputStream): String {
        val hashBytes = digest(inputStream)
        val hashInt = BigInteger(1, hashBytes)
        val hashText = hashInt.toString(RADIX)
        return if (hashText.length < hashLength)
            "0".repeat(hashLength - hashText.length) + hashText
        else hashText
    }

    fun hash(string: String): String = hash(string.byteInputStream())

    override fun toString(): String = algorithm

    class MD5 : HashAlgorithm("MD5", MD5_LENGTH)

    class SHA1 : HashAlgorithm("SHA-1", SHA1_LENGTH)

    class SHA256 : HashAlgorithm("SHA-256", SHA256_LENGTH)

    class SHA512 : HashAlgorithm("SHA-512", SHA512_LENGTH)

    companion object {
        private const val RADIX = 16
        const val MD5_LENGTH = 32
        const val SHA1_LENGTH = 40
        const val SHA256_LENGTH = 64
        const val SHA512_LENGTH = 128
    }
}

fun InputStream.sha512() = HashAlgorithm.SHA512().hash(this)
fun InputStream.sha256() = HashAlgorithm.SHA256().hash(this)
fun InputStream.sha1() = HashAlgorithm.SHA1().hash(this)
fun InputStream.md5() = HashAlgorithm.MD5().hash(this)

fun File.sha512() = this.inputStream().buffered().sha512()
fun File.sha256() = this.inputStream().buffered().sha256()
fun File.sha1() = this.inputStream().buffered().sha1()
fun File.md5() = this.inputStream().buffered().md5()

fun String.sha512() = this.byteInputStream().sha512()
fun String.sha256() = this.byteInputStream().sha256()
fun String.sha1() = this.byteInputStream().sha1()
fun String.md5() = this.byteInputStream().md5()
