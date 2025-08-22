/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.manager

import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.artifact.stream.StreamReadListener
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.util.createNewOutputStream
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import org.slf4j.LoggerFactory

/**
 * 代理拉取数据写入缓存
 */
class ProxyBlobCacheWriter(
    private val storageService: StorageService,
    val digest: String
) : StreamReadListener {

    private val receivedPath = storageService.getTempPath().resolve(digest)
    private var outputStream: OutputStream? = null

    init {
        try {
            if (Files.exists(receivedPath)) {
                logger.debug("Path[$receivedPath] exists, ignore caching")
            } else {
                outputStream = receivedPath.createNewOutputStream()
            }
        } catch (ignore: FileAlreadyExistsException) {
            // 如果目录或者文件已存在则忽略
        } catch (exception: Exception) {
            logger.error("initial ProxyBlobCacheWriter error: $exception", exception)
            close()
        }
    }

    override fun data(i: Int) {
        outputStream?.write(i)
    }

    override fun data(buffer: ByteArray, off: Int, length: Int) {
        outputStream?.write(buffer, off, length)
    }

    override fun finish() {
        outputStream?.flush()
        outputStream?.closeQuietly()
        storageService.store(digest, receivedPath.toFile().toArtifactFile(), null)
    }

    override fun close() {
        outputStream?.let {
            it.flush()
            it.closeQuietly()
            Files.deleteIfExists(receivedPath)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProxyBlobCacheWriter::class.java)
    }
}
