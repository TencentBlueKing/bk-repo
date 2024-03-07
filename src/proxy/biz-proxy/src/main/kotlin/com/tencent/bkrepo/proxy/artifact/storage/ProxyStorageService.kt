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
import com.tencent.bkrepo.common.api.util.StreamUtils.readText
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.storage.core.AbstractStorageService
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.repository.api.proxy.ProxyFileReferenceClient
import org.apache.commons.io.IOUtils
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

class ProxyStorageService : AbstractStorageService() {

    /**
     * 多存储一个sha256.sync文件，用来标记对应sha256的文件待同步至服务端
     * sha256.sync文件中记录credentialsKey
     */
    override fun doStore(
        path: String,
        filename: String,
        artifactFile: ArtifactFile,
        credentials: StorageCredentials,
        cancel: AtomicBoolean?
    ) {
        val proxyCredentials = storageProperties.defaultStorageCredentials()
        when {
            artifactFile.isInMemory() -> {
                fileStorage.store(
                    path, filename, artifactFile.getInputStream(), artifactFile.getSize(), proxyCredentials
                )
            }
            artifactFile.isFallback() -> {
                fileStorage.store(path, filename, artifactFile.flushToFile(), proxyCredentials)
            }
            else -> {
                fileStorage.store(path, filename, artifactFile.flushToFile(), proxyCredentials)
            }
        }
        val syncFilename = filename.ensureSuffix(".sync")
        val inputStream = IOUtils.toInputStream(credentials.key.toString(), Charset.defaultCharset())
        val size = credentials.key.toString().length.toLong()
        fileStorage.store(path, syncFilename, inputStream, size, storageProperties.defaultStorageCredentials())
    }

    override fun doLoad(
        path: String,
        filename: String,
        range: Range,
        credentials: StorageCredentials
    ): ArtifactInputStream? {
        return fileStorage.load(path, filename, range, storageProperties.defaultStorageCredentials())
            ?.artifactStream(range)
    }

    override fun doDelete(path: String, filename: String, credentials: StorageCredentials) {
        return fileStorage.delete(path, filename, storageProperties.defaultStorageCredentials())
    }

    override fun doExist(path: String, filename: String, credentials: StorageCredentials): Boolean {
        val proxyCredentials = storageProperties.defaultStorageCredentials()
        val dataFileExist = fileStorage.exist(path, filename, proxyCredentials)
        if (!dataFileExist) {
            return false
        }

        // 数据文件存在，确认对应credentialsKey已记录
        val syncFileName = filename.plus(".sync")
        val syncFileExist = fileStorage.exist(path, syncFileName, proxyCredentials)
        if (syncFileExist) {
            val keys = fileStorage.load(path, syncFileName, Range.FULL_RANGE, proxyCredentials)?.use {
                it.readText().lines().toMutableSet()
            }
            keys?.add(credentials.key.toString())
            val content = keys.orEmpty().joinToString(System.lineSeparator())
            val inputStream = IOUtils.toInputStream(content, Charset.defaultCharset())
            val size = content.length.toLong()
            fileStorage.delete(path, syncFileName, proxyCredentials)
            fileStorage.store(path, syncFileName, inputStream, size, proxyCredentials)
        } else {
            val inputStream = IOUtils.toInputStream(credentials.key.toString(), Charset.defaultCharset())
            val size = credentials.key.toString().length.toLong()
            fileStorage.store(path, syncFileName, inputStream, size, storageProperties.defaultStorageCredentials())
        }
        return true
    }

    /**
     * 同步数据到服务端
     */
    fun sync(rate: Long, cacheExpireDays: Int) {
        val credentials = storageProperties.defaultStorageCredentials()
        val visitor = ProxySyncFileVisitor(rate, cacheExpireDays)
        require(credentials is FileSystemCredentials)
        FileSystemClient(credentials.path).walk(visitor)
    }

    /**
     * 不同步时，清理已删除文件对应存储
     */
    fun clean(
        proxyFileReferenceClient: ProxyFileReferenceClient,
        cacheExpireDays: Int
    ) {
        val credentials = storageProperties.defaultStorageCredentials()
        val visitor = ProxyCleanFileVisitor(proxyFileReferenceClient, cacheExpireDays)
        require(credentials is FileSystemCredentials)
        FileSystemClient(credentials.path).walk(visitor)
    }
}
