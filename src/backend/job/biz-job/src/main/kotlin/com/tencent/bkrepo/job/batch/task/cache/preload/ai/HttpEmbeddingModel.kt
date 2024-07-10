/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch.task.cache.preload.ai

import com.tencent.bkrepo.common.api.constant.BEARER_AUTH_PREFIX
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration

class HttpEmbeddingModel(
    private val properties: AiProperties,
) : EmbeddingModel {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(Duration.ofSeconds(15L))
            .writeTimeout(Duration.ofSeconds(15L))
            .connectTimeout(Duration.ofSeconds(15L))
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun embed(text: String): List<Float> {
        return embed(listOf(text)).first()
    }

    override fun embed(texts: List<String>): List<List<Float>> {
        val body = EmbeddingRequest(texts).toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType())
        val req = buildReq("/embeddings").post(body).build()
        val res = client.newCall(req).execute()
        if (res.isSuccessful) {
            return res.body!!.byteStream().readJsonString<EmbeddingResponse>().data
        } else {
            val message = res.body?.string()
            throw RuntimeException("embedding failed: $message, code[${res.code}]")
        }
    }

    private fun buildReq(api: String) = Request.Builder()
        .url("${properties.embeddingServiceUrl}$api")
        .header(HttpHeaders.AUTHORIZATION, "$BEARER_AUTH_PREFIX ${properties.embeddingServiceToken}")

    private data class EmbeddingRequest(
        val input: List<String>
    )

    private data class EmbeddingResponse(
        val data: List<List<Float>>
    )
}
