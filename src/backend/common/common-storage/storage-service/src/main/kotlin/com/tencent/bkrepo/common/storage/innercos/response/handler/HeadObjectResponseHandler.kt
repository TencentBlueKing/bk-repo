/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.storage.innercos.response.handler

import com.tencent.bkrepo.common.storage.innercos.RESPONSE_CRC64
import com.tencent.bkrepo.common.storage.innercos.RESPONSE_SIZE
import com.tencent.bkrepo.common.storage.innercos.http.Headers
import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import com.tencent.bkrepo.common.storage.innercos.response.CosObject
import okhttp3.Response

class HeadObjectResponseHandler : HttpResponseHandler<CosObject>() {
    override fun handle(response: Response): CosObject {
        val eTag = response.header(Headers.ETAG)!!.trim('"')
        // 内部cos size是实际文件大小，腾讯云cos content-length是实际文件大小
        val length = response.header(RESPONSE_SIZE)?.toLong() ?: response.header(Headers.CONTENT_LENGTH)!!.toLong()
        val crc64ecma = response.header(RESPONSE_CRC64)
        return CosObject(eTag, null, length, crc64ecma)
    }

    override fun handle404(): CosObject {
        return CosObject(null, null, null, null)
    }
}
