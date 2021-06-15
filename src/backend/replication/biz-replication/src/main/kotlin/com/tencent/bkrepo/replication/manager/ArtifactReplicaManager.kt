/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.manager

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.replication.exception.ReplicaFileFailedException
import com.tencent.bkrepo.replication.job.ReplicaContext
import com.tencent.bkrepo.replication.pojo.request.RequestBodyUtil
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import org.springframework.stereotype.Component
import java.net.URLEncoder

@Component
class ArtifactReplicaManager(
    private val localDataManager: LocalDataManager
) {
    fun replicaFile(context: ReplicaContext, request: NodeCreateRequest) {
        with(context) {
            // 查询文件
            val localRepoDetail = currentRepoDetail.localRepoDetail
            val inputStream = localDataManager.getBlobData(request.sha256!!, request.size!!, localRepoDetail)
            val rateLimitInputStream = inputStream.rateLimit(replicationProperties.rateLimit.toBytes())
            val fileRequestBody = RequestBodyUtil.create(MEDIA_TYPE_STREAM, rateLimitInputStream, request.size!!)
            val fullPath = URLEncoder.encode(request.fullPath, "utf-8")
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fullPath, fileRequestBody)
                .addFormDataPart("size", request.size.toString())
                .addFormDataPart("sha256", request.sha256!!)
                .addFormDataPart("md5", request.md5!!)
                .addFormDataPart("userId", request.operator)
            request.metadata?.forEach { (key, value) ->
                builder.addFormDataPart("metadata[$key]", value as String)
            }
            val url = "$remoteUrl/replica/file/${request.projectId}/${request.repoName}/${request.fullPath}"
            val requestBody = builder.build()
            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            val response = httpClient.newCall(httpRequest).execute()
            response.use {
                if (!response.isSuccessful) {
                    val responseString = response.body()?.string() ?: StringPool.UNKNOWN
                    throw ReplicaFileFailedException("Failed to replica node, response message: $responseString")
                }
            }
        }
    }

    companion object {
        private val MEDIA_TYPE_STREAM = MediaType.parse(MediaTypes.APPLICATION_OCTET_STREAM)
    }
}
