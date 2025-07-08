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

package com.tencent.bkrepo.proxy.artifact.storage

import com.tencent.bkrepo.common.storage.util.existReal
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView
import java.time.Instant
import java.time.temporal.ChronoUnit

object ProxyStorageUtils {

    fun deleteCacheFile(filePath: Path, cacheExpireDays: Int) {
        if (filePath.toFile().isDirectory) {
            return
        }
        val syncFilePath = Paths.get(filePath.toString().plus(".sync"))
        // 还未同步到服务端
        if (syncFilePath.existReal()) {
            return
        }
        val view = Files.getFileAttributeView(filePath, BasicFileAttributeView::class.java)
        val attributes = view.readAttributes()
        val aTime = attributes.lastAccessTime().toInstant()
        val expireTime = Instant.now().minus(cacheExpireDays.toLong(), ChronoUnit.DAYS)
        if (aTime.isBefore(expireTime)) {
            filePath.toFile().delete()
        }
    }

    fun readStorageCredentialKeys(syncFile: File): List<String?> {
        if (syncFile.length() == 0L) {
             return listOf(null)
        }

        val keys = mutableListOf<String?>()
        syncFile.readLines().forEach {
            if (it == "null") {
                keys.add(null)
            } else {
                keys.add(it)
            }
        }
        return keys
    }
}
