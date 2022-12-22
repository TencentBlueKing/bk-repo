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

package com.tencent.bkrepo.fs.server.file

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.fs.server.model.TBlockNode
import kotlin.math.min
import org.slf4j.LoggerFactory

/**
 * 处理range overlay的工具
 * upper覆盖lower，间隙部分补0
 * */
object OverlayRangeUtils {

    /**
     * 根据块数据构建文件范围
     * */
    fun build(range: Range, blocks: List<TBlockNode>): List<FileRange> {
        return build(FileRange.ZERO_SOURCE, range, 0, blocks)
    }

    /**
     * 根据下层和上层资源构建文件范围，upper会覆盖lower，空的地方补0
     * */
    fun build(lower: String, range: Range, lowerSize: Long, uppers: List<TBlockNode>): List<FileRange> {
        val uppersIt = uppers.iterator()
        val lowerEndPos = lowerSize - 1
        val fileRanges = mutableListOf<FileRange>()
        if (!uppersIt.hasNext()) {
            // 没有分块
            fileRanges.appendLower(range.end, range.start, lower, lowerEndPos)
            if (!validate(range, fileRanges)) {
                throw RuntimeException("")
            }
            return fileRanges
        }
        var upper = uppersIt.next()
        // 下一次需要写入的位置
        var pos = range.start
        while (pos <= range.end) {
            val upperEndPos = min(range.end, upper.endPos)
            if (pos < upper.startPos) {
                if (pos <= lowerEndPos) {
                    // 还在节点内部，考虑下层数据
                    fileRanges.appendLower(upper.startPos - 1, pos, lower, lowerEndPos)
                } else {
                    // 在节点外部 补充块与块之间的空隙
                    val emptyRange = FileRange(FileRange.ZERO_SOURCE, 0, upper.startPos - pos - 1)
                    fileRanges.add(emptyRange)
                }
                val upperRange = FileRange(upper.sha256, 0, upperEndPos - upper.startPos)
                fileRanges.add(upperRange)
            } else {
                val upperRange = FileRange(upper.sha256, pos - upper.startPos, upperEndPos - upper.startPos)
                fileRanges.add(upperRange)
            }
            pos = upperEndPos + 1
            if (pos > range.end) {
                // 已经移动至末尾
                break
            }
            if (uppersIt.hasNext()) {
                upper = uppersIt.next()
            } else {
                // 后续没有文件块
                fileRanges.appendLower(range.end, pos, lower, lowerEndPos)
                break
            }
        }
        if (logger.isDebugEnabled) {
            val formatBuilder = StringBuilder("target[${range.start},${range.end}] split to: ")
            fileRanges.forEach {
                formatBuilder.append("${it.source}[${it.startPos},${it.endPos}] ,")
            }
            formatBuilder.setLength(formatBuilder.length - 1)
            logger.debug(formatBuilder.toString())
        }
        if (!validate(range, fileRanges)) {
            throw IllegalStateException("Overlay range build error.")
        }
        return fileRanges
    }

    /**
     * 添加下层数据,如果目标结束位置超过下层数据范围，则补充0
     * @param targetEndPos 需补齐的数据结束位置
     * @param pos 下层数据起始位置
     * @param lowerSource 下层资源
     * @param lowerEndPos 下层资源结束位置
     * */
    private fun MutableList<FileRange>.appendLower(
        targetEndPos: Long,
        pos: Long,
        lowerSource: String,
        lowerEndPos: Long
    ) {
        if (lowerEndPos < 0) {
            val emptyRange = FileRange(FileRange.ZERO_SOURCE, 0, targetEndPos - pos)
            this.add(emptyRange)
            return
        }
        val endPos = min(targetEndPos, lowerEndPos)
        val lowerRange = FileRange(lowerSource, pos, endPos)
        this.add(lowerRange)
        if (targetEndPos > endPos) {
            val emptyRange = FileRange(FileRange.ZERO_SOURCE, 0, targetEndPos - endPos - 1)
            this.add(emptyRange)
        }
    }

    private fun validate(range: Range, fileRanges: List<FileRange>): Boolean {
        var total = 0L
        fileRanges.forEach {
            total += it.endPos - it.startPos + 1
        }
        return range.length == total
    }

    private val logger = LoggerFactory.getLogger(OverlayRangeUtils::class.java)
}
