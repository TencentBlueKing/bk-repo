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

package com.tencent.com.bkrepo.fs.service

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.fs.server.api.RRepositoryClient
import com.tencent.bkrepo.fs.server.model.TBlockNode
import com.tencent.bkrepo.fs.server.service.BlockNodeService
import com.tencent.bkrepo.fs.server.service.FileNodeService
import com.tencent.bkrepo.fs.server.storage.CoStorageManager
import com.tencent.com.bkrepo.fs.UT_PROJECT_ID
import com.tencent.com.bkrepo.fs.UT_REPO_NAME
import com.tencent.com.bkrepo.fs.UT_USER
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq

@ExtendWith(MockitoExtension::class)
class FileNodeServiceTest {

    lateinit var fileNodeService: FileNodeService

    @Mock
    lateinit var storageService: StorageService

    @Mock
    lateinit var blockNodeService: BlockNodeService

    @Mock
    lateinit var rRepositoryClient: RRepositoryClient

    @BeforeEach
    fun beforeEach() {
        val coStorageManager = CoStorageManager(blockNodeService, storageService, rRepositoryClient)
        fileNodeService = FileNodeService(blockNodeService, coStorageManager)
    }

    @DisplayName("测试数据来自节点")
    @Test
    fun testReadFromNode() {
        runBlocking {
            // mock 节点数据
            val nodeData = byteArrayOf(1, 2, 3)
            `when`(storageService.load(eq("node"), any(), anyOrNull())).thenReturn(
                nodeData.inputStream().artifactStream(Range.full(3))
            )

            // 没有任何块
            `when`(blockNodeService.listBlocks(any(), any(), any(), any(), anyOrNull())).thenReturn(emptyList())

            val range = Range.full(3)
            val inputStream = fileNodeService.read(UT_PROJECT_ID, UT_REPO_NAME, "", null, "node", 3, range)
            Assertions.assertNotNull(inputStream)
            val out = ByteArrayOutputStream()
            inputStream!!.copyTo(out)
            Assertions.assertArrayEquals(nodeData, out.toByteArray())
        }
    }

    @DisplayName("测试数据来自块")
    @Test
    fun testReadFromBlock() {
        runBlocking {
            val nodeData = byteArrayOf(1, 2, 3)
            val block = createBlock("block", 0, 3)
            val blockData = byteArrayOf(4, 5, 6)
            `when`(blockNodeService.listBlocks(any(), any(), any(), any(), anyOrNull())).thenReturn(
                listOf(block)
            )
            `when`(storageService.load(any(), any(), anyOrNull()))
                .thenAnswer {
                    val digest = it.arguments[0] as String
                    val range = it.arguments[1] as Range
                    when (digest) {
                        "node" -> nodeData.copyOfRange(range.start.toInt(), range.end.toInt() + 1)
                            .inputStream()
                            .artifactStream(range)
                        "block" -> blockData.copyOfRange(range.start.toInt(), range.end.toInt() + 1)
                            .inputStream()
                            .artifactStream(range)
                        else -> null
                    }
                }
            val range = Range.full(3)
            val inputStream = fileNodeService.read(UT_PROJECT_ID, UT_REPO_NAME, "", null, "node", 3, range)
            Assertions.assertNotNull(inputStream)
            val out = ByteArrayOutputStream()
            inputStream!!.copyTo(out)
            Assertions.assertArrayEquals(blockData, out.toByteArray())
        }
    }

    @DisplayName("测试节点数据被块数据覆盖")
    @Test
    fun testReadFromMix() {
        runBlocking {
            val nodeData = byteArrayOf(1, 2, 3)
            val block = createBlock("block", 1, 2)
            val blockData = byteArrayOf(4, 5)
            `when`(blockNodeService.listBlocks(any(), any(), any(), any(), anyOrNull())).thenReturn(
                listOf(block)
            )
            `when`(storageService.load(any(), any(), anyOrNull()))
                .thenAnswer {
                    val digest = it.arguments[0] as String
                    val range = it.arguments[1] as Range
                    when (digest) {
                        "node" -> nodeData.copyOfRange(range.start.toInt(), range.end.toInt() + 1)
                            .inputStream()
                            .artifactStream(range)
                        "block" -> blockData.copyOfRange(range.start.toInt(), range.end.toInt() + 1)
                            .inputStream()
                            .artifactStream(range)
                        else -> null
                    }
                }

            val range = Range.full(3)
            val inputStream = fileNodeService.read(UT_PROJECT_ID, UT_REPO_NAME, "", null, "node", 3, range)
            Assertions.assertNotNull(inputStream)
            val out = ByteArrayOutputStream()
            inputStream!!.copyTo(out)
            val expected = byteArrayOf(1, 4, 5)
            Assertions.assertArrayEquals(expected, out.toByteArray())
        }
    }

    @DisplayName("测试块结束位置大于节点的情况")
    @Test
    fun testReadOnBlockAfterNodeSize() {
        runBlocking {
            // n{1 2 3} 0 b{4 5}
            val nodeData = byteArrayOf(1, 2, 3)
            val block = createBlock("block", 3, 2)
            val blockData = byteArrayOf(4, 5)
            `when`(blockNodeService.listBlocks(any(), any(), any(), any(), anyOrNull())).thenReturn(
                listOf(block)
            )
            `when`(storageService.load(any(), any(), anyOrNull()))
                .thenAnswer {
                    val digest = it.arguments[0] as String
                    val range = it.arguments[1] as Range
                    when (digest) {
                        "node" -> nodeData.copyOfRange(range.start.toInt(), range.end.toInt() + 1)
                            .inputStream()
                            .artifactStream(range)
                        "block" -> blockData.copyOfRange(range.start.toInt(), range.end.toInt() + 1)
                            .inputStream()
                            .artifactStream(range)
                        else -> null
                    }
                }

            val range = Range.full(5)
            val inputStream = fileNodeService.read(UT_PROJECT_ID, UT_REPO_NAME, "", null, "node", 3, range)
            Assertions.assertNotNull(inputStream)
            val out = ByteArrayOutputStream()
            inputStream!!.copyTo(out)
            val expected = byteArrayOf(1, 2, 3, 4, 5)
            Assertions.assertArrayEquals(expected, out.toByteArray())
        }
    }

    private fun createBlock(sha256: String, startPos: Long, size: Int): TBlockNode {
        return TBlockNode(
            createdBy = UT_USER,
            createdDate = LocalDateTime.now(),
            nodeFullPath = "",
            nodeSha256 = "sha256",
            startPos = startPos,
            sha256 = sha256,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            size = size,
            isDeleted = false
        )
    }
}
