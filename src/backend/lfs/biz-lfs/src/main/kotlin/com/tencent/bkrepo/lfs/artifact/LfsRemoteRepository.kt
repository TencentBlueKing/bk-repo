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

package com.tencent.bkrepo.lfs.artifact

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.innercos.http.toRequestBody
import com.tencent.bkrepo.lfs.constant.BASIC_TRANSFER
import com.tencent.bkrepo.lfs.constant.DOWNLOAD_OPERATION
import com.tencent.bkrepo.lfs.constant.HEADER_BATCH_AUTHORIZATION
import com.tencent.bkrepo.lfs.pojo.BatchRequest
import com.tencent.bkrepo.lfs.pojo.BatchResponse
import com.tencent.bkrepo.lfs.pojo.LfsObject
import com.tencent.bkrepo.lfs.utils.OidUtils
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.springframework.stereotype.Component

@Component
class LfsRemoteRepository : RemoteRepository() {

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        val httpClient = createHttpClient(context.getRemoteConfiguration(), false).newBuilder()
            .addInterceptor {
                val userAgent = HeaderUtils.getHeader(HttpHeaders.USER_AGENT)
                val request = it.request().newBuilder()
                    .apply { userAgent?.let { header(HttpHeaders.USER_AGENT, userAgent) } }
                    .build()
                it.proceed(request)
            }.build()
        val lfsObject = getLfsObject(httpClient, context)
        return getCacheArtifactResource(context) ?: downloadLfs(httpClient, context, lfsObject)
    }

    fun onDownloadResponse(context: ArtifactDownloadContext, response: Response, size: Long): ArtifactResource {
        val artifactFile = createTempFile(response.body!!)
        val artifactStream = artifactFile.getInputStream().artifactStream(Range.full(size))
        val node = cacheArtifactFile(context, artifactFile)
        return ArtifactResource(artifactStream, context.artifactInfo.getResponseName(), node, ArtifactChannel.LOCAL)
    }


    /**
     *  object batch请求，获取LfsObject
     */
    private fun getLfsObject(httpClient: OkHttpClient, context: ArtifactDownloadContext): LfsObject {
        val request = HttpContextHolder.getRequest()
        val batchRequest = BatchRequest(
            operation = DOWNLOAD_OPERATION,
            transfers = listOf(BASIC_TRANSFER),
            ref = mapOf("name" to request.getParameter("ref").toString()),
            objects = listOf(
                LfsObject(
                    oid = OidUtils.convertToOid(context.artifactInfo.getArtifactFullPath()),
                    size = request.getParameter("size").toLong()
                )
            )
        )
        val requestBody = batchRequest.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType())
        val config = context.getRemoteConfiguration()
        val url = "${config.url.removePrefix(StringPool.SLASH)}/info/lfs/objects/batch"
        val authHeader = HeaderUtils.getHeader(HEADER_BATCH_AUTHORIZATION).orEmpty()
        val request2 = Request.Builder().url(url)
            .header(HttpHeaders.AUTHORIZATION, authHeader).post(requestBody).build()
        httpClient.newCall(request2).execute().use {
            if (!it.isSuccessful) {
                throw RemoteErrorCodeException("batch", it.code, it.body!!.string())
            }
            val batchResponse = it.body!!.string().readJsonString<BatchResponse>()
            return batchResponse.objects.first()
        }
    }

    private fun downloadLfs(
        httpClient: OkHttpClient,
        context: ArtifactDownloadContext,
        lfsObject: LfsObject
    ): ArtifactResource? {
        val actionDetail = lfsObject.actions!![DOWNLOAD_OPERATION]!!
        with(actionDetail) {
            val request = Request.Builder().url(href).headers(header.toHeaders()).get().build()
            httpClient.newCall(request).execute().use {
                return if (checkResponse(it)) {
                    onDownloadResponse(context, it, lfsObject.size)
                } else null
            }
        }
    }
}
