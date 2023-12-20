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

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import java.lang.Long.min
import java.util.LinkedList
import kotlin.math.max

/**
 * 处理range overlay的工具
 * upper覆盖lower，间隙部分补0
 * */
object OverlayRangeUtils {

    fun build(blocks: List<RegionResource>, range: Range): List<RegionResource> {
        val total = range.total
        require(total != null)
        val initialBlock = RegionResource(RegionResource.ZERO_RESOURCE, 0, total, 0, total)
        val list = LinkedList<RegionResource>()
        list.add(initialBlock)
        // 完整文件分布
        blocks.forEach {
            insert(it, list, total)
        }
        // 范围内的文件分布
        val newList = LinkedList<RegionResource>()
        list.forEach {
            // 分块在范围内
            if (it.endPos >= range.start && it.pos <= range.end) {
                // 当range的结束位置在前时
                val len1 = min(range.end - it.pos + 1, range.length)
                // 当分块结束位置在前时
                val len2 = min(it.endPos - range.start + 1, it.len)
                val part = RegionResource(
                    it.digest,
                    it.pos,
                    it.size,
                    max(range.start - it.pos + it.off, it.off),
                    min(len1, len2),
                )
                newList.add(part)
            }
        }
        return newList
    }

    private fun insert(block: RegionResource, list: LinkedList<RegionResource>, length: Long) {
        for ((index, res) in list.withIndex()) {
            // 在分块内部
            if (block.pos >= res.pos && block.endPos <= res.endPos) {
                // 第一部分
                val part1 = RegionResource(res.digest, res.pos, res.size, res.off, block.pos - res.pos)
                // 第二部分
                val part2 = RegionResource(block.digest, block.pos, block.size, 0, block.size)
                // 第三部分
                val part3 = RegionResource(
                    res.digest,
                    block.endPos + 1,
                    res.size,
                    res.off + block.endPos - res.pos + 1,
                    res.endPos - block.endPos,
                )
                list.removeAt(index)
                val needAdd = listOf(part1, part2, part3)
                addBlocks(index, needAdd, list)
                return
            }
            if (block.pos >= res.pos && block.pos <= res.endPos) {
                val part1 = RegionResource(res.digest, res.pos, res.size, 0, block.pos - res.pos)
                // 最后一块
                if (list.lastIndex == index) {
                    val part2 = RegionResource(block.digest, block.pos, block.size, 0, length - block.pos)
                    list.removeAt(index)
                    val needAdd = listOf(part1, part2)
                    addBlocks(index, needAdd, list)
                    return
                }
                val part2 = RegionResource(block.digest, block.pos, block.size, 0, block.size)
                val nextIndex = index + 1
                while (nextIndex <= list.lastIndex && list[nextIndex].endPos <= block.endPos) {
                    list.removeAt(nextIndex)
                }
                if (nextIndex > list.lastIndex) {
                    list.removeAt(index)
                    val needAdd = listOf(part1, part2)
                    addBlocks(index, needAdd, list)
                } else {
                    val nextRes = list[nextIndex]
                    val part3 = RegionResource(
                        nextRes.digest,
                        block.endPos + 1,
                        nextRes.size,
                        nextRes.off + block.endPos - nextRes.pos + 1,
                        nextRes.endPos - block.endPos,
                    )
                    list.removeAt(index)
                    list.removeAt(index)
                    val needAdd = listOf(part1, part2, part3)
                    addBlocks(index, needAdd, list)
                }
                return
            }
        }
    }

    private fun addBlocks(
        index: Int,
        adds: List<RegionResource>,
        list: LinkedList<RegionResource>,
    ) {
        var insertIndex = index
        adds.forEach {
            if (it.len > 0) {
                list.add(insertIndex++, it)
            }
        }
    }
}
