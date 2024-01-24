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

package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter.addParams
import com.tencent.bkrepo.replication.pojo.blob.RequestTag
import com.tencent.bkrepo.replication.pojo.remote.RequestProperty
import okhttp3.Request
import org.springframework.web.bind.annotation.RequestMethod
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object HttpUtils {
    /**
     * 封装请求
     */
    fun wrapperRequest(
        requestProperty: RequestProperty
    ): Request {
        with(requestProperty) {
            val url = params?.let {
                addParams(url = requestUrl!!, params = params!!)
            } ?: requestUrl!!
            var builder = Request.Builder()
                .url(url)
            headers?.let { builder = builder.headers(headers!!) }
            authorizationCode?.let {
                builder = builder.header(HttpHeaders.AUTHORIZATION, authorizationCode!!)
            }
            requestTag?.let {
                builder = builder.tag(RequestTag::class.java, requestTag)
            }
            return when (requestMethod) {
                RequestMethod.POST -> builder.post(requestBody!!)
                RequestMethod.HEAD -> builder.head()
                RequestMethod.PATCH -> builder.patch(requestBody!!)
                RequestMethod.PUT -> builder.put(requestBody!!)
                RequestMethod.GET -> builder.get()
                else -> throw RuntimeException("Unknown http request method")
            }.build()
        }
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
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = false
            val responseCode = connection.responseCode
            val result = responseCode in 200..399
            connection.disconnect()
            result
        } catch (exception: IOException) {
            throw exception
        }
    }

    /**
     * 从Content-Range头中解析出起始位置
     */
    fun getRangeInfo(range: String): Pair<Long, Long> {
        val values = range.split("-")
        return Pair(values[0].toLong(), values[1].toLong())
    }
}
