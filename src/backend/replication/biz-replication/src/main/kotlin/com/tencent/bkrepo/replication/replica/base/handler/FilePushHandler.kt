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

package com.tencent.bkrepo.replication.replica.base.handler

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.BLOB_PUSH_URI
import com.tencent.bkrepo.replication.constant.BOLBS_UPLOAD_FIRST_STEP_URL
import com.tencent.bkrepo.replication.constant.FILE
import com.tencent.bkrepo.replication.constant.OCI_BLOBS_UPLOAD_FIRST_STEP_URL
import com.tencent.bkrepo.replication.constant.PUSH_WITH_CHUNKED
import com.tencent.bkrepo.replication.constant.REPOSITORY_INFO
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.replication.constant.STORAGE_KEY
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.blob.RequestTag
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeType
import com.tencent.bkrepo.replication.pojo.remote.DefaultHandlerResult
import com.tencent.bkrepo.replication.pojo.remote.RequestProperty
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.replica.base.context.FilePushContext
import com.tencent.bkrepo.replication.replica.base.context.ReplicaContext
import com.tencent.bkrepo.replication.util.HttpUtils
import com.tencent.bkrepo.replication.util.StreamRequestBody
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.ByteString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMethod
import java.net.URL

@Component
class FilePushHandler(
    val localDataManager: LocalDataManager,
    val replicationProperties: ReplicationProperties
) {


    fun blobPush(
        filePushContext: FilePushContext,
        pushType: String = replicationProperties.pushType
    ) : Boolean {
        return when (pushType) {
            PUSH_WITH_CHUNKED -> {
                pushFileInChunks(
                    filePushContext = filePushContext
                )
            }
            else -> {
                pushBlob(
                    filePushContext = filePushContext
                )
                true
            }
        }
    }

    /**
     * 上传 file
     */
    private fun pushFileInChunks(filePushContext: FilePushContext): Boolean {
        with(filePushContext) {
            val clusterUrl = context.cluster.url
            val clusterName = context.cluster.name
            logger.info(
                "Will try to push $name file $sha256 " +
                    "in repo ${context.localProjectId}|${context.localRepo} to remote cluster $clusterName."
            )
            logger.info("Will try to obtain uuid from remote cluster $clusterName for blob $name|$digest")

            var sessionIdHandlerResult = processSessionIdHandler(filePushContext)
            if (!sessionIdHandlerResult.isSuccess) {
                return false
            }
            val (sha256, size) = getBlobSha256AndSize(
                sha256, size, digest, context.localProjectId, context.localRepoName
            )
            logger.info(
                "Will try to push file with ${sessionIdHandlerResult.location} " +
                    "in chunked upload way to remote cluster $clusterUrl for blob $name|$sha256"
            )
            // 需要将大文件进行分块上传
            var chunkedUploadResult = try {
                processFileChunkUpload(
                    size = size,
                    sha256 = sha256,
                    filePushContext = filePushContext,
                    location = buildRequestUrl(clusterUrl, sessionIdHandlerResult.location)
                )
            } catch (e: Exception) {
                // 针对mirrors不支持将blob分成多块上传，返回404 BLOB_UPLOAD_INVALID
                // 针对csighub不支持将blob分成多块上传，报java.net.SocketException: Broken pipe (Write failed)
                // 针对部分tencentyun.com分块上传报okhttp3.internal.http2.StreamResetException: stream was reset: NO_ERROR
                // 抛出异常后，都进行降级，直接使用单个文件上传进行降级重试
                if (context.remoteCluster.type == ClusterNodeType.REMOTE) {
                    DefaultHandlerResult(isFailure = true)
                } else {
                    throw e
                }
            } ?: return false
            if (chunkedUploadResult.isFailure) {
                sessionIdHandlerResult = processSessionIdHandler(filePushContext)
                if (!sessionIdHandlerResult.isSuccess) {
                    return false
                }
                chunkedUploadResult = processBlobUploadWithSingleChunk(
                    size = size,
                    sha256 = sha256,
                    location = buildRequestUrl(clusterUrl, sessionIdHandlerResult.location),
                    filePushContext = filePushContext
                )
            }

            if (!chunkedUploadResult.isSuccess) return false
            logger.info(
                "The file $name|$sha256 is pushed " +
                    "and will try to send a completed request with ${chunkedUploadResult.location}."
            )
            val sessionCloseHandlerResult = processSessionCloseHandler(
                location = buildRequestUrl(clusterUrl, chunkedUploadResult.location),
                filePushContext = filePushContext,
                sha256 = sha256
            )
            return sessionCloseHandlerResult.isSuccess
        }
    }

    /**
     * 构件file上传处理器
     * 上传file文件step1: post获取sessionID
     */
    private fun processSessionIdHandler(
        filePushContext: FilePushContext
        ): DefaultHandlerResult {
        with(filePushContext) {
            var (postUrl, params) = when (context.remoteCluster.type) {
                ClusterNodeType.REMOTE -> {
                    val temp: String? = context.remoteRepo?.storageCredentials?.key?.let {
                        "$SHA256=$sha256&$STORAGE_KEY=$it"
                    }
                    Pair(OCI_BLOBS_UPLOAD_FIRST_STEP_URL.format(name), temp)
                }
                else -> {
                    Pair(BOLBS_UPLOAD_FIRST_STEP_URL, null)
                }
            }
            postUrl = buildUrl(context.cluster.url, postUrl, context)
            val postBody: RequestBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(), StringPool.EMPTY
            )
            val property = RequestProperty(
                requestBody = postBody,
                authorizationCode = token,
                requestMethod = RequestMethod.POST,
                requestUrl = buildRequestUrl(filePushContext.context.cluster.url, postUrl),
                params = params
            )
            return DefaultHandler.process(
                httpClient = httpClient,
                responseType = responseType,
                requestProperty = property
            )
        }
    }

    /**
     * 构件file上传处理器
     * 上传file文件step2: patch分块上传
     */
    private fun processFileChunkUpload(
        size: Long,
        sha256: String,
        location: String?,
        filePushContext: FilePushContext
    ): DefaultHandlerResult? {
        var startPosition: Long = 0
        var chunkedHandlerResult: DefaultHandlerResult? = null
        val (params, ignoredFailureCode) = when (filePushContext.context.remoteCluster.type) {
            ClusterNodeType.REMOTE -> {
                val params = filePushContext.context.remoteRepo?.storageCredentials?.key?.let {
                    "$SHA256=$sha256&$STORAGE_KEY=$it"
                }
                Pair(params, emptyList())
            }
            else -> {
                Pair(null, listOf(HttpStatus.NOT_FOUND.value))
            }
        }
        while (startPosition < size) {
            val offset = size - startPosition - replicationProperties.chunkedSize
            val byteCount: Long = if (offset < 0) {
                (size - startPosition)
            } else {
                replicationProperties.chunkedSize
            }
            val contentRange = "$startPosition-${startPosition + byteCount - 1}"
            logger.info(
                "${Thread.currentThread().name} start is $startPosition, " +
                    "size is $size, byteCount is $byteCount contentRange is $contentRange"
            )
            val range = Range(startPosition, startPosition + byteCount - 1, size)
            val input = localDataManager.loadInputStreamByRange(
                sha256, range, filePushContext.context.localProjectId, filePushContext.context.localRepoName
            )
            val patchBody: RequestBody = RequestBody.create(
                MediaTypes.APPLICATION_OCTET_STREAM.toMediaTypeOrNull(), input.readBytes()
            )
            val patchHeader = Headers.Builder()
                .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
                .add(HttpHeaders.CONTENT_RANGE, contentRange)
                .add(HttpHeaders.CONTENT_LENGTH, "$byteCount")
                .build()
            val property = RequestProperty(
                requestBody = patchBody,
                authorizationCode = filePushContext.token,
                requestMethod = RequestMethod.PATCH,
                headers = patchHeader,
                requestUrl = buildRequestUrl(filePushContext.context.cluster.url, location),
                requestTag = buildRequestTag(filePushContext.context, sha256 + range, byteCount),
                params = params
            )
            chunkedHandlerResult = DefaultHandler.process(
                httpClient = filePushContext.httpClient,
                ignoredFailureCode = ignoredFailureCode,
                responseType = filePushContext.responseType,
                requestProperty = property
            )
            if (!chunkedHandlerResult.isSuccess) {
                return chunkedHandlerResult
            }
            startPosition += byteCount
        }
        return chunkedHandlerResult
    }


    /**
     * 构件file上传处理器
     * 上传file文件最后一步: put上传
     */
    private fun processSessionCloseHandler(
        location: String?,
        sha256: String,
        filePushContext: FilePushContext
    ): DefaultHandlerResult {
        val putBody: RequestBody = RequestBody.create(
            null, ByteString.EMPTY
        )
        val params = when (filePushContext.context.remoteCluster.type) {
            ClusterNodeType.REMOTE -> {
                filePushContext.context.remoteRepo?.storageCredentials?.key?.let {
                    "$SHA256=$sha256&$STORAGE_KEY=$it"
                }
            }
            else -> {
                "digest=${filePushContext.digest!!}"
            }
        }
        val putHeader = Headers.Builder()
            .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
            .add(HttpHeaders.CONTENT_LENGTH, "0")
            .build()
        val property = RequestProperty(
            requestBody = putBody,
            params = params,
            authorizationCode = filePushContext.token,
            requestMethod = RequestMethod.PUT,
            headers = putHeader,
            requestUrl = buildRequestUrl(filePushContext.context.cluster.url, location),
        )
        return DefaultHandler.process(
            httpClient = filePushContext.httpClient,
            responseType = filePushContext.responseType,
            requestProperty = property
        )
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件step2: patch分块上传
     * 针对部分registry不支持将blob分成多块上传，将blob文件整块上传
     */
    private fun processBlobUploadWithSingleChunk(
        size: Long,
        sha256: String,
        location: String?,
        filePushContext: FilePushContext
    ): DefaultHandlerResult {
        with(filePushContext) {
            logger.info("Will upload blob $sha256 in a single patch request")
            val params = when (filePushContext.context.remoteCluster.type) {
                ClusterNodeType.REMOTE -> {
                     filePushContext.context.remoteRepo?.storageCredentials?.key?.let {
                        "$SHA256=$sha256&$STORAGE_KEY=$it"
                    }
                }
                else -> {
                    null
                }
            }
            val patchBody = StreamRequestBody(
                localDataManager.loadInputStream(sha256, size, context.localProjectId, context.localRepoName),
                size
            )
            val patchHeader = Headers.Builder()
                .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
                .add(HttpHeaders.CONTENT_RANGE, "0-${0 + size - 1}")
                .add(REPOSITORY_INFO, "${context.localProjectId}|${context.localRepoName}")
                .add(SHA256, sha256)
                .add(HttpHeaders.CONTENT_LENGTH, "$size")
                .build()
            val property = RequestProperty(
                requestBody = patchBody,
                authorizationCode = token,
                requestMethod = RequestMethod.PATCH,
                headers = patchHeader,
                requestUrl = buildRequestUrl(context.cluster.url, location),
                params = params,
                requestTag = buildRequestTag(context, sha256, size)
            )
            return DefaultHandler.process(
                httpClient = httpClient,
                responseType = responseType,
                requestProperty = property
            )
        }
    }


    /**
     * 推送blob文件数据到远程集群
     */
    private fun pushBlob(
        filePushContext: FilePushContext
    ) {
        with(filePushContext) {
            logger.info("File $sha256 will be pushed using the default way.")
            val artifactInputStream = localDataManager.getBlobData(sha256!!, size!!, context.localRepo)
            val rateLimitInputStream = artifactInputStream.rateLimit(
                replicationProperties.rateLimit.toBytes()
            )
            val storageKey = context.remoteRepo?.storageCredentials?.key
            val requestTag = buildRequestTag(context, sha256, size)
            val pushUrl =  buildRequestUrl(context.cluster.url, BLOB_PUSH_URI)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(FILE, sha256, StreamRequestBody(rateLimitInputStream, size))
                .addFormDataPart(SHA256, sha256).apply {
                    storageKey?.let { addFormDataPart(STORAGE_KEY, it) }
                }.build()
            logger.info("The request will be sent for file sha256 [$sha256].")
            val httpRequest = Request.Builder()
                .url(pushUrl!!)
                .post(requestBody)
                .tag(RequestTag::class.java, requestTag)
                .build()
            httpClient.newCall(httpRequest).execute().use {
                check(it.isSuccessful) { "Failed to replica file: ${it.body?.string()}" }
            }
        }
    }

    fun getBlobSha256AndSize(
        sha256: String?,
        size: Long?,
        digest: String?,
        projectId: String,
        repoName: String
    ): Pair<String, Long> {
        sha256?.let {
            return Pair(sha256, size!!)
        }
        val realSha256 = digest!!.split(":").last()
        val realSize = localDataManager.getNodeBySha256(projectId, repoName, realSha256)
        return Pair(realSha256, realSize)
    }

    /**
     * 获取上传blob的location
     * 如返回location不带host，需要补充完整
     */
    fun buildRequestUrl(
        url: String,
        location: String?
    ): String? {
        return location?.let {
            try {
                URL(location)
                location
            } catch (e: Exception) {
                val baseUrl = URL(url)
                val host = URL(baseUrl.protocol, baseUrl.host, StringPool.EMPTY).toString()
                HttpUtils.buildUrl(host, location.removePrefix("/"))
            }
        }
    }

    /**
     * 拼接url
     */
    fun buildUrl(
        url: String,
        path: String,
        context: ReplicaContext,
        params: String = StringPool.EMPTY
    ): String {
        val baseUrl = URL(url)

        val suffixUrl = when(context.remoteCluster.type) {
            ClusterNodeType.REMOTE ->  {
                URL(baseUrl, "/v2" + baseUrl.path).toString()
            }
            else -> URL(baseUrl, baseUrl.path).toString()
        }
        return HttpUtils.buildUrl(suffixUrl, path, params)
    }

    private fun buildRequestTag(
        context: ReplicaContext,
        key: String,
        size: Long
    ): RequestTag? {
        return when(context.task.replicaType) {
            ReplicaType.RUN_ONCE -> RequestTag(
                task = context.task,
                key = key,
                size = size
            )
            else -> null
        }
    }


    companion object {
        private val logger = LoggerFactory.getLogger(FilePushHandler::class.java)

    }
}