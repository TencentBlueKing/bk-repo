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

package com.tencent.bkrepo.fs.server.storage

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.DigestCalculateListener
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.storage.core.config.ReceiveProperties
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import com.tencent.bkrepo.common.storage.monitor.Throughput
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Mono

class AsynchronousReceiver(
    receiveProperties: ReceiveProperties,
    private var path: Path,
    private val fileName: String = generateRandomName()
) : StorageHealthMonitor.Observer {

    private val fileSizeThreshold = receiveProperties.fileSizeThreshold.toBytes().toInt()
    private var cache: ByteArray? = ByteArray(fileSizeThreshold)
    private var pos: Long = 0
    var inMemory = true
    val filePath: Path get() = path.resolve(fileName)

    private val channel: AsynchronousFileChannel by lazy {
        AsynchronousFileChannel.open(
            filePath,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE_NEW
        )
    }
    var fallback: Boolean = false
    var finished: Boolean = false
    var fallBackPath: Path? = null

    /**
     * 数据摘要计算监听类
     */
    val listener = DigestCalculateListener()

    /**
     * 接收开始时间
     */
    private var startTime = 0L

    /**
     * 接收结束时间
     */
    private var endTime = 0L

    suspend fun receive(buffer: DataBuffer) {
        if (startTime == 0L) {
            startTime = System.nanoTime()
        }
        checkFallback()
        val len = buffer.readableByteCount()
        if (pos + len > fileSizeThreshold && inMemory) {
            flushToFile()
            DataBufferUtils.write(Mono.just(buffer), channel, pos).awaitSingle()
        } else if (inMemory) {
            buffer.read(cache!!, pos.toInt(), len)
        } else {
            DataBufferUtils.write(Mono.just(buffer), channel, pos).awaitSingle()
        }
        buffer.readPosition(0)
        digest(buffer)
        pos += len
    }

    suspend fun flushToFile() {
        if (inMemory) {
            val cacheData = cache!!.copyOfRange(0, pos.toInt())
            val buf = DefaultDataBufferFactory.sharedInstance.wrap(cacheData)
            DataBufferUtils.write(Mono.just(buf), channel).awaitSingle()
            inMemory = false
            // help gc
            cache = null
        }
    }

    private fun digest(buffer: DataBuffer) {
        val len = buffer.readableByteCount()
        val digestArray = ByteArray(len)
        buffer.read(digestArray)
        listener.data(digestArray, 0, len)
    }

    override fun unhealthy(fallbackPath: Path?, reason: String?) {
        if (!finished && !fallback) {
            fallBackPath = fallbackPath
            fallback = true
            logger.warn("Path[$path] is unhealthy, fallback to use [$fallBackPath], reason: $reason")
        }
    }

    private fun checkFallback() {
        if (!fallback) {
            return
        }
        if (fallBackPath == null || fallBackPath == path) {
            logger.info("Fallback path is null or equals to primary path,skip")
            return
        }
        if (inMemory) {
            path = fallBackPath!!
        }
    }

    fun finish(): Throughput {
        try {
            endTime = System.nanoTime()
            finished = true
            listener.finished()
            return Throughput(pos, endTime - startTime)
        } finally {
            if (!inMemory) {
                channel.closeQuietly()
            }
        }
    }

    fun close() {
        if (!inMemory) {
            channel.closeQuietly()
            Files.deleteIfExists(filePath)
            logger.info("Delete path $filePath")
        }
    }

    fun getFile(): File? {
        return if (inMemory) null else filePath.toFile()
    }

    fun getSize(): Long {
        return pos
    }

    fun getInputStream(): InputStream {
        getFile()?.let {
            return it.inputStream()
        }
        return ByteArrayInputStream(cache, 0, pos.toInt())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AsynchronousReceiver::class.java)
        private const val ARTIFACT_ASYNC_PREFIX = "artifact_async_"
        private const val ARTIFACT_ASYNC_SUFFIX = ".temp"

        private fun generateRandomName(): String {
            return StringPool.randomStringByLongValue(
                prefix = ARTIFACT_ASYNC_PREFIX,
                suffix = ARTIFACT_ASYNC_SUFFIX
            )
        }
    }
}
