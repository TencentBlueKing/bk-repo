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

package com.tencent.com.bkrepo.fs.storage

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.storage.config.MonitorProperties
import com.tencent.bkrepo.common.storage.config.ReceiveProperties
import com.tencent.bkrepo.common.storage.util.toPath
import com.tencent.bkrepo.fs.server.storage.CoArtifactDataReceiver
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.util.unit.DataSize

class CoArtifactDataReceiverTest {
    private val primaryPath = "temp".toPath()
    private val fallbackPath = "fallback".toPath()
    private val filename = "testfile"
    private val shortContent = StringPool.randomString(DEFAULT_BUFFER_SIZE).toByteArray()
    private val longContent = StringPool.randomString(DEFAULT_BUFFER_SIZE * 20).toByteArray()

    @BeforeEach
    fun initAndClean() {
        Files.createDirectories(primaryPath)
        Files.createDirectories(fallbackPath)
        Files.deleteIfExists(primaryPath.resolve(filename))
        Files.deleteIfExists(fallbackPath.resolve(filename))
    }

    @AfterEach
    fun clean() {
        Files.deleteIfExists(primaryPath.resolve(filename))
        Files.deleteIfExists(fallbackPath.resolve(filename))
    }

    @Test
    fun testNormalInMemory() {
        runBlocking {
            val receiver = createReceiver()
            val buffer = DefaultDataBufferFactory.sharedInstance.wrap(shortContent)
            receiver.receive(buffer)
            Assertions.assertTrue(receiver.inMemory)
            Assertions.assertFalse(Files.exists(primaryPath.resolve(filename)))
            Assertions.assertFalse(Files.exists(fallbackPath.resolve(filename)))
            receiver.finish()
            val content = readFromInputStream(receiver)
            Assertions.assertArrayEquals(shortContent, content)
        }
    }

    @Test
    fun testNormalInFile() {
        runBlocking {
            val receiver = createReceiver()
            val buffer = DefaultDataBufferFactory.sharedInstance.wrap(longContent)
            receiver.receive(buffer)
            Assertions.assertFalse(receiver.inMemory)
            Assertions.assertTrue(Files.exists(primaryPath.resolve(filename)))
            Assertions.assertFalse(Files.exists(fallbackPath.resolve(filename)))
            receiver.finish()
            receiver.assertContentEquals(longContent)
        }
    }

    @Test
    fun testFallback() {
        runBlocking {
            val receiver = createReceiver()
            val buffer = DefaultDataBufferFactory.sharedInstance.wrap(shortContent)
            // 此时文件在内存
            receiver.receive(buffer)

            // fallback
            receiver.unhealthy(fallbackPath, "Unit Test")

            // go on write
            val buffer2 = DefaultDataBufferFactory.sharedInstance.wrap(longContent)
            receiver.receive(buffer2)

            Assertions.assertFalse(receiver.inMemory)
            Assertions.assertFalse(Files.exists(primaryPath.resolve(filename)))
            Assertions.assertTrue(Files.exists(fallbackPath.resolve(filename)))
            receiver.finish()

            receiver.assertContentEquals(shortContent.plus(longContent))
        }
    }

    private fun CoArtifactDataReceiver.assertContentEquals(expected: ByteArray) {
        val fromInputStream = readFromInputStream(this)
        Assertions.assertArrayEquals(expected, fromInputStream)
        val fromFile = readFromFile(this)
        Assertions.assertArrayEquals(expected, fromFile)
    }

    private fun readFromFile(receiver: CoArtifactDataReceiver): ByteArray {
        val outputStream = ByteArrayOutputStream()
        receiver.getFile()!!.inputStream().use { it.copyTo(outputStream) }
        return outputStream.toByteArray()
    }
    private fun readFromInputStream(receiver: CoArtifactDataReceiver): ByteArray {
        val outputStream = ByteArrayOutputStream()
        receiver.getInputStream().use { it.copyTo(outputStream) }
        return outputStream.toByteArray()
    }

    private fun createReceiver(
        fileSizeThreshold: Long = DataSize.ofBytes(DEFAULT_BUFFER_SIZE * 10L).toBytes()
    ): CoArtifactDataReceiver {
        val receive = ReceiveProperties(
            fileSizeThreshold = DataSize.ofBytes(fileSizeThreshold),
            rateLimit = DataSize.ofBytes(-1)
        )
        val monitorProperties = MonitorProperties(enabled = true, enableTransfer = true)
        return CoArtifactDataReceiver(receive, monitorProperties, primaryPath, filename)
    }
}
