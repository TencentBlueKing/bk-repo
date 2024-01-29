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

import com.tencent.bkrepo.common.api.constant.ensureSuffix
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.EmptyInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.storage.core.AbstractStorageService
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import org.apache.commons.io.IOUtils
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

class ProxyStorageService : AbstractStorageService() {

    /**
     * 多存储一个sha256.sync文件，用来标记对应sha256的文件待同步至服务端
     */
    override fun doStore(
        path: String,
        filename: String,
        artifactFile: ArtifactFile,
        credentials: StorageCredentials,
        cancel: AtomicBoolean?
    ) {
        when {
            artifactFile.isInMemory() -> {
                fileStorage.store(path, filename, artifactFile.getInputStream(), artifactFile.getSize(), credentials)
            }
            artifactFile.isFallback() -> {
                fileStorage.store(path, filename, artifactFile.flushToFile(), credentials)
            }
            else -> {
                fileStorage.store(path, filename, artifactFile.flushToFile(), credentials)
            }
        }
        val syncFilename = filename.ensureSuffix(".sync")
        val inputStream =
            credentials.key?.let { IOUtils.toInputStream(it, Charset.defaultCharset()) } ?: EmptyInputStream()
        val size = credentials.key?.length?.toLong() ?: 0L
        fileStorage.store(path, syncFilename, inputStream, size, credentials)
    }

    override fun doLoad(
        path: String,
        filename: String,
        range: Range,
        credentials: StorageCredentials
    ): ArtifactInputStream? {
        return fileStorage.load(path, filename, range, credentials)?.artifactStream(range)
    }

    override fun doDelete(path: String, filename: String, credentials: StorageCredentials) {
        return fileStorage.delete(path, filename, credentials)
    }

    override fun doExist(path: String, filename: String, credentials: StorageCredentials): Boolean {
        return fileStorage.exist(path, filename, credentials)
    }

    /**
     * 同步数据到服务端
     */
    fun sync(rate: Long, cacheExpireDays: Int) {
        val credentials = getCredentialsOrDefault(null)
        val visitor = ProxySyncFileVisitor(rate, cacheExpireDays)
        require(credentials is FileSystemCredentials)
        FileSystemClient(credentials.path).walk(visitor)
        return
    }
}
