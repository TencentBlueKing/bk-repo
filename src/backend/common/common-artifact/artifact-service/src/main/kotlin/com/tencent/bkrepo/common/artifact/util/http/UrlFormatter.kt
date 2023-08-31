/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.util.http

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.CharPool.QUESTION
import com.tencent.bkrepo.common.api.constant.CharPool.SLASH
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.HTTP
import com.tencent.bkrepo.common.api.constant.StringPool.HTTPS
import com.tencent.bkrepo.common.storage.innercos.retry
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

/**
 * Http URL 格式化工具类
 */
object UrlFormatter {

    /**
     * 格式化url
     */
    fun format(host: String, uri: String? = null, query: String? = null): String {
        val builder = StringBuilder(formatHost(host))
        if (!uri.isNullOrBlank()) {
            builder.append(SLASH).append(uri.trim(SLASH))
        }
        if (!query.isNullOrBlank()) {
            builder.append(QUESTION).append(query.trim(SLASH))
        }
        return builder.toString()
    }

    /**
     * 格式化host
     * http://xxx.com/// -> http://xxx.com
     * xxx.com -> http://xxx.com
     */
    fun formatHost(value: String): String {
        var host = value.trim().trimEnd(SLASH)
        if (!host.startsWith(HTTP) && !host.startsWith(HTTPS)) {
            host = HTTP + host
        }
        return host
    }

    /**
     * 格式化url
     */
    @Throws(IllegalArgumentException::class)
    fun formatUrl(value: String): String {
        var url = value.trim()
        if (url.isBlank()) {
            throw IllegalArgumentException("Url should not be blank")
        }
        if (!url.startsWith(HTTP) && !url.startsWith(HTTPS)) {
            url = HTTP + url
        }
        return url
    }


    /**
     * 拼接url
     */
    fun buildUrl(
        url: String,
        path: String = StringPool.EMPTY,
        params: String = StringPool.EMPTY,
    ): String {
        if (url.isBlank())
            throw IllegalArgumentException("Url should not be blank")
        val newUrl = addProtocol(url.trim().trimEnd(SLASH))
        val baseUrl = URL(newUrl, newUrl.path)
        val builder = StringBuilder(baseUrl.toString().trimEnd(SLASH))
        if (path.isNotBlank()) {
            builder.append(SLASH).append(path.trimStart(SLASH))
        }
        if (!newUrl.query.isNullOrEmpty()) {
            builder.append(QUESTION).append(newUrl.query)
        }
        return addParams(builder.toString(), params)
    }

    fun addParams(url: String, params: String): String {
        val baseUrl = URL(url)
        val builder = StringBuilder(baseUrl.toString())

        if (params.isNotEmpty()) {
            if (builder.contains(QUESTION)) {
                builder.append(CharPool.AND).append(params)
            } else {
                builder.append(QUESTION).append(params)
            }
        }
        return builder.toString()
    }

    /**
     * 当没有protocol时进行添加
     */
    fun addProtocol(registry: String): URL {
        try {
            return URL(registry)
        } catch (ignore: MalformedURLException) {
        }
        return addProtocolToHost(registry)
    }

    /**
     * 针对url如果没传protocol， 则默认以https请求发送；
     * 如果http请求无法访问，则以http发送
     */
    private fun addProtocolToHost(registry: String): URL {
        val url = try {
            URL("$HTTPS$registry")
        } catch (ignore: MalformedURLException) {
            throw IllegalArgumentException("Check your input url!")
        }
        return try {
            retry(times = 3, delayInSeconds = 1) {
                validateHttpsProtocol(url)
                url
            }
        } catch (ignore: Exception) {
            URL(url.toString().replaceFirst("^https".toRegex(), "http"))
        }
    }

    /**
     * 验证registry是否支持https
     */
    private fun validateHttpsProtocol(url: URL): Boolean {
        return try {
            val http: HttpURLConnection = url.openConnection() as HttpURLConnection
            http.instanceFollowRedirects = false
            http.responseCode
            http.disconnect()
            true
        } catch (e: Exception) {
            throw e
        }
    }
}
