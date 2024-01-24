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

package com.tencent.bkrepo.replication.replica.replicator.base.remote

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.OCI_BLOBS_UPLOAD_FIRST_STEP_URL
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.remote.DefaultHandlerResult
import com.tencent.bkrepo.replication.replica.context.FilePushContext
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.replicator.base.ArtifactReplicationHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URL


@Component
class RemoteClusterArtifactReplicationHandler(
    localDataManager: LocalDataManager,
    replicationProperties: ReplicationProperties
) : ArtifactReplicationHandler(localDataManager, replicationProperties) {


    override fun getBlobFileInfo(
        filePushContext: FilePushContext
    ): FileInfo {
        val realSha256 = filePushContext.digest!!.split(":").last()
        return localDataManager.getNodeBySha256(
            filePushContext.context.localProjectId,
            filePushContext.context.localRepoName,
            realSha256
        )
    }

    override fun handleChunkUploadException(e: Exception): DefaultHandlerResult {
        // 针对部分镜像源不支持将blob分成多块上传，返回以下几种异常：
        // 1 404 BLOB_UPLOAD_INVALID
        // 2 java.net.SocketException: Broken pipe (Write failed)
        // 3 okhttp3.internal.http2.StreamResetException: stream was reset: NO_ERROR
        // 针对这几类情况都进行降级，直接使用单个文件上传进行降级重试
        return DefaultHandlerResult(isFailure = true)
    }

    override fun buildSessionRequestInfo(filePushContext: FilePushContext) : Pair<String, String?> {
        with(filePushContext) {
            val postUrl = buildUrl(context.cluster.url, OCI_BLOBS_UPLOAD_FIRST_STEP_URL.format(name), context)
            return Pair(postUrl, null)
        }
    }

    override fun buildBlobUploadWithSingleChunkRequestParam(
        sha256: String,
        filePushContext: FilePushContext
    ): String? {
        return null
    }

    override fun buildChunkUploadRequestInfo(
        sha256: String,
        filePushContext: FilePushContext
    ) : Pair<String?, List<Int>>{
        return Pair(null, listOf(HttpStatus.NOT_FOUND.value))
    }

    override fun buildSessionCloseRequestParam(
        fileInfo: FileInfo,
        filePushContext: FilePushContext
    ) : String {
        return "digest=${filePushContext.digest!!}"
    }


    override fun buildUrl(
        url: String,
        path: String,
        context: ReplicaContext,
        params: String
    ): String {
        val baseUrl = URL(url)
        val suffixUrl = URL(baseUrl, "/v2" + baseUrl.path).toString()
        return UrlFormatter.buildUrl(suffixUrl, path, params)
    }
    companion object {
        private val logger = LoggerFactory.getLogger(RemoteClusterArtifactReplicationHandler::class.java)
    }
}