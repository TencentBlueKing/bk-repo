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
import com.tencent.bkrepo.common.storage.config.MonitorProperties
import com.tencent.bkrepo.common.storage.config.ReceiveProperties
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.common.storage.util.createFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * 制品文件接收器
 * */
class CoArtifactDataReceiver(
    receiveProperties: ReceiveProperties,
    monitorProperties: MonitorProperties,
    private var path: Path,
    private val fileName: String = generateRandomName(),
) : StorageHealthMonitor.Observer {

    /**
     * 传输过程中发生存储降级时，是否将数据转移到本地磁盘
     */
    private val enableTransfer = monitorProperties.enableTransfer

    /**
     * 数据传输buffer大小
     */
    private val bufferSize = receiveProperties.bufferSize.toBytes().toInt()


    /**
     * 文件内存接收阈值
     * */
    private val fileSizeThreshold = receiveProperties.fileSizeThreshold.toBytes().toInt()

    /**
     * 缓存数据大小，默认等于fileSizeThreshold
     * */
    private val cacheDataSize = if (fileSizeThreshold == -1) 0 else fileSizeThreshold

    /**
     * 缓存数据
     * */
    private var cacheData: ByteArray? = ByteArray(cacheDataSize)

    /**
     * 文件接收位置
     * */
    private var pos: Long = 0

    /**
     * 文件是否在内存当中
     * */
    var inMemory = true

    /**
     * 文件路径，降级时会自动修改
     * */
    val filePath: Path get() = path.resolve(fileName)

    /**
     * 文件异步接收通道
     * */
    private var channel: AsynchronousFileChannel? = null

    /**
     * 是否降级
     * */
    var fallback: Boolean = false

    /**
     * 是否完成接收
     * */
    var finished: Boolean = false

    /**
     * 数据是否转移到本地磁盘
     */
    private var hasTransferred: Boolean = false

    /**
     * 降级路径
     * */
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

    /**
     * 用于异步计算摘要的流
     * */
    private val digestStream = Sinks
        .many()
        .unicast()
        .onBackpressureBuffer<ByteArray>()

    /**
     * 异步计算摘要执行结果
     * */
    val digestResult = Sinks.one<Void>()

    init {
        digestStream.asFlux()
            .publishOn(Schedulers.boundedElastic())
            .subscribe(DigestSubscriber())
    }

    suspend fun receive(buffer: DataBuffer) {
        if (startTime == 0L) {
            startTime = System.nanoTime()
        }
        checkFallback()
        val len = buffer.readableByteCount()
        if (pos + len > fileSizeThreshold && inMemory) {
            initChannel()
            flushToFile()
            DataBufferUtils.write(Mono.just(buffer), channel!!, pos).awaitSingle()
        } else if (inMemory) {
            buffer.read(cacheData!!, pos.toInt(), len)
        } else {
            initChannel()
            DataBufferUtils.write(Mono.just(buffer), channel!!, pos).awaitSingle()
        }
        buffer.readPosition(0)
        digest(buffer)
        pos += len
    }

    suspend fun flushToFile() {
        if (inMemory) {
            val cacheData = cacheData!!.copyOfRange(0, pos.toInt())
            val buf = DefaultDataBufferFactory.sharedInstance.wrap(cacheData)
            DataBufferUtils.write(Mono.just(buf), channel!!).awaitSingle()
            inMemory = false
            // help gc
            this.cacheData = null
        }
    }

    private suspend fun initChannel() {
        if (channel == null) {
            channel = withContext(Dispatchers.IO) {
                Files.createDirectories(filePath.parent)
                AsynchronousFileChannel.open(
                    filePath,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE_NEW,
                )
            }
        }
    }

    private fun digest(buffer: DataBuffer) {
        val len = buffer.readableByteCount()
        val digestArray = ByteArray(len)
        buffer.read(digestArray)
        digestStream.tryEmitNext(digestArray)
    }

    override fun unhealthy(fallbackPath: Path?, reason: String?) {
        if (!finished && !fallback && !hasTransferred) {
            fallBackPath = fallbackPath
            fallback = true
            logger.warn("Path[$path] is unhealthy, fallback to use [$fallBackPath], reason: $reason")
        }
    }

    /**
     * 检查是否需要fall back操作
     */
    private suspend fun checkFallback() {
        if (!fallback || hasTransferred) {
            return
        }
        if (fallBackPath == null || fallBackPath == path) {
            logger.info("Fallback path is null or equals to primary path, skip transfer data")
            hasTransferred = true
            return
        }
        // originalPath表示NFS位置， fallBackPath表示本地磁盘位置
        val originalPath = path
        // 更新当前path为本地磁盘
        path = fallBackPath!!
        // transfer date
        if (!inMemory) {
            // 当文件已经落到NFS
            if (enableTransfer) {
                // 开Transfer功能时，从NFS转移到本地盘
                cleanOriginalChannel()
                val originalFile = originalPath.resolve(fileName)
                val filePath = this.filePath.apply { this.createFile() }
                val dataBuffer = DataBufferUtils.read(originalPath, DefaultDataBufferFactory(), bufferSize)
                channel = withContext(Dispatchers.IO) {
                    AsynchronousFileChannel.open(
                        filePath,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE_NEW,
                    )
                }
                DataBufferUtils.write(dataBuffer, channel!!, 0).awaitSingle()
                withContext(Dispatchers.IO) {
                    Files.deleteIfExists(originalFile)
                }
                logger.info("Success to transfer data from [$originalPath] to [$path]")
            } else {
                // 禁用Transfer功能时，忽略操作，继续使用NFS
                path = originalPath
                fallback = false
            }
        }
        hasTransferred = true
    }

    /**
     * 关闭原始输出流
     */
    private fun cleanOriginalChannel() {
        try {
            channel?.force(true)
        } catch (ignored: IOException) {
        }

        try {
            channel?.close()
        } catch (ignored: IOException) {
        }
    }

    suspend fun finish(): Throughput {
        try {
            digestStream.tryEmitComplete()
            digestResult.asMono().awaitSingleOrNull()
            endTime = System.nanoTime()
            finished = true
            return Throughput(pos, endTime - startTime)
        } finally {
            if (!inMemory) {
                channel?.closeQuietly()
            }
        }
    }

    fun close() {
        if (!inMemory) {
            channel?.closeQuietly()
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
        return ByteArrayInputStream(cacheData, 0, pos.toInt())
    }

    private inner class DigestSubscriber : BaseSubscriber<ByteArray>() {
        override fun hookOnNext(value: ByteArray) {
            listener.data(value, 0, value.size)
        }

        override fun hookOnComplete() {
            listener.finished()
            digestResult.tryEmitEmpty()
        }

        override fun hookOnError(throwable: Throwable) {
            digestResult.tryEmitError(throwable)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CoArtifactDataReceiver::class.java)
        private const val ARTIFACT_ASYNC_PREFIX = "artifact_async_"
        private const val ARTIFACT_ASYNC_SUFFIX = ".temp"

        private fun generateRandomName(): String {
            return StringPool.randomStringByLongValue(
                prefix = ARTIFACT_ASYNC_PREFIX,
                suffix = ARTIFACT_ASYNC_SUFFIX,
            )
        }
    }
}
