/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.metrics

import org.springframework.util.unit.DataSize
import org.springframework.util.unit.DataUnit

enum class FileSizeLevel(private val range: LongRange) {
    MINI(
        LongRange(
            0L,
            DataSize.of(100, DataUnit.MEGABYTES).toBytes()
        )
    ),
    SMALL(
        LongRange(
            MINI.range.last,
            DataSize.of(200, DataUnit.MEGABYTES).toBytes()
        )
    ),
    NORMAL(
        LongRange(
            SMALL.range.last,
            DataSize.of(300, DataUnit.MEGABYTES).toBytes()
        )
    ),
    LARGE(
        LongRange(
            NORMAL.range.last,
            DataSize.of(500, DataUnit.MEGABYTES).toBytes()
        )
    ),
    HUGE(
        LongRange(
            LARGE.range.last,
            Long.MAX_VALUE
        )
    );

    companion object {
        fun fromSize(size: Long): FileSizeLevel {
            values().forEach {
                if (size in it.range) {
                    return it
                }
            }
            return HUGE
        }
    }
}
