/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.consistency

import com.tencent.bkrepo.common.frpc.FileEventBus
import com.tencent.bkrepo.common.frpc.TextEventMessageConverter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

class FileCheckProcessTest {
    private val tempFile = createTempFile()
    lateinit var fileEventBus: FileEventBus
    private val logPath = createTempDir()

    @BeforeEach
    fun beforeEach() {
        val messageConverter = TextEventMessageConverter()
        messageConverter.registerEvent(FileEventType.FILE_CHECK.name, FileCheckEvent::class.java)
        messageConverter.registerEvent(FileEventType.FILE_CHECK_ACK.name, FileCheckAckEvent::class.java)
        fileEventBus = FileEventBus(logPath.absolutePath, 200, messageConverter, 1000)
    }

    @AfterEach
    fun afterEach() {
        fileEventBus.stop()
        Thread.sleep(1000)
        logPath.deleteRecursively()
        Files.deleteIfExists(tempFile.toPath())
    }

    @Test
    fun checkFile() {
        val fileCheckProcess = FileCheckProcess(fileEventBus, 2000, true)

        val localhost = InetAddress.getLocalHost().hostAddress
        val path = tempFile.absolutePath
        // 没有handler处理，超时
        assertThrows<TimeoutException> { fileCheckProcess.check(listOf(localhost), path) }

        // 添加handler，正常执行
        fileEventBus.register(fileCheckProcess)
        assertDoesNotThrow { fileCheckProcess.check(listOf(localhost), path) }

        // 文件不存在，无响应，超时
        tempFile.delete()
        assertThrows<TimeoutException> { fileCheckProcess.check(listOf(localhost), path) }

        // 文件重新创建，正常响应
        thread {
            Thread.sleep(1000)
            tempFile.createNewFile()
        }
        val time = measureTimeMillis { assertDoesNotThrow { fileCheckProcess.check(listOf(localhost), path) } }
        Assertions.assertTrue(time > 1000)
        Thread.sleep(3000)
    }
}
