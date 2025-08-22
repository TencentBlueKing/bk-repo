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

package com.tencent.bkrepo.common.storage.monitor

import com.tencent.bkrepo.common.artifact.stream.ZeroInputStream
import org.slf4j.LoggerFactory
import org.springframework.util.unit.DataSize
import java.io.Closeable
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 存储健康检查类
 */
class StorageHealthChecker(dir: Path, private val dataSize: DataSize) : Closeable {

    private val tempPath = dir.resolve(System.nanoTime().toString())

    private var closed: AtomicBoolean = AtomicBoolean(false)

    private val checked: AtomicBoolean = AtomicBoolean(false)

    private val outputStream: OutputStream

    init {
        Files.createFile(tempPath)
        outputStream = Files.newOutputStream(tempPath)
    }

    /**
     * 检查
     */
    fun check() {
        if (closed.get()) {
            throw RuntimeException("Checker is already closed")
        }
        if (!checked.compareAndSet(false, true)) {
            throw RuntimeException("Checker is already checked")
        }

        try {
            ZeroInputStream(dataSize.toBytes()).copyTo(outputStream)
        } finally {
            clean()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        try {
            outputStream.close()
        } catch (e: Exception) {
            logger.warn("Close checker failed", e)
        }
    }

    /**
     * 清理
     */
    private fun clean() {
        close()
        try {
            Files.deleteIfExists(tempPath)
        } catch (exception: Exception) {
            val errorMsg = "Clean checker error: $exception"
            if (exception is AccessDeniedException) {
                logger.error(errorMsg, exception)
            } else {
                logger.warn(errorMsg, exception)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StorageHealthChecker::class.java)
    }
}
