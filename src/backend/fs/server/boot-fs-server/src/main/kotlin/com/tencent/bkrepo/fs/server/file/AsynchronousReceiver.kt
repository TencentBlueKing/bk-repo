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

package com.tencent.bkrepo.fs.server.file

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.DigestCalculateListener
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.inputStream
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Mono

class AsynchronousReceiver(
    private val path: Path,
    private val fileName: String = generateRandomName()
) {
    val bufferSize = DEFAULT_BUFFER_SIZE
    val cache = ByteArray(bufferSize)
    private var pos: Long = 0
    var inMemory = true
    private val filePath: Path by lazy { path.resolve(fileName) }

    private val channel: AsynchronousFileChannel by lazy {
        AsynchronousFileChannel.open(
            filePath,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE_NEW
        )
    }
    var finish: Boolean = false

    /**
     * 数据摘要计算监听类
     */
    val listener = DigestCalculateListener()

    val digestDataBuffer: ByteArray = ByteArray(bufferSize)

    suspend fun write(buffer: DataBuffer) {
        val len = buffer.readableByteCount()
        if (pos + len > bufferSize && inMemory) {
            flushToFile()
            DataBufferUtils.write(Mono.just(buffer), channel, pos).awaitSingle()
        } else if (inMemory) {
            buffer.read(cache, pos.toInt(), len)
        } else {
            DataBufferUtils.write(Mono.just(buffer), channel, pos).awaitSingle()
        }
        buffer.readPosition(0)
        buffer.read(digestDataBuffer, 0, len)
        listener.data(digestDataBuffer, 0, len)
        pos += len
    }

    suspend fun flushToFile() {
        if (inMemory) {
            val cacheData = cache.copyOfRange(0, pos.toInt())
            val buf = DefaultDataBufferFactory.sharedInstance.wrap(cacheData)
            DataBufferUtils.write(Mono.just(buf), channel, pos).awaitSingle()
            inMemory = false
        }
    }

    fun finish() {
        finish = true
        listener.finished()
    }

    fun close() {
        getFile()?.let {
            Files.deleteIfExists(filePath)
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
        return ByteArrayInputStream(cache)
    }

    companion object {
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
