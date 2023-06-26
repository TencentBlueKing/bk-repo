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

package com.tencent.bkrepo.analyst.pojo.request

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.analysis.pojo.scanner.Level

data class ScanQualityUpdateRequest(
    val critical: Long? = null,
    val high: Long? = null,
    val medium: Long? = null,
    val low: Long? = null,
    val forbidScanUnFinished: Boolean? = null,
    val forbidQualityUnPass: Boolean? = null
) {
    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        Level.values().forEach { level ->
            val methodName = "get${level.levelName.capitalize()}"
            val method = ScanQualityUpdateRequest::class.java.getDeclaredMethod(methodName)

            val redLine = method.invoke(this) as Long?
            if (redLine != null && redLine < 0) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, level.levelName)
            }
            map[level.levelName] = redLine
        }
        map[ScanQualityUpdateRequest::forbidScanUnFinished.name] = this.forbidScanUnFinished
        map[ScanQualityUpdateRequest::forbidQualityUnPass.name] = this.forbidQualityUnPass
        return map
    }
}
