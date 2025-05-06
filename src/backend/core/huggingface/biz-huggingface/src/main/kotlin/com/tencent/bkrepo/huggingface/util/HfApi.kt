/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bkrepo.common.api.constant.HttpHeaders.ACCEPT_ENCODING
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.huggingface.constants.ERROR_CODE_HEADER
import com.tencent.bkrepo.huggingface.constants.ERROR_MSG_HEADER
import com.tencent.bkrepo.huggingface.exception.HfApiException
import com.tencent.bkrepo.huggingface.pojo.DatasetInfo
import com.tencent.bkrepo.huggingface.pojo.ModelInfo
import okhttp3.Request
import okhttp3.Response
import org.springframework.beans.factory.BeanFactory
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component

@Component
class HfApi(
    private val beanFactory: BeanFactory,
) {

    init {
        Companion.beanFactory = beanFactory
    }

    companion object {
        private lateinit var beanFactory: BeanFactory
        private val httpClient by lazy {
            HttpClientBuilderFactory.create(beanFactory = beanFactory)
                .addInterceptor(RedirectInterceptor())
                .followRedirects(false).build()
        }

        fun modelInfo(endpoint: String, token: String, repoId: String): ModelInfo {
            val url = "$endpoint/api/models/$repoId"
            val request = Request.Builder().url(url)
                .apply { if (token.isNotBlank()) header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .build()
            httpClient.newCall(request).execute().use {
                throwExceptionWhenFailed(it)
                return it.body!!.string().readJsonString<ModelInfo>()
            }
        }

        fun datasetInfo(endpoint: String, token: String, repoId: String): DatasetInfo {
            val url = "$endpoint/api/datasets/$repoId"
            val request = Request.Builder().url(url)
                .apply { if (token.isNotBlank()) header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .build()
            httpClient.newCall(request).execute().use {
                throwExceptionWhenFailed(it)
                return it.body!!.string().readJsonString<DatasetInfo>()
            }
        }

        fun download(endpoint: String, token: String, artifactUri: String): Response {
            val url = "$endpoint$artifactUri"
            val method = HttpContextHolder.getRequestOrNull()?.method
            val request = Request.Builder().url(url)
                .apply { if (token.isNotBlank()) header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                // https://github.com/square/okhttp/issues/259#issuecomment-22056176
                // 使用默认gzip编码时，Content-Length可能被okhttp剥离
                .header(ACCEPT_ENCODING, "identity")
                .method(method ?: HttpMethod.GET.name, null)
                .build()
            val response = httpClient.newCall(request).execute()
            throwExceptionWhenFailed(response)
            return response
        }

        fun head(endpoint: String, token: String, artifactUri: String): Response {
            val url = "$endpoint$artifactUri"
            val request = Request.Builder().url(url)
                .apply { if (token.isNotBlank()) header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .head()
                .build()
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
    }
}
