/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.fs.server.storage

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import com.tencent.bkrepo.common.storage.util.toPath
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import java.io.File
import java.io.InputStream

/**
 * 制品文件
 * */
class CoArtifactFile(
    storageCredentials: StorageCredentials,
    storageProperties: StorageProperties,
    val monitor: StorageHealthMonitor
) : ArtifactFile {

    /**
     * 是否初始化
     */
    private var initialized: Boolean = false

    private val receiver: CoArtifactDataReceiver

    /**
     * 文件sha1值
     */
    private val sha1: String by lazy {
        getInputStream().sha1()
    }

    init {
        val path = storageCredentials.upload.location.toPath()
        receiver = CoArtifactDataReceiver(
            storageProperties.receive,
            storageProperties.monitor,
            path
        )
        monitor.add(receiver)
        if (!monitor.healthy.get()) {
            receiver.unhealthy(monitor.getFallbackPath(), monitor.fallBackReason)
        }
    }

    override fun getInputStream(): InputStream {
        require(initialized)
        return receiver.getInputStream()
    }

    override fun getSize(): Long {
        return receiver.getSize()
    }

    override fun isInMemory(): Boolean {
        require(initialized)
        return receiver.inMemory
    }

    override fun getFile(): File? {
        require(initialized)
        return receiver.getFile()
    }

    override fun flushToFile(): File {
        require(initialized)
        runBlocking { receiver.flushToFile() }
        return receiver.filePath.toFile()
    }

    override fun delete() {
        this.close()
    }

    override fun hasInitialized(): Boolean {
        return initialized
    }

    override fun isFallback(): Boolean {
        runBlocking { finish() }
        return receiver.fallback
    }

    override fun isInLocalDisk(): Boolean {
        return false
    }

    override fun getFileMd5(): String {
        require(initialized)
        return receiver.listener.getMd5()
    }

    override fun getFileSha1(): String {
        require(initialized)
        return sha1
    }

    override fun getFileSha256(): String {
        require(initialized)
        return receiver.listener.getSha256()
    }

    override fun getFileCrc64ecma(): String {
        require(initialized)
        return receiver.listener.getCrc64ecma()
    }

    suspend fun write(buffer: DataBuffer) {
        receiver.receive(buffer)
    }

    suspend fun finish() {
        if (!initialized) {
            initialized = true
            monitor.remove(receiver)
            val throughput = receiver.finish()
            logger.info("Receive file $throughput")
        }
    }

    fun close() {
        receiver.close()
        monitor.remove(receiver)
    }
    companion object {
        private val logger = LoggerFactory.getLogger(CoArtifactFile::class.java)
    }
}
