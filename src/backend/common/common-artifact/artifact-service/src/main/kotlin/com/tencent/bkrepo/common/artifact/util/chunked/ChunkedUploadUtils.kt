/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.util.chunked

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode

object ChunkedUploadUtils {

    fun chunkedRequestCheck(
        contentLength: Int,
        range: String?,
        lengthOfAppendFile: Long
    ): RangeStatus {
        // 当range不存在或者length < 0时
        if (!validateValue(contentLength, range)) {
            return RangeStatus.ILLEGAL_RANGE
        }
        val (start, end) = getRangeInfo(range!!)
        // 当上传的长度和range内容不匹配时
        return if ((end - start) != (contentLength - 1).toLong()) {
            RangeStatus.ILLEGAL_RANGE
        } else {
            // 当追加的文件大小和range的起始大小一致时代表写入正常
            if (start == lengthOfAppendFile) {
                RangeStatus.READY_TO_APPEND
            } else if (start > lengthOfAppendFile) {
                // 当追加的文件大小比start小时，说明文件写入有误
                RangeStatus.ILLEGAL_RANGE
            } else {
                // 当追加的文件大小==end+1时，可能存在重试导致已经写入一次
                if (lengthOfAppendFile == end + 1) {
                    RangeStatus.ALREADY_APPENDED
                } else {
                    // 当追加的文件大小大于start时，并且不等于end+1时，文件已损坏
                    RangeStatus.ILLEGAL_RANGE
                }
            }
        }
    }

    /**
     * 从Content-Range头中解析出起始位置
     * Content-Range 类型为"start-end"
     */
    fun getRangeInfo(range: String): Pair<Long, Long> {
        if (range.isNullOrEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "range is empty!")
        }
        val values = range.split("-")
        if (values.isEmpty() || values.size < 2)
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, range)
        return Pair(values[0].toLong(), values[1].toLong())
    }

    private fun validateValue(contentLength: Int, range: String?): Boolean {
        return !(range.isNullOrEmpty() || contentLength < 0)
    }

    enum class RangeStatus {
        ILLEGAL_RANGE,
        ALREADY_APPENDED,
        READY_TO_APPEND;
    }
}