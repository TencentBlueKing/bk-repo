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

package com.tencent.com.bkrepo.fs.file

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.fs.server.file.FileRange
import com.tencent.bkrepo.fs.server.file.OverlayRangeUtils
import com.tencent.bkrepo.fs.server.model.TBlockNode
import com.tencent.com.bkrepo.fs.UT_PROJECT_ID
import com.tencent.com.bkrepo.fs.UT_REPO_NAME
import com.tencent.com.bkrepo.fs.UT_USER
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OverlayRangeUtilsTest {

    @DisplayName("测试只有块数据")
    @Test
    fun testOnlyBlockData() {
        val source = "lower"
        val block = createBlock("block", 0, 10)
        val range = Range(block.startPos, block.endPos, block.size.toLong())
        val fileRanges = OverlayRangeUtils.build(source, range, 0, listOf(block))
        Assertions.assertEquals(1, fileRanges.size)
        Assertions.assertEquals("block", fileRanges.first().source)
        Assertions.assertEquals(0, fileRanges.first().startPos)
        Assertions.assertEquals(9, fileRanges.first().endPos)
    }

    @DisplayName("测试只有文件数据")
    @Test
    fun testOnlyLowerData() {
        val source = "lower"
        val range = Range(0, 9, 10)
        val fileRanges = OverlayRangeUtils.build(source, range, 10, listOf())
        Assertions.assertEquals(1, fileRanges.size)
        Assertions.assertEquals("lower", fileRanges.first().source)
        Assertions.assertEquals(0, fileRanges.first().startPos)
        Assertions.assertEquals(9, fileRanges.first().endPos)
    }

    @DisplayName("测试块数据在开始位置")
    @Test
    fun testBlockDataOnTheStart() {
        val source = "lower"
        val block = createBlock("block", 0, 10)
        val range = Range(5, 15, 20)
        val fileRanges = OverlayRangeUtils.build(source, range, 20, listOf(block))
        Assertions.assertEquals(2, fileRanges.size)
        Assertions.assertEquals("block", fileRanges.first().source)
        Assertions.assertEquals(5, fileRanges.first().startPos)
        Assertions.assertEquals(9, fileRanges.first().endPos)

        Assertions.assertEquals("lower", fileRanges[1].source)
        Assertions.assertEquals(10, fileRanges[1].startPos)
        Assertions.assertEquals(15, fileRanges[1].endPos)
    }

    @DisplayName("测试块数据在结束位置")
    @Test
    fun testBlockDataOnTheEnd() {
        val source = "lower"
        val block = createBlock("block", 10, 10)
        val range = Range(5, 15, 20)
        val fileRanges = OverlayRangeUtils.build(source, range, 20, listOf(block))
        Assertions.assertEquals(2, fileRanges.size)

        Assertions.assertEquals("lower", fileRanges.first().source)
        Assertions.assertEquals(5, fileRanges.first().startPos)
        Assertions.assertEquals(9, fileRanges.first().endPos)

        Assertions.assertEquals("block", fileRanges[1].source)
        Assertions.assertEquals(0, fileRanges[1].startPos)
        Assertions.assertEquals(5, fileRanges[1].endPos)
    }

    @DisplayName("测试块数据在中间位置")
    @Test
    fun testBlockDataOnTheMid() {
        val source = "lower"
        val block = createBlock("block", 10, 5)
        val range = Range(5, 20, 30)
        val fileRanges = OverlayRangeUtils.build(source, range, 30, listOf(block))
        Assertions.assertEquals(3, fileRanges.size)

        Assertions.assertEquals("lower", fileRanges.first().source)
        Assertions.assertEquals(5, fileRanges.first().startPos)
        Assertions.assertEquals(9, fileRanges.first().endPos)

        Assertions.assertEquals("block", fileRanges[1].source)
        Assertions.assertEquals(0, fileRanges[1].startPos)
        Assertions.assertEquals(4, fileRanges[1].endPos)

        Assertions.assertEquals("lower", fileRanges[2].source)
        Assertions.assertEquals(15, fileRanges[2].startPos)
        Assertions.assertEquals(20, fileRanges[2].endPos)
    }

    @DisplayName("测试文件与块混合数据")
    @Test
    fun testMixData() {
        val source = "lower"
        val range = Range(5, 30, 40)
        val startBlock = createBlock("block", 0, 10)
        val midBlock = createBlock("block", 15, 10)
        val endBlock = createBlock("block", 28, 10)

        val fileRanges = OverlayRangeUtils.build(source, range, 40, listOf(startBlock, midBlock, endBlock))
        Assertions.assertEquals(5, fileRanges.size)

        Assertions.assertEquals("block", fileRanges.first().source)
        Assertions.assertEquals(5, fileRanges.first().startPos)
        Assertions.assertEquals(9, fileRanges.first().endPos)

        Assertions.assertEquals("lower", fileRanges[1].source)
        Assertions.assertEquals(10, fileRanges[1].startPos)
        Assertions.assertEquals(14, fileRanges[1].endPos)

        Assertions.assertEquals("block", fileRanges[2].source)
        Assertions.assertEquals(0, fileRanges[2].startPos)
        Assertions.assertEquals(9, fileRanges[2].endPos)

        Assertions.assertEquals("lower", fileRanges[3].source)
        Assertions.assertEquals(25, fileRanges[3].startPos)
        Assertions.assertEquals(27, fileRanges[3].endPos)

        Assertions.assertEquals("block", fileRanges[4].source)
        Assertions.assertEquals(0, fileRanges[4].startPos)
        Assertions.assertEquals(2, fileRanges[4].endPos)
    }

    @DisplayName("测试块大于节点长度")
    @Test
    fun testBlockAfterLower() {
        val source = "lower"
        val block = createBlock("block", 10, 20)
        val range = Range(5, 20, 30)
        val fileRanges = OverlayRangeUtils.build(source, range, 8, listOf(block))
        Assertions.assertEquals(3, fileRanges.size)

        Assertions.assertEquals("lower", fileRanges[0].source)
        Assertions.assertEquals(5, fileRanges[0].startPos)
        Assertions.assertEquals(7, fileRanges[0].endPos)

        Assertions.assertEquals(FileRange.ZERO_SOURCE, fileRanges[1].source)
        Assertions.assertEquals(0, fileRanges[1].startPos)
        Assertions.assertEquals(1, fileRanges[1].endPos)

        Assertions.assertEquals("block", fileRanges[2].source)
        Assertions.assertEquals(0, fileRanges[2].startPos)
        Assertions.assertEquals(10, fileRanges[2].endPos)
    }

    @DisplayName("测试不连续分块")
    @Test
    fun testNoSeqBlock() {
        val block1 = createBlock("block", 5, 5)
        val block2 = createBlock("block", 15, 5)

        val range = Range(0, 19, 20)
        val fileRanges = OverlayRangeUtils.build(FileRange.ZERO_SOURCE, range, 0, listOf(block1, block2))

        Assertions.assertEquals(4, fileRanges.size)

        Assertions.assertEquals(FileRange.ZERO_SOURCE, fileRanges[0].source)
        Assertions.assertEquals(0, fileRanges[0].startPos)
        Assertions.assertEquals(4, fileRanges[0].endPos)

        Assertions.assertEquals("block", fileRanges[1].source)
        Assertions.assertEquals(0, fileRanges[1].startPos)
        Assertions.assertEquals(4, fileRanges[1].endPos)

        Assertions.assertEquals(FileRange.ZERO_SOURCE, fileRanges[2].source)
        Assertions.assertEquals(0, fileRanges[2].startPos)
        Assertions.assertEquals(4, fileRanges[2].endPos)

        Assertions.assertEquals("block", fileRanges[1].source)
        Assertions.assertEquals(0, fileRanges[1].startPos)
        Assertions.assertEquals(4, fileRanges[1].endPos)
    }

    @DisplayName("测试超过上层的长度读取")
    @Test
    fun testExceedUpper() {
        val block1 = createBlock("block", 0, 2)
        val range = Range.full(10)
        val fileRanges = OverlayRangeUtils.build(range, listOf(block1))
        Assertions.assertEquals(2, fileRanges.size)

        Assertions.assertEquals("block", fileRanges[0].source)
        Assertions.assertEquals(0, fileRanges[0].startPos)
        Assertions.assertEquals(1, fileRanges[0].endPos)

        Assertions.assertEquals(FileRange.ZERO_SOURCE, fileRanges[1].source)
        Assertions.assertEquals(0, fileRanges[1].startPos)
        Assertions.assertEquals(7, fileRanges[1].endPos)
    }

    @DisplayName("测试超过下层数据的长度读取")
    @Test
    fun testExceedLower() {
        val range = Range.full(10)
        val fileRanges = OverlayRangeUtils.build("lower", range, 2, listOf())
        Assertions.assertEquals(2, fileRanges.size)

        Assertions.assertEquals("lower", fileRanges[0].source)
        Assertions.assertEquals(0, fileRanges[0].startPos)
        Assertions.assertEquals(1, fileRanges[0].endPos)

        Assertions.assertEquals(FileRange.ZERO_SOURCE, fileRanges[1].source)
        Assertions.assertEquals(0, fileRanges[1].startPos)
        Assertions.assertEquals(7, fileRanges[1].endPos)
    }

    private fun createBlock(digest: String, startPos: Long, size: Int): TBlockNode {
        return TBlockNode(
            createdBy = UT_USER,
            createdDate = LocalDateTime.now(),
            nodeFullPath = "",
            nodeSha256 = "sha256",
            startPos = startPos,
            sha256 = digest,
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            size = size,
            isDeleted = false
        )
    }
}
