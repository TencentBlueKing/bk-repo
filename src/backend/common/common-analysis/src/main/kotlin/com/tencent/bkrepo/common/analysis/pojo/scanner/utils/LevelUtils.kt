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

package com.tencent.bkrepo.common.analysis.pojo.scanner.utils

import com.tencent.bkrepo.common.analysis.pojo.scanner.Level

/**
 * 标准化等级
 */
fun normalizedLevel(level: String): String {
    return when (level.toLowerCase()) {
        "危急", "严重", "critical" -> Level.CRITICAL.levelName
        "高危", "high" -> Level.HIGH.levelName
        "中危", "mid", "middle", "medium" -> Level.MEDIUM.levelName
        "低危", "low" -> Level.LOW.levelName
        else -> Level.CRITICAL.levelName
    }
}

fun levelOf(levelName: String) = when (levelName.toLowerCase()) {
    Level.CRITICAL.levelName -> Level.CRITICAL.level
    Level.HIGH.levelName -> Level.HIGH.level
    Level.MEDIUM.levelName -> Level.MEDIUM.level
    Level.LOW.levelName -> Level.LOW.level
    else -> Int.MAX_VALUE
}
