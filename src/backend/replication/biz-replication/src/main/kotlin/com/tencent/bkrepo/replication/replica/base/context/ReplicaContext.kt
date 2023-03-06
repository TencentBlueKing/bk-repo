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

package com.tencent.bkrepo.replication.replica.base.context

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.service.util.okhttp.BasicAuthInterceptor
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.api.BlobReplicaClient
import com.tencent.bkrepo.replication.constant.FILE
import com.tencent.bkrepo.replication.constant.SHA256
import com.tencent.bkrepo.replication.constant.STORAGE_KEY
import com.tencent.bkrepo.replication.pojo.blob.RequestTag
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.replica.base.OkHttpClientPool
import com.tencent.bkrepo.replication.replica.base.interceptor.SignInterceptor
import com.tencent.bkrepo.replication.replica.base.replicator.ClusterReplicator
import com.tencent.bkrepo.replication.replica.base.replicator.EdgeNodeReplicator
import com.tencent.bkrepo.replication.replica.base.replicator.RemoteReplicator
import com.tencent.bkrepo.replication.replica.base.replicator.Replicator
import com.tencent.bkrepo.replication.util.StreamRequestBody
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.time.Duration

class ReplicaContext(
    taskDetail: ReplicaTaskDetail,
    val taskObject: ReplicaObjectInfo,
    val taskRecord: ReplicaRecordInfo,
    val localRepo: RepositoryDetail,
    val remoteCluster: ClusterNodeInfo
) {
    // 任务信息
    val task = taskDetail.task

    // 本地仓库信息
    val localProjectId: String = task.projectId
    val localRepoName: String = taskObject.localRepoName
    val localRepoType: RepositoryType = taskObject.repoType

    // 远程仓库信息
    val remoteProjectId: String? = taskObject.remoteProjectId
    val remoteRepoName: String? = taskObject.remoteRepoName
    val remoteRepoType: RepositoryType = taskObject.repoType
    var remoteRepo: RepositoryDetail? = null

    // 事件
    lateinit var event: ArtifactEvent

    // 同步状态
    var status = ExecutionStatus.RUNNING
    var errorMessage: String? = null
    var artifactReplicaClient: ArtifactReplicaClient? = null
    var blobReplicaClient: BlobReplicaClient? = null
    val replicator: Replicator

    var cluster: ClusterInfo

    // 只针对remote镜像仓库分发的时候，将源tag分发成多个不同的tag，仅支持源tag为一个指定的版本
    var targetVersions: List<String>?

    private val pushBlobUrl = "${remoteCluster.url}/replica/blob/push"
    private val httpClient: OkHttpClient

    init {
        cluster = ClusterInfo(
            name = remoteCluster.name,
            url = remoteCluster.url,
            username = remoteCluster.username,
            password = remoteCluster.password,
            certificate = remoteCluster.certificate,
            appId = remoteCluster.appId,
            accessKey = remoteCluster.accessKey,
            secretKey = remoteCluster.secretKey
        )

        // 远端集群仓库特殊处理, 远端集群走对应制品类型协议传输
        if (remoteCluster.type != ClusterNodeType.REMOTE) {
            artifactReplicaClient = FeignClientFactory.create(cluster)
            blobReplicaClient = FeignClientFactory.create(cluster)
        }
        replicator = when (remoteCluster.type) {
            ClusterNodeType.STANDALONE -> SpringContextUtils.getBean<ClusterReplicator>()
            ClusterNodeType.EDGE -> SpringContextUtils.getBean<EdgeNodeReplicator>()
            ClusterNodeType.REMOTE -> SpringContextUtils.getBean<RemoteReplicator>()
            else -> throw UnsupportedOperationException()
        }

        targetVersions = initImageTargetTag()
        val readTimeout = Duration.ofMillis(READ_TIMEOUT)
        httpClient = if (cluster.username != null) {
            OkHttpClientPool.getHttpClient(
                cluster,
                readTimeout,
                BasicAuthInterceptor(cluster.username!!, cluster.password!!)
            )
        } else {
            OkHttpClientPool.getHttpClient(
                cluster,
                readTimeout,
                SignInterceptor(cluster)
            )
        }
    }

    /**
     * 推送blob文件数据到远程集群
     */
    fun pushBlob(inputStream: InputStream, size: Long, sha256: String, storageKey: String? = null) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(FILE, sha256, StreamRequestBody(inputStream, size))
            .addFormDataPart(SHA256, sha256).apply {
                storageKey?.let { addFormDataPart(STORAGE_KEY, it) }
            }.build()
        logger.info("The request will be sent for file sha256 [$sha256].")
        val tag = RequestTag(task, sha256, size)
        val httpRequest = Request.Builder()
            .url(pushBlobUrl)
            .post(requestBody)
            .tag(RequestTag::class.java, tag)
            .build()
        httpClient.newCall(httpRequest).execute().use {
            check(it.isSuccessful) { "Failed to replica file: ${it.body?.string()}" }
        }
    }

    /**
     * 只针对remote镜像仓库分发的时候，将源tag分发成多个不同的tag，仅支持源tag为一个指定的版本
     */
    private fun initImageTargetTag(): List<String>? {
        if (taskObject.packageConstraints.isNullOrEmpty()) return null
        if (taskObject.packageConstraints!!.size != 1) return null
        if (taskObject.packageConstraints!!.first().targetVersions.isNullOrEmpty()) return null
        if (taskObject.packageConstraints!!.first().versions.isNullOrEmpty()) return null
        if (taskObject.packageConstraints!!.first().versions!!.size != 1) return null
        return taskObject.packageConstraints!!.first().targetVersions
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReplicaContext::class.java)
        private const val READ_TIMEOUT = 60 * 60 * 1000L
    }
}
