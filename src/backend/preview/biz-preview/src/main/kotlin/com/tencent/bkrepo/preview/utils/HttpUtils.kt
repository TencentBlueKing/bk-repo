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

package com.tencent.bkrepo.preview.utils

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import io.micrometer.observation.ObservationRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Recover
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit

@Component
class HttpUtils(
    private val registry: ObservationRegistry,
    private val ssrfGuard: SsrfGuard
) {
    private val okHttpClient: OkHttpClient by lazy {
        HttpClientBuilderFactory
            .create(registry = registry)
            .readTimeout(72000, TimeUnit.MILLISECONDS)
            .connectTimeout(10000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            // 关闭自动重定向，改为手动处理，以便在每次跟随前进行 SSRF 校验
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /**
     * 下载远程文件。
     * 关闭 OkHttp 自动重定向，手动处理 3xx，并在每次跟随前对目标 URL 重新进行 SSRF 校验，
     * 防止外部服务器通过 302 等重定向指向内网地址绕过初始校验。
     */
    @Retryable(Exception::class, maxAttempts = 3, backoff = Backoff(delay = 5 * 1000, multiplier = 1.0))
    fun downloadHttpFile(url: URL): Response {
        // 初始 URL 的 SSRF 校验
        var currentUrl = ssrfGuard.validate(url.toString())
        var response: Response? = null
        var redirects = 0
        while (true) {
            response?.closeQuietly()
            val request = createRequest(currentUrl)
            response = okHttpClient.newCall(request).execute()
            val code = response.code
            // 仅对标准 3xx 重定向进行处理
            if (code in 300..399 && code != 304) {
                if (redirects >= MAX_REDIRECTS) {
                    response.closeQuietly()
                    throw Exception("Too many redirects for url: [$url]")
                }
                val location = response.header(HttpHeaders.LOCATION)
                if (location.isNullOrBlank()) {
                    // 3xx 但没有 Location 头，按失败处理
                    response.closeQuietly()
                    throw Exception("Redirect without Location header, url: [$currentUrl]")
                }
                val nextUrl = resolveLocation(currentUrl, location)
                // 每次重定向前重新走 SSRF 校验，防止被 302 绕过
                currentUrl = ssrfGuard.validate(nextUrl.toString())
                redirects++
                continue
            }
            if (!checkResponse(response)) {
                response.closeQuietly()
                throw Exception("request http url: [$url] failed")
            }
            return response
        }
    }

    /**
     * 将 Location 头（可能是绝对或相对 URL）解析为绝对 URL。
     */
    private fun resolveLocation(base: URL, location: String): URL {
        return try {
            URI(location).let { uri ->
                if (uri.isAbsolute) uri.toURL() else URI(base.toString()).resolve(uri).toURL()
            }
        } catch (e: Exception) {
            throw Exception("Invalid redirect location: [$location]", e)
        }
    }

    private fun Response.closeQuietly() {
        try {
            this.close()
        } catch (ignored: Exception) {
        }
    }

    private fun createRequest(url: URL): Request {
        return Request.Builder()
            .url(url)
            .addHeader(HttpHeaders.ACCEPT, MediaTypes.APPLICATION_OCTET_STREAM)
            .build()
    }

    @Recover
    fun recover(exception: Exception, url: String, headers: Map<String, String> = emptyMap()): Response {
        logger.error("recover, retry http send url: [$url], headers:[$headers] failed, exception: $exception")
        throw ArtifactNotFoundException("http send url: [$url] failed.")
    }

    private fun checkResponse(response: Response): Boolean {
        if (!response.isSuccessful) {
            logger.warn("Download file from remote failed: [${response.code}]")
            return false
        }
        return true
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HttpUtils::class.java)
        private const val MAX_REDIRECTS = 5
    }
}
