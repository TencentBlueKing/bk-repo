/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.huggingface.util

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Request
import okhttp3.Response

/**
 * hf下载文件时，可能会重定向，导致X-REPO-COMMIT响应头丢失
 */
class RedirectInterceptor : Interceptor {
    override fun intercept(chain: Chain): Response {
        val request: Request = chain.request()
        val response: Response = chain.proceed(request)

        if (!response.isRedirect) {
            return response
        }

        val headersBeforeRedirect: Headers = response.headers

        val location = response.header(HttpHeaders.LOCATION) ?: return response
        response.close()
        val newRequest: Request = request.newBuilder().url(location).build()
        val newResponse: Response = chain.proceed(newRequest)

        val responseBuilder = newResponse.newBuilder()
        for (name in headersBeforeRedirect.names()) {
            if (name !in newResponse.headers.names()) {
                responseBuilder.header(name, headersBeforeRedirect[name].toString())
            }
        }

        return responseBuilder.build()
    }
}
