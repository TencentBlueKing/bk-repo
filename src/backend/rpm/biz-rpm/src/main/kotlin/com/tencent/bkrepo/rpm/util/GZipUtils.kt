/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.  
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 */

package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.rpm.pojo.IndexType
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GZipUtils {
    /**
     * 将'xml'以gzip压缩后返回'xml.gz'
     */
    fun ByteArray.gZip(indexType: IndexType): File {
        val file = File.createTempFile("rpm", "-${indexType.value}.xml.gz")
        GZIPOutputStream(FileOutputStream(file)).use { it.write(this, 0, this.size) }
        return file
    }

    @Throws(IOException::class)
    fun InputStream.gZip(indexType: IndexType): File {
        val file = File.createTempFile("rpm", "-${indexType.value}.xml.gz")
        val buffer = ByteArray(5 * 1024 * 1024)
        GZIPOutputStream(FileOutputStream(file)).use { gZIPOutputStream ->
            var mark: Int
            while (this.read(buffer).also { mark = it } > 0) {
                gZIPOutputStream.write(buffer, 0, mark)
                gZIPOutputStream.flush()
            }
        }
        return file
    }

    /**
     * 解压gz文件，并关闭文件流
     */
    fun InputStream.unGzipInputStream(): File {
        val gZIPInputStream = GZIPInputStream(this)
        val file = File.createTempFile("rpm", ".xmlStream")
        val bufferedOutputStream = BufferedOutputStream(FileOutputStream(file))
        val buffer = ByteArray(5 * 1024 * 1024)
        var mark: Int
        try {
            while (gZIPInputStream.read(buffer).also { mark = it } > 0) {
                bufferedOutputStream.write(buffer, 0, mark)
                bufferedOutputStream.flush()
            }
        } finally {
            gZIPInputStream.closeQuietly()
            bufferedOutputStream.closeQuietly()
            this.closeQuietly()
        }
        return file
    }
}
