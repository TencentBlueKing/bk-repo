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

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.tencent.bkrepo.common.api.constant.HttpHeaders.ACCEPT_ENCODING
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.repository.remote.buildOkHttpClient
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.huggingface.constants.ERROR_CODE_HEADER
import com.tencent.bkrepo.huggingface.constants.ERROR_MSG_HEADER
import com.tencent.bkrepo.huggingface.exception.HfApiException
import com.tencent.bkrepo.huggingface.pojo.DatasetInfo
import com.tencent.bkrepo.huggingface.pojo.ModelInfo
import io.micrometer.observation.ObservationRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class HfApi(
    private val registry: ObservationRegistry
) {

    init {
        Companion.registry = registry
    }

    companion object {
        private lateinit var registry: ObservationRegistry
        private val logger = LoggerFactory.getLogger(HfApi::class.java)

        private val httpClientCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(
                CacheLoader.from<RemoteConfiguration, OkHttpClient> { buildHttpClient(it) }
            )

        fun modelInfo(configuration: RemoteConfiguration, repoId: String, revision: String?): ModelInfo {
            val httpClient = httpClientCache.get(configuration)
            val url = "${configuration.url}/api/models/$repoId" + revision?.let { "/revision/$it" }.orEmpty()
            logger.info("fetch model info from $url")
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use {
                throwExceptionWhenFailed(it)
                return it.body!!.string().readJsonString<ModelInfo>()
            }
        }

        fun datasetInfo(configuration: RemoteConfiguration, repoId: String, revision: String?): DatasetInfo {
            val httpClient = httpClientCache.get(configuration)
            val url = "${configuration.url}/api/datasets/$repoId" + revision?.let { "/revision/$it" }.orEmpty()
            logger.info("fetch dataset info from $url")
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use {
                throwExceptionWhenFailed(it)
                return it.body!!.string().readJsonString<DatasetInfo>()
            }
        }

        fun download(configuration: RemoteConfiguration, artifactUri: String, type: String?): Response {
            val httpClient = httpClientCache.get(configuration)
            val url = "${configuration.url.trim('/')}${type?.let { "/" + it + "s" }.orEmpty()}$artifactUri"
            val method = HttpContextHolder.getRequestOrNull()?.method
            logger.info("download file: $method $url")
            val request = Request.Builder().url(url)
                // https://github.com/square/okhttp/issues/259#issuecomment-22056176
                // 使用默认gzip编码时，Content-Length可能被okhttp剥离
                .header(ACCEPT_ENCODING, "identity")
                .method(method ?: HttpMethod.GET.name(), null)
                .build()
            val response = httpClient.newCall(request).execute()
            throwExceptionWhenFailed(response)
            return response
        }

        fun head(configuration: RemoteConfiguration, artifactUri: String): Response {
            val httpClient = httpClientCache.get(configuration)
            val url = "${configuration.url}$artifactUri"
            logger.info("HEAD request url: $url")
            val request = Request.Builder().url(url).head().build()
            val response = httpClient.newCall(request).execute()
            throwExceptionWhenFailed(response)
            return response
        }


        private fun throwExceptionWhenFailed(response: Response) {
            if (!response.isSuccessful) {
                throw HfApiException(
                    status = response.code,
                    errorCode = response.header(ERROR_CODE_HEADER).orEmpty(),
                    errorMessage = response.header(ERROR_MSG_HEADER).orEmpty()
                )
            }
        }

        private fun buildHttpClient(configuration: RemoteConfiguration): OkHttpClient {
            // 使用bearer token认证
            configuration.credentials.username = null
            configuration.credentials.credentialKey = "Bearer ${configuration.credentials.password}"
            return buildOkHttpClient(configuration, registry = registry)
                .addInterceptor(RedirectInterceptor())
                .followRedirects(false)
                .build()
        }
    }
}
