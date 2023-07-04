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

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.fdtp.codec.DefaultFdtpHeaders
import com.tencent.bkrepo.fdtp.codec.FdtpResponseStatus
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.BLOB_PUSH_URI
import com.tencent.bkrepo.replication.constant.BOLBS_UPLOAD_FIRST_STEP_URL_STRING
import com.tencent.bkrepo.replication.constant.FILE
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.replication.constant.SIZE
import com.tencent.bkrepo.replication.constant.STORAGE_KEY
import com.tencent.bkrepo.replication.enums.WayOfPushArtifact
import com.tencent.bkrepo.replication.fdtp.FdtpAFTClientFactory
import com.tencent.bkrepo.replication.fdtp.FdtpServerProperties
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.blob.RequestTag
import com.tencent.bkrepo.replication.replica.base.context.FilePushContext
import com.tencent.bkrepo.replication.replica.base.impl.remote.exception.ArtifactPushException
import com.tencent.bkrepo.replication.replica.base.process.ProgressListener
import com.tencent.bkrepo.replication.util.StreamRequestBody
import io.netty.channel.ChannelProgressiveFuture
import io.netty.channel.ChannelProgressiveFutureListener
import java.net.ConnectException
import okhttp3.MultipartBody
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@RefreshScope
@Component
class ClusterArtifactReplicationHandler(
    localDataManager: LocalDataManager,
    replicationProperties: ReplicationProperties,
    val fdtpServerProperties: FdtpServerProperties,
    val listener: ProgressListener
) : ArtifactReplicationHandler(localDataManager, replicationProperties) {

    //不支持yaml list配置 https://github.com/spring-projects/spring-framework/issues/16381
    @Value("\${replication.chunkedRepos:}")
    private var chunkedRepos: List<String> = emptyList()

    @Value("\${replication.fdtpRepos:}")
    private var fdtpRepos: List<String> = emptyList()

    @Value("\${replication.httpRepos:}")
    private var httpRepos: List<String> = emptyList()

    override fun blobPush(
        filePushContext: FilePushContext,
        pushType: String,
        downGrade: Boolean
    ) : Boolean {
        val newType = filterRepoWithPushType(
            pushType, filePushContext.context.localProjectId, filePushContext.context.localRepoName, downGrade
        )
        return when (newType) {
            WayOfPushArtifact.PUSH_WITH_CHUNKED.value -> {
                super.blobPush(filePushContext, newType, downGrade)
            }
            WayOfPushArtifact.PUSH_WITH_FDTP.value -> {
                pushWithFdtp(filePushContext)
            }
            else -> {
                pushBlob(filePushContext)
                true
            }
        }
    }

    private fun filterRepoWithPushType(
        pushType: String, projectId: String, repoName: String, downGrade: Boolean
    ): String {
        if (downGrade) return pushType
        return if (filterProjectRepo(projectId, repoName, chunkedRepos)) {
            WayOfPushArtifact.PUSH_WITH_CHUNKED.value
        } else if (filterProjectRepo(projectId, repoName, fdtpRepos)) {
            return WayOfPushArtifact.PUSH_WITH_FDTP.value
        } else if (filterProjectRepo(projectId, repoName, httpRepos)) {
            return WayOfPushArtifact.PUSH_WITH_DEFAULT.value
        } else {
            return pushType
        }
    }

    override fun getBlobSha256AndSize(
        filePushContext: FilePushContext
    ): Pair<String, Long> {
        return Pair(filePushContext.sha256!!, filePushContext.size!!)
    }

    override fun buildSessionRequestInfo(filePushContext: FilePushContext) : Pair<String, String?> {
        with(filePushContext) {
            val url = BOLBS_UPLOAD_FIRST_STEP_URL_STRING.format(context.remoteProjectId, context.remoteRepoName)
            val postUrl = buildUrl(context.cluster.url, url, context)
            return Pair(postUrl, buildParams(sha256!!, filePushContext))
        }
    }

    override fun buildChunkUploadRequestInfo(
        sha256: String,
        filePushContext: FilePushContext
    ) : Pair<String?, List<Int>>{
        return Pair(buildParams(sha256, filePushContext), emptyList())
    }

    override fun buildSessionCloseRequestParam(
        sha256: String,
        filePushContext: FilePushContext
    ) : String {
        return buildParams(sha256, filePushContext)
    }


    override fun buildBlobUploadWithSingleChunkRequestParam(
        sha256: String,
        filePushContext: FilePushContext
    ) : String? {
        return buildParams(sha256, filePushContext)
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
            val pushUrl =  buildUrl(context.cluster.url, BLOB_PUSH_URI, context)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(FILE, sha256, StreamRequestBody(rateLimitInputStream, size))
                .addFormDataPart(SIZE, size.toString())
                .addFormDataPart(SHA256, sha256).apply {
                    storageKey?.let { addFormDataPart(STORAGE_KEY, it) }
                }.build()
            logger.info("The request will be sent for file sha256 [$sha256].")
            val httpRequest = Request.Builder()
                .url(pushUrl)
                .post(requestBody)
                .tag(RequestTag::class.java, requestTag)
                .build()
            httpClient.newCall(httpRequest).execute().use {
                check(it.isSuccessful) { "Failed to replica file: ${it.body?.string()}" }
            }
        }
    }

    /**
     * 使用fdtp推送blob文件数据到远程集群
     */
    private fun pushWithFdtp(filePushContext: FilePushContext): Boolean {
        with(filePushContext) {
            logger.info("File $sha256 will be pushed using the fdtp way.")
            val client = FdtpAFTClientFactory.createAFTClient(context.cluster, fdtpServerProperties.port)
            val artifactInputStream = localDataManager.getBlobData(sha256!!, size!!, context.localRepo)
            val rateLimitInputStream = artifactInputStream.rateLimit(
                replicationProperties.rateLimit.toBytes()
            )
            val storageKey = context.remoteRepo?.storageCredentials?.key
            val headers = DefaultFdtpHeaders()
            headers.add(SHA256, sha256)
            storageKey?.let { headers.add(STORAGE_KEY, storageKey) }
            try {
                val progressListener = object : ChannelProgressiveFutureListener {
                    private val tag = RequestTag(context.task, sha256, size)
                    private val progressListener: ProgressListener = listener
                    private var previous: Long = 0

                    @Throws(Exception::class)
                    override fun operationProgressed(future: ChannelProgressiveFuture?, progress: Long, total: Long) {
                        if (progress == previous) return
                        try {
                            progressListener.onProgress(tag.task, tag.key, progress - previous)
                        } catch (ignore: Exception) {
                        }
                        previous = progress
                    }

                    @Throws(Exception::class)
                    override fun operationComplete(future: ChannelProgressiveFuture) {
                        if (future.isSuccess) {
                            progressListener.onSuccess(tag.task)
                        } else {
                            progressListener.onFailed(tag.task, tag.key)
                        }
                    }
                }
                listener.onStart(context.task, sha256,0)

                val responsePromise = client.sendStream(rateLimitInputStream, headers, progressListener)

                val response = responsePromise.get(READ_TIME_OUT, TimeUnit.SECONDS)
                if (response.status == FdtpResponseStatus.OK){
                    return true
                } else {
                    val logMessage = "Error occurred while pushing file $sha256 " +
                        "with the fdtp way, error is ${response.status.reasonPhrase}"
                    logger.warn(logMessage)
                    throw ArtifactPushException(logMessage)
                }
            } catch (e: ConnectException) {
                // 当不支持fdtp方式进行传输时抛出异常，进行降级处理
                logger.warn(
                    "Error occurred while pushing file $sha256 with the fdtp way, errors is ${e.message}", e
                )
                throw ArtifactPushException(e.message.orEmpty(), HttpStatus.METHOD_NOT_ALLOWED.value)
            }
        }
    }

    private fun buildParams(
        sha256: String,
        filePushContext: FilePushContext
    ): String {
        val params = "$SHA256=$sha256"
        filePushContext.context.remoteRepo?.storageCredentials?.key?.let {
            "$params&$STORAGE_KEY=$it"
        }
        return params
    }

    /**
     * 只针对配置的仓库进行删除
     */
    private fun filterProjectRepo(projectId: String, repoName: String, includeRepositories: List<String>): Boolean {
        if (contains(StringPool.POUND, StringPool.POUND, includeRepositories)) {
            return true
        }
        if (contains(projectId, repoName, includeRepositories)) {
            return true
        }
        if (contains(projectId, StringPool.POUND, includeRepositories)) {
            return true
        }
        if (contains(StringPool.POUND, repoName, includeRepositories)) {
            return true
        }
        return false
    }

    private fun contains(projectId: String, repoName: String, includeRepositories: List<String>): Boolean {
        val key = "$projectId/$repoName"
        return includeRepositories.contains(key)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterArtifactReplicationHandler::class.java)
        // 读取结果返回超时时间 15分钟
        private const val READ_TIME_OUT = 60L * 15
    }
}