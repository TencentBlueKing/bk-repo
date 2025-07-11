/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.replicator.base.internal

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.rateLimit
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.common.artifact.util.http.StreamRequestBody
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.BLOB_PUSH_URI
import com.tencent.bkrepo.replication.constant.BOLBS_UPLOAD_FIRST_STEP_URL_STRING
import com.tencent.bkrepo.replication.constant.FILE
import com.tencent.bkrepo.replication.constant.MD5
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.replication.constant.SIZE
import com.tencent.bkrepo.replication.constant.STORAGE_KEY
import com.tencent.bkrepo.replication.enums.WayOfPushArtifact
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.blob.RequestTag
import com.tencent.bkrepo.replication.replica.context.FilePushContext
import com.tencent.bkrepo.replication.replica.replicator.base.ArtifactReplicationHandler
import okhttp3.MultipartBody
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.stereotype.Component

@RefreshScope
@Component
class ClusterArtifactReplicationHandler(
    localDataManager: LocalDataManager,
    replicationProperties: ReplicationProperties,
) : ArtifactReplicationHandler(localDataManager, replicationProperties) {

    //不支持yaml list配置 https://github.com/spring-projects/spring-framework/issues/16381
    @Value("\${replication.chunkedRepos:}")
    private var chunkedRepos: List<String> = emptyList()

    @Value("\${replication.fdtpRepos:}")
    private var fdtpRepos: List<String> = emptyList()

    @Value("\${replication.httpRepos:}")
    private var httpRepos: List<String> = emptyList()

    @Autowired
    @Lazy
    private lateinit var fdtpPusher: FdtpPusher

    override fun blobPush(
        filePushContext: FilePushContext,
        pushType: String,
        downGrade: Boolean
    ) : Boolean {
        val newType = filterRepoWithPushType(
            pushType = pushType,
            projectId = filePushContext.context.localProjectId,
            repoName = filePushContext.context.localRepoName,
            downGrade = downGrade,
            size = filePushContext.size
        )
        return when (newType) {
            WayOfPushArtifact.PUSH_WITH_CHUNKED.value -> {
                super.blobPush(filePushContext, newType, downGrade)
            }
            WayOfPushArtifact.PUSH_WITH_FDTP.value -> {
                require(::fdtpPusher.isInitialized) { "fdtp is disabled" }
                fdtpPusher.pushBlob(filePushContext)
            }
            else -> {
                pushBlob(filePushContext)
                true
            }
        }
    }

    private fun filterRepoWithPushType(
        pushType: String, projectId: String,
        repoName: String, downGrade: Boolean,
        size: Long?
    ): String {
        if (downGrade && !filterFileSize(size)) {
            return pushType
        }
        return if (filterProjectRepo(projectId, repoName, chunkedRepos)) {
            WayOfPushArtifact.PUSH_WITH_CHUNKED.value
        } else if (filterProjectRepo(projectId, repoName, fdtpRepos)) {
            WayOfPushArtifact.PUSH_WITH_FDTP.value
        } else if (filterProjectRepo(projectId, repoName, httpRepos)) {
            WayOfPushArtifact.PUSH_WITH_DEFAULT.value
        } else if (filterFileSize(size)){
            WayOfPushArtifact.PUSH_WITH_CHUNKED.value
        } else {
            pushType
        }
    }

    override fun getBlobFileInfo(
        filePushContext: FilePushContext
    ): FileInfo {
        return FileInfo(filePushContext.sha256!!, filePushContext.md5!!, filePushContext.size!!)
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
        fileInfo: FileInfo,
        filePushContext: FilePushContext
    ) : String {
        return buildParams(
            sha256 = fileInfo.sha256,
            filePushContext = filePushContext,
            md5 = fileInfo.md5,
            size = fileInfo.size
        )
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

    private fun buildParams(
        sha256: String,
        filePushContext: FilePushContext,
        md5: String? = null,
        size: Long? = null
    ): String {
        val builder = StringBuilder("$SHA256=$sha256")
        filePushContext.context.remoteRepo?.storageCredentials?.key?.let {
            builder.append("&$STORAGE_KEY=$it")
        }
        md5?.let {
            builder.append("&$MD5=$it")
        }
        size?.let {
            builder.append("&$SIZE=$it")
        }
        return builder.toString()
    }

    /**
     * 针对仓库特殊配置进行过滤
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

    /**
     * 针对client_max_body_size有最大值限制，文件分发时，超过该值的请求使用分块分发
     */
    private fun filterFileSize(size: Long?): Boolean {
        return size != null && replicationProperties.clientMaxBodySize <= size
    }

    private fun contains(projectId: String, repoName: String, includeRepositories: List<String>): Boolean {
        val key = "$projectId/$repoName"
        return includeRepositories.contains(key)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterArtifactReplicationHandler::class.java)
    }
}
