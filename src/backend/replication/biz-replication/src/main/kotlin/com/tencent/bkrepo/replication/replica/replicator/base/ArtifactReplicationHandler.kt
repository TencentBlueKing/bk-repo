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

package com.tencent.bkrepo.replication.replica.replicator.base

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.CHUNKED_UPLOAD
import com.tencent.bkrepo.replication.constant.MD5
import com.tencent.bkrepo.replication.constant.REPOSITORY_INFO
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.replication.constant.SIZE
import com.tencent.bkrepo.replication.enums.WayOfPushArtifact
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.blob.RequestTag
import com.tencent.bkrepo.replication.pojo.remote.DefaultHandlerResult
import com.tencent.bkrepo.replication.pojo.remote.RequestProperty
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.replica.context.FilePushContext
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.util.DefaultHandler
import com.tencent.bkrepo.replication.util.StreamRequestBody
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.ByteString
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.RequestMethod
import java.net.URL

abstract class ArtifactReplicationHandler(
    val localDataManager: LocalDataManager,
    val replicationProperties: ReplicationProperties
) {


    open fun blobPush(
        filePushContext: FilePushContext,
        pushType: String = WayOfPushArtifact.PUSH_WITH_CHUNKED.value,
        downGrade: Boolean = false
    ) : Boolean {
        return pushFileInChunks(filePushContext)
    }

    /**
     * 上传 file
     */
    private fun pushFileInChunks(filePushContext: FilePushContext): Boolean {
        with(filePushContext) {
            val clusterUrl = context.cluster.url
            val clusterName = context.cluster.name
            logger.info(
                "Will try to push $name file $digest or $sha256 " +
                    "in repo ${context.localProjectId}|${context.localRepo} to remote cluster $clusterName."
            )
            logger.info("Will try to obtain uuid from remote cluster $clusterName for blob $name|$digest")

            var sessionIdHandlerResult = processSessionIdHandler(filePushContext)
            if (!sessionIdHandlerResult.isSuccess) {
                return false
            }
            val fileInfo = getBlobFileInfo(filePushContext)
            logger.info(
                "Will try to push file with ${sessionIdHandlerResult.location} " +
                    "in chunked upload way to remote cluster $clusterUrl for blob $name|${fileInfo.sha256}"
            )
            // 需要将大文件进行分块上传
            var chunkedUploadResult = try {
                processFileChunkUpload(
                    fileInfo = fileInfo,
                    filePushContext = filePushContext,
                    location = buildRequestUrl(clusterUrl, sessionIdHandlerResult.location)
                )
            } catch (e: Exception) {
                handleChunkUploadException(e)
            } ?: return false
            if (chunkedUploadResult.isFailure) {
                sessionIdHandlerResult = processSessionIdHandler(filePushContext)
                if (!sessionIdHandlerResult.isSuccess) {
                    return false
                }
                chunkedUploadResult = processBlobUploadWithSingleChunk(
                    fileInfo = fileInfo,
                    location = buildRequestUrl(clusterUrl, sessionIdHandlerResult.location),
                    filePushContext = filePushContext
                )
            }

            if (!chunkedUploadResult.isSuccess) return false
            logger.info(
                "The file $name|${fileInfo.sha256} is pushed " +
                    "and will try to send a completed request with ${chunkedUploadResult.location}."
            )
            val sessionCloseHandlerResult = processSessionCloseHandler(
                location = buildRequestUrl(clusterUrl, chunkedUploadResult.location),
                filePushContext = filePushContext,
                fileInfo = fileInfo
            )
            return sessionCloseHandlerResult.isSuccess
        }
    }

    open fun handleChunkUploadException(e: Exception): DefaultHandlerResult {
        throw e
    }

    /**
     * 构件file上传处理器
     * 上传file文件step1: post获取sessionID
     */
    private fun processSessionIdHandler(
        filePushContext: FilePushContext
        ): DefaultHandlerResult {
        with(filePushContext) {
            val (postUrl, params) = buildSessionRequestInfo(filePushContext)
            val postBody: RequestBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(), StringPool.EMPTY
            )
            val headers = Headers.Builder()
                .add(CHUNKED_UPLOAD, CHUNKED_UPLOAD)
                .build()
            val property = RequestProperty(
                requestBody = postBody,
                authorizationCode = token,
                requestMethod = RequestMethod.POST,
                requestUrl = buildRequestUrl(filePushContext.context.cluster.url, postUrl),
                headers = headers,
                params = params
            )
            return DefaultHandler.process(
                httpClient = httpClient,
                responseType = responseType,
                requestProperty = property
            )
        }
    }

    open fun buildSessionRequestInfo(filePushContext: FilePushContext) : Pair<String, String?> {
        return Pair(StringPool.EMPTY, null)
    }

    /**
     * 构件file上传处理器
     * 上传file文件step2: patch分块上传
     */
    private fun processFileChunkUpload(
        fileInfo: FileInfo,
        location: String?,
        filePushContext: FilePushContext
    ): DefaultHandlerResult? {
        var startPosition: Long = 0
        var chunkedHandlerResult: DefaultHandlerResult? = null
        val (params, ignoredFailureCode) = buildChunkUploadRequestInfo(fileInfo.sha256, filePushContext)
        while (startPosition < fileInfo.size) {
            val offset = fileInfo.size - startPosition - replicationProperties.chunkedSize
            val byteCount: Long = if (offset < 0) {
                (fileInfo.size - startPosition)
            } else {
                replicationProperties.chunkedSize
            }
            val contentRange = "$startPosition-${startPosition + byteCount - 1}"
            logger.info(
                "${Thread.currentThread().name} start is $startPosition, " +
                    "size is ${fileInfo.size}, byteCount is $byteCount contentRange is $contentRange"
            )
            val range = Range(startPosition, startPosition + byteCount - 1, fileInfo.size)
            val input = localDataManager.loadInputStreamByRange(
                fileInfo.sha256, range, filePushContext.context.localProjectId, filePushContext.context.localRepoName
            )
            val rateLimitInputStream = input.rateLimit(
                replicationProperties.rateLimit.toBytes()
            )
            val patchBody: RequestBody = StreamRequestBody(rateLimitInputStream, byteCount)
            val patchHeader = Headers.Builder()
                .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
                .add(HttpHeaders.CONTENT_RANGE, contentRange)
                .add(HttpHeaders.CONTENT_LENGTH, "$byteCount")
                .add(CHUNKED_UPLOAD, CHUNKED_UPLOAD)
                .add(
                    REPOSITORY_INFO,
                    "${filePushContext.context.localProjectId}|${filePushContext.context.localRepoName}"
                )
                .add(SHA256, fileInfo.sha256)
                .add(SIZE, fileInfo.size.toString())
                .add(MD5, fileInfo.md5)
                .build()
            val property = RequestProperty(
                requestBody = patchBody,
                authorizationCode = filePushContext.token,
                requestMethod = RequestMethod.PATCH,
                headers = patchHeader,
                requestUrl = location,
                requestTag = buildRequestTag(filePushContext.context, fileInfo.sha256 + range, byteCount),
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


    open fun buildChunkUploadRequestInfo(
        sha256: String,
        filePushContext: FilePushContext
    ) : Pair<String?, List<Int>>{
        return Pair(null, emptyList())
    }


    /**
     * 构件file上传处理器
     * 上传file文件最后一步: put上传
     */
    private fun processSessionCloseHandler(
        location: String?,
        fileInfo: FileInfo,
        filePushContext: FilePushContext
    ): DefaultHandlerResult {
        val putBody: RequestBody = RequestBody.create(
            null, ByteString.EMPTY
        )
        val params = buildSessionCloseRequestParam(fileInfo, filePushContext)
        val putHeader = Headers.Builder()
            .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
            .add(HttpHeaders.CONTENT_LENGTH, "0")
            .add(CHUNKED_UPLOAD, CHUNKED_UPLOAD)
            .build()
        val property = RequestProperty(
            requestBody = putBody,
            params = params,
            authorizationCode = filePushContext.token,
            requestMethod = RequestMethod.PUT,
            headers = putHeader,
            requestUrl = location,
        )
        return DefaultHandler.process(
            httpClient = filePushContext.httpClient,
            responseType = filePushContext.responseType,
            requestProperty = property
        )
    }

    open fun buildSessionCloseRequestParam(
        fileInfo: FileInfo,
        filePushContext: FilePushContext
    ) : String {
        return StringPool.EMPTY
    }

    /**
     * 构件blob上传处理器
     * 上传blob文件step2: patch分块上传
     * 针对部分registry不支持将blob分成多块上传，将blob文件整块上传
     */
    private fun processBlobUploadWithSingleChunk(
        fileInfo: FileInfo,
        location: String?,
        filePushContext: FilePushContext
    ): DefaultHandlerResult {
        with(filePushContext) {
            logger.info("Will upload blob ${fileInfo.sha256} in a single patch request")
            val params = buildBlobUploadWithSingleChunkRequestParam(fileInfo.sha256, filePushContext)
            val inputStream = localDataManager.loadInputStream(
                fileInfo.sha256, fileInfo.size, context.localProjectId, context.localRepoName
            )
            val rateLimitInputStream = inputStream.rateLimit(
                replicationProperties.rateLimit.toBytes()
            )
            val patchBody = StreamRequestBody(
                rateLimitInputStream,
                fileInfo.size
            )
            val patchHeader = Headers.Builder()
                .add(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_OCTET_STREAM)
                .add(HttpHeaders.CONTENT_RANGE, "0-${0 + fileInfo.size - 1}")
                .add(REPOSITORY_INFO, "${context.localProjectId}|${context.localRepoName}")
                .add(SHA256, fileInfo.sha256)
                .add(HttpHeaders.CONTENT_LENGTH, "$size")
                .add(CHUNKED_UPLOAD, CHUNKED_UPLOAD)
                .build()
            val property = RequestProperty(
                requestBody = patchBody,
                authorizationCode = token,
                requestMethod = RequestMethod.PATCH,
                headers = patchHeader,
                requestUrl = location,
                params = params,
                requestTag = buildRequestTag(context, fileInfo.sha256, fileInfo.size)
            )
            return DefaultHandler.process(
                httpClient = httpClient,
                responseType = responseType,
                requestProperty = property
            )
        }
    }

    open fun buildBlobUploadWithSingleChunkRequestParam(
        sha256: String,
        filePushContext: FilePushContext
    ) : String? {
        return null
    }



    abstract fun getBlobFileInfo(filePushContext: FilePushContext): FileInfo

    /**
     * 获取上传blob的location
     * 如返回location不带host，需要补充完整
     */
    private fun buildRequestUrl(
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
                UrlFormatter.buildUrl(host, location.removePrefix("/"))
            }
        }
    }

    /**
     * 拼接url
     */
    open fun buildUrl(
        url: String,
        path: String,
        context: ReplicaContext,
        params: String = StringPool.EMPTY
    ): String {
        val baseUrl = URL(url)
        val suffixUrl = URL(baseUrl, baseUrl.path).toString()
        return UrlFormatter.buildUrl(suffixUrl, path, params)
    }

    open fun buildRequestTag(
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
        private val logger = LoggerFactory.getLogger(ArtifactReplicationHandler::class.java)
    }
}
