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

package com.tencent.bkrepo.generic.util

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.generic.constant.CHUNKED_UPLOAD_UUID
import com.tencent.bkrepo.generic.pojo.ChunkedResponseProperty
import javax.servlet.http.HttpServletResponse

object ChunkedRequestUtil {
    /**
     * 生成chunked upload的响应体
     */
    fun uploadResponse(
        responseProperty: ChunkedResponseProperty,
        response: HttpServletResponse = HttpContextHolder.getResponse()
    ) {
        with(responseProperty) {
            response.status = status!!.value

            uuid?.let {
                response.addHeader(CHUNKED_UPLOAD_UUID, uuid)
            }
            contentLength?.let {
                response.addHeader(HttpHeaders.CONTENT_LENGTH, contentLength.toString())
            }
            size?.let {
                response.addHeader(HttpHeaders.RANGE, "0-${size!! - 1}")
            }
        }
    }
}