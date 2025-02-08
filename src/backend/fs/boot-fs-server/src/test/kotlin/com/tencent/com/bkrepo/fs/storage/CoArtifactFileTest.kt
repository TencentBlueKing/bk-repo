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
import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.hash.sha256
import com.tencent.bkrepo.common.storage.config.UploadProperties
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.config.ReceiveProperties
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.config.MonitorProperties
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import com.tencent.bkrepo.fs.server.storage.CoArtifactFile
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.util.unit.DataSize

class CoArtifactFileTest {

    private val tempDir = System.getProperty("java.io.tmpdir")

    private val uploadProperties = UploadProperties(location = tempDir)

    private val storageCredentials = FileSystemCredentials(upload = uploadProperties)

    private val shortContent = StringPool.randomString(DEFAULT_BUFFER_SIZE).toByteArray()
    private val longContent = StringPool.randomString(DEFAULT_BUFFER_SIZE * 20).toByteArray()

    private fun buildArtifactFile(
        threshold: Long
    ): CoArtifactFile {
        val storageProperties = StorageProperties(
            filesystem = storageCredentials,
            receive = ReceiveProperties(
                fileSizeThreshold = DataSize.ofBytes(threshold)
            ),
            monitor = MonitorProperties()
        )
        val monitor = StorageHealthMonitor(storageProperties, tempDir)
        return CoArtifactFile(storageCredentials, storageProperties, monitor)
    }

    @Test
    fun testInMemory() {
        runBlocking {
            val artifactFile = buildArtifactFile(DEFAULT_BUFFER_SIZE * 10L)
            val buffer = DefaultDataBufferFactory.sharedInstance.wrap(shortContent)
            artifactFile.write(buffer)
            artifactFile.finish()
            Assertions.assertTrue(artifactFile.isInMemory())
            artifactFile.assertFileContentEquals(shortContent)
        }
    }

    @Test
    fun testInFile() {
        runBlocking {
            val artifactFile = buildArtifactFile(DEFAULT_BUFFER_SIZE * 10L)
            val buffer = DefaultDataBufferFactory.sharedInstance.wrap(longContent)
            artifactFile.write(buffer)
            artifactFile.finish()
            Assertions.assertFalse(artifactFile.isInMemory())
            Assertions.assertTrue(artifactFile.getFile()!!.exists())
            Assertions.assertTrue(artifactFile.getFile()!!.path.startsWith(tempDir))
            artifactFile.assertFileContentEquals(longContent)
            artifactFile.delete()
            Assertions.assertFalse(artifactFile.getFile()!!.exists())
        }
    }

    private fun CoArtifactFile.assertFileContentEquals(expected: ByteArray) {
        Assertions.assertArrayEquals(expected, this.readAll())
        Assertions.assertEquals(expected.sha1(), this.getFileSha1())
        Assertions.assertEquals(expected.md5(), this.getFileMd5())
        Assertions.assertEquals(expected.sha256(), this.getFileSha256())
    }

    private fun CoArtifactFile.readAll(): ByteArray {
        val outputStream = ByteArrayOutputStream()
        this.getInputStream().use { it.copyTo(outputStream) }
        return outputStream.toByteArray()
    }

    private fun ByteArray.sha1(): String {
        return this.inputStream().sha1()
    }

    private fun ByteArray.md5(): String {
        return this.inputStream().md5()
    }

    private fun ByteArray.sha256(): String {
        return this.inputStream().sha256()
    }
}
