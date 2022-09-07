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

package com.tencent.bkrepo.analyst.pojo.response

import com.tencent.bkrepo.common.analysis.pojo.scanner.Level

data class ScanQualityResponse(
    val critical: Long?,
    val high: Long?,
    val medium: Long?,
    val low: Long?,
    val forbidScanUnFinished: Boolean,
    val forbidQualityUnPass: Boolean
) {

    companion object {
        fun create(map: Map<String, Any>) = ScanQualityResponse(
            critical = map[Level.CRITICAL.levelName] as? Long,
            high = map[Level.HIGH.levelName] as? Long,
            medium = map[Level.MEDIUM.levelName] as? Long,
            low = map[Level.LOW.levelName] as? Long,
            forbidScanUnFinished = map[ScanQualityResponse::forbidScanUnFinished.name] as? Boolean ?: false,
            forbidQualityUnPass = map[ScanQualityResponse::forbidQualityUnPass.name] as? Boolean ?: false
        )
    }
}
