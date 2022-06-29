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

package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.replication.constant.BODY
import com.tencent.bkrepo.replication.constant.GET_METHOD
import com.tencent.bkrepo.replication.constant.HEADERS
import com.tencent.bkrepo.replication.constant.HEAD_METHOD
import com.tencent.bkrepo.replication.constant.METHOD
import com.tencent.bkrepo.replication.constant.PARAMS
import com.tencent.bkrepo.replication.constant.PATCH_METHOD
import com.tencent.bkrepo.replication.constant.POST_METHOD
import com.tencent.bkrepo.replication.constant.PUT_METHOD
import com.tencent.bkrepo.replication.constant.URL
import com.tencent.bkrepo.replication.replica.external.exception.RepoDeployException
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object HttpUtils {

    /**
     * 封装请求
     */
    fun wrapperRequest(
        token: String? = null,
        extraMap: Map<String, Any>? = null,
        originalMap: Map<String, Any?> = emptyMap(),
    ): Request {
        val map = mutableMapOf<String, Any?>()
        map.putAll(originalMap)
        if (extraMap != null) {
            map.putAll(extraMap)
        }
        val url = if (map[PARAMS] != null) {
            builderUrl(url = map[URL].toString(), params = map[PARAMS].toString())
        } else {
            map[URL].toString()
        }
        var builder = Request.Builder()
            .url(url)
        map[HEADERS]?.let {
            builder = builder.headers(map[HEADERS] as Headers)
        }
        token?.let {
            builder = builder.header(HttpHeaders.AUTHORIZATION, token)
        }
        return when (map[METHOD]) {
            POST_METHOD -> builder.post(map[BODY] as RequestBody)
            HEAD_METHOD -> builder.head()
            PATCH_METHOD -> builder.patch(map[BODY] as RequestBody)
            PUT_METHOD -> builder.put(map[BODY] as RequestBody)
            GET_METHOD -> builder.get()
            else -> throw RepoDeployException("Unknown http request method")
        }.build()
    }

    /**
     * 拼接url
     */
    fun builderUrl(
        url: String,
        path: String = StringPool.EMPTY,
        params: String = StringPool.EMPTY,
    ): String {
        return UrlFormatter.format(url, path, params)
    }

    /**
     * Pings a HTTP URL. This effectively sends a HEAD request and returns `true` if the response code is in
     * the 200-399 range.
     * @param url The HTTP URL to be pinged.
     * @param timeout The timeout in millis for both the connection timeout and the response read timeout. Note that
     * the total timeout is effectively two times the given timeout.
     * @return `true` if the given HTTP URL has returned response code 200-399 on a HEAD request within the
     * given timeout, otherwise `false`.
     */
    fun pingURL(url: String, timeout: Int): Boolean {
        var url = url
        // Otherwise an exception may be thrown on invalid SSL certificates.
        url = url.replaceFirst("^https".toRegex(), "http")
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            connection.requestMethod = "HEAD"
            val responseCode = connection.responseCode
            responseCode in 200..399
        } catch (exception: IOException) {
            false
        }
    }
}
