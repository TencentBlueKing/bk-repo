/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class OverlayRangeUtilsTest {
    @Test
    fun overlayBySeqTest() {
        val block1 = RegionResource("b1", 10, 20, 0, 20)
        val block2 = RegionResource("b2", 20, 30, 0, 30)
        val block3 = RegionResource("b3", 30, 40, 0, 40)
        val blocks = listOf(block1, block2, block3)
        val newList = OverlayRangeUtils.build(blocks, Range.full(100))
        Assertions.assertEquals(5, newList.size)
        val exceptBlock1 = RegionResource(RegionResource.ZERO_RESOURCE, 0, 100, 0, 10)
        val exceptBlock2 = RegionResource("b1", 10, 20, 0, 10)
        val exceptBlock3 = RegionResource("b2", 20, 30, 0, 10)
        val exceptBlock4 = RegionResource("b3", 30, 40, 0, 40)
        val exceptBlock5 = RegionResource(RegionResource.ZERO_RESOURCE, 70, 100, 70, 30)
        Assertions.assertEquals(exceptBlock1, newList[0])
        Assertions.assertEquals(exceptBlock2, newList[1])
        Assertions.assertEquals(exceptBlock3, newList[2])
        Assertions.assertEquals(exceptBlock4, newList[3])
        Assertions.assertEquals(exceptBlock5, newList[4])
    }

    @Test
    fun overlayByNoSeqTest() {
        val block1 = RegionResource("b1", 10, 20, 0, 20)
        val block2 = RegionResource("b2", 5, 30, 0, 30)
        val block3 = RegionResource("b3", 40, 40, 0, 40)
        val blocks = listOf(block1, block2, block3)
        val newList = OverlayRangeUtils.build(blocks, Range.full(100))
        Assertions.assertEquals(5, newList.size)
        val exceptBlock1 = RegionResource(RegionResource.ZERO_RESOURCE, 0, 100, 0, 5)
        val exceptBlock2 = RegionResource("b2", 5, 30, 0, 30)
        val exceptBlock3 = RegionResource(RegionResource.ZERO_RESOURCE, 35, 100, 35, 5)
        val exceptBlock4 = RegionResource("b3", 40, 40, 0, 40)
        val exceptBlock5 = RegionResource(RegionResource.ZERO_RESOURCE, 80, 100, 80, 20)
        Assertions.assertEquals(exceptBlock1, newList[0])
        Assertions.assertEquals(exceptBlock2, newList[1])
        Assertions.assertEquals(exceptBlock3, newList[2])
        Assertions.assertEquals(exceptBlock4, newList[3])
        Assertions.assertEquals(exceptBlock5, newList[4])
    }

    @Test
    fun overlayOnRightEdgeTest() {
        val block1 = RegionResource("b1", 10, 20, 0, 20)
        val block2 = RegionResource("b2", 20, 10, 0, 10)
        val blocks = listOf(block1, block2)
        val newList = OverlayRangeUtils.build(blocks, Range.full(100))
        Assertions.assertEquals(4, newList.size)
        val exceptBlock1 = RegionResource(RegionResource.ZERO_RESOURCE, 0, 100, 0, 10)
        val exceptBlock2 = RegionResource("b1", 10, 20, 0, 10)
        val exceptBlock3 = RegionResource("b2", 20, 10, 0, 10)
        val exceptBlock4 = RegionResource(RegionResource.ZERO_RESOURCE, 30, 100, 30, 70)
        Assertions.assertEquals(exceptBlock1, newList[0])
        Assertions.assertEquals(exceptBlock2, newList[1])
        Assertions.assertEquals(exceptBlock3, newList[2])
        Assertions.assertEquals(exceptBlock4, newList[3])
    }

    @Test
    fun overlayOnLeftEdgeTest() {
        val block1 = RegionResource("b1", 10, 20, 0, 20)
        val block2 = RegionResource("b2", 10, 5, 0, 5)
        val blocks = listOf(block1, block2)
        val newList = OverlayRangeUtils.build(blocks, Range.full(100))
        Assertions.assertEquals(4, newList.size)
        val exceptBlock1 = RegionResource(RegionResource.ZERO_RESOURCE, 0, 100, 0, 10)
        val exceptBlock2 = RegionResource("b2", 10, 5, 0, 5)
        val exceptBlock3 = RegionResource("b1", 15, 20, 5, 15)
        val exceptBlock4 = RegionResource(RegionResource.ZERO_RESOURCE, 30, 100, 30, 70)
        Assertions.assertEquals(exceptBlock1, newList[0])
        Assertions.assertEquals(exceptBlock2, newList[1])
        Assertions.assertEquals(exceptBlock3, newList[2])
        Assertions.assertEquals(exceptBlock4, newList[3])
    }

    @Test
    fun overlayFullTest() {
        val block1 = RegionResource("b1", 0, 100, 0, 100)
        val blocks = listOf(block1)
        val newList = OverlayRangeUtils.build(blocks, Range.full(100))
        Assertions.assertEquals(1, newList.size)
        val exceptBlock1 = RegionResource("b1", 0, 100, 0, 100)
        Assertions.assertEquals(exceptBlock1, newList[0])
    }

    @Test
    fun overlayPartialTest() {
        val block1 = RegionResource("b1", 0, 100, 0, 100)
        val blocks = listOf(block1)
        val newList = OverlayRangeUtils.build(blocks, Range(10, 13, 100))
        Assertions.assertEquals(1, newList.size)
        val exceptBlock1 = RegionResource("b1", 0, 100, 10, 4)
        Assertions.assertEquals(exceptBlock1, newList[0])
    }

    @Test
    fun holeFileTest() {
        val list = OverlayRangeUtils.build(emptyList(), Range.full(100))
        Assertions.assertEquals(1, list.size)
        val exceptBlock1 = RegionResource(RegionResource.ZERO_RESOURCE, 0, 100, 0, 100)
        Assertions.assertEquals(exceptBlock1, list[0])
    }
}
