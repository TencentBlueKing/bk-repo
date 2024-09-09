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

package com.tencent.bkrepo.common.storage.core.overlay

import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.artifact.hash.sha256
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

@ExtendWith(SpringExtension::class)
@ImportAutoConfiguration(StorageAutoConfiguration::class, TaskExecutionAutoConfiguration::class)
@TestPropertySource(locations = ["classpath:storage-fs.properties"])
class OverlaySupportTest {
    @Autowired
    lateinit var storageService: StorageService

    @Autowired
    private lateinit var storageProperties: StorageProperties

    @AfterEach
    fun afterEach() {
        FileUtils.deleteDirectory(File(storageProperties.filesystem.upload.location))
        FileUtils.deleteDirectory(File(storageProperties.filesystem.path))
    }

    @Test
    fun test() {
        val data = Random.nextBytes(20)
        val tmpFile = createTempFile()
        tmpFile.writeBytes(data)
        val digest = data.inputStream().sha256()
        storageService.store(digest, tmpFile.toArtifactFile(), null)
        val block1 = RegionResource(digest, 10, 20, 0, 20)
        val blocks = listOf(block1)
        val range = Range.full(100)
        val inputStream = storageService.load(blocks, range, null)
        Assertions.assertNotNull(inputStream)
        val output = ByteArrayOutputStream()
        inputStream!!.copyTo(output)
        val exceptData = ByteArray(100)
        data.copyInto(exceptData, 10, 0, data.size)
        Assertions.assertArrayEquals(exceptData, output.toByteArray())
    }
}
