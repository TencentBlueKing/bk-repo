/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.resolve.file.memory

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.hash.crc64Ecma
import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.hash.sha256
import java.io.File
import java.io.InputStream

class ByteArrayArtifactFile(
    private val data: ByteArray,
) : ArtifactFile {

    private val sha1: String by lazy { getInputStream().sha1() }
    private val sha256: String by lazy { getInputStream().sha256() }
    private val md5: String by lazy { getInputStream().md5() }
    private val crc64Ecma: String by lazy { getInputStream().crc64Ecma() }

    override fun getInputStream(): InputStream {
        return data.inputStream()
    }

    override fun getSize(): Long {
        return data.size.toLong()
    }

    override fun isInMemory(): Boolean {
        return true
    }

    override fun getFile(): File? {
        throw UnsupportedOperationException("not implemented")
    }

    override fun flushToFile(): File {
        throw UnsupportedOperationException("not implemented")
    }

    override fun delete() {
        throw UnsupportedOperationException("not implemented")
    }

    override fun hasInitialized(): Boolean {
        return true
    }

    override fun isFallback(): Boolean {
        return false
    }

    override fun isInLocalDisk(): Boolean {
        return false
    }

    override fun getFileMd5(): String {
        return md5
    }

    override fun getFileSha1(): String {
        return sha1
    }

    override fun getFileSha256(): String {
        return sha256
    }

    override fun getFileCrc64Ecma(): String {
        return crc64Ecma
    }

    fun byteArray() = data
}
