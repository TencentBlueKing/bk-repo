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

package com.tencent.bkrepo.common.storage.core

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.hash.sha256
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.io.File
import java.nio.file.Files

@ExtendWith(SpringExtension::class)
@ImportAutoConfiguration(StorageAutoConfiguration::class, TaskExecutionAutoConfiguration::class)
@TestPropertySource(locations = ["classpath:storage-encrypt.properties"])
class EncryptFileStorageTest {

    private val filePath = createTempFile().toPath()

    @Autowired
    private lateinit var fileStorage: FileStorage

    @Autowired
    private lateinit var storageProperties: StorageProperties

    @BeforeEach
    fun beforeEach() {
        if (Files.notExists(filePath)) {
            Files.createFile(filePath)
        }
    }

    @AfterEach
    fun afterEach() {
        Files.deleteIfExists(filePath)
    }

    @Test
    fun storeFile() {
        val file = createFile()
        val sha256 = file.sha256()
        val path = "ut"
        val storageCredentials = storageProperties.defaultStorageCredentials()
        fileStorage.store(path, sha256, file, storageCredentials)
        Assertions.assertTrue(fileStorage.exist(path, sha256, storageCredentials))
        require(storageCredentials is FileSystemCredentials)
        val client = FileSystemClient(storageCredentials.path)
        // 存储的文件是加密的
        val alg = storageCredentials.encrypt.algorithm.toLowerCase()
        val encryptedFile = client.load(path, sha256.plus(".$alg"))
        Assertions.assertNotNull(encryptedFile)
        Assertions.assertNotEquals(sha256, encryptedFile?.sha256())
        // 加载文件时，会对文件解密
        val loadSha256 = fileStorage.load(path, sha256, Range.full(file.length()), storageCredentials)?.sha256()
        Assertions.assertEquals(sha256, loadSha256)
    }

    @Test
    fun storeStream() {
        val data = StringPool.randomString(10240)
        val sha256 = data.byteInputStream().sha256()
        val path = "ut"
        val storageCredentials = storageProperties.defaultStorageCredentials()
        val size = data.length.toLong()
        fileStorage.store(path, sha256, data.byteInputStream(), size, storageCredentials)
        Assertions.assertTrue(fileStorage.exist(path, sha256, storageCredentials))
        require(storageCredentials is FileSystemCredentials)
        val client = FileSystemClient(storageCredentials.path)
        // 存储的文件是加密的
        val alg = storageCredentials.encrypt.algorithm.toLowerCase()
        val encryptedFile = client.load(path, sha256.plus(".$alg"))
        Assertions.assertNotNull(encryptedFile)
        Assertions.assertNotEquals(sha256, encryptedFile?.sha256())
        // 加载文件时，会对文件解密
        val loadSha256 = fileStorage.load(path, sha256, Range.full(size), storageCredentials)?.sha256()
        Assertions.assertEquals(sha256, loadSha256)
    }

    @Test
    fun loadPartialContent() {
        val data = StringPool.randomString(10240)
        val sha256 = data.byteInputStream().sha256()
        val path = "ut"
        val storageCredentials = storageProperties.defaultStorageCredentials()
        val size = data.length.toLong()
        fileStorage.store(path, sha256, data.byteInputStream(), size, storageCredentials)
        val range = Range(1, 1024, data.length.toLong())
        val part = data.substring(range.start.toInt(), range.end.plus(1).toInt())
        val loadSha256 = fileStorage.load(path, sha256, range, storageCredentials)?.sha256()
        Assertions.assertEquals(part.sha256(), loadSha256)
    }

    @Test
    fun delete() {
        val file = createFile()
        val sha256 = file.sha256()
        val path = "ut"
        val storageCredentials = storageProperties.defaultStorageCredentials()
        fileStorage.store(path, sha256, file, storageCredentials)

        val input = fileStorage.load(path, sha256, Range.full(file.length()), storageCredentials)
        Assertions.assertNotNull(input)
        input?.close()
        fileStorage.delete(path, sha256, storageCredentials)
        val input2 = fileStorage.load(path, sha256, Range.full(file.length()), storageCredentials)
        Assertions.assertNull(input2)
    }

    @Test
    fun exist() {
        val file = createFile()
        val sha256 = file.sha256()
        val path = "ut"
        val storageCredentials = storageProperties.defaultStorageCredentials()
        fileStorage.store(path, sha256, file, storageCredentials)

        Assertions.assertTrue(fileStorage.exist(path, sha256, storageCredentials))
        fileStorage.delete(path, sha256, storageCredentials)
        Assertions.assertFalse(fileStorage.exist(path, sha256, storageCredentials))
    }

    private fun createFile(): File {
        val data = StringPool.randomString(10240)
        val file = filePath.toFile()
        file.writeText(data)
        return file
    }
}
