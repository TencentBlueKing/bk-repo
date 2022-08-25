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

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.util.okhttp.BasicAuthInterceptor
import com.tencent.bkrepo.common.artifact.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.common.artifact.cluster.FeignClientFactory
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.api.BlobReplicaClient
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeType
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.replica.base.replicator.ClusterReplicator
import com.tencent.bkrepo.replication.replica.base.replicator.EdgeNodeReplicator
import com.tencent.bkrepo.replication.replica.base.replicator.RemoteReplicator
import com.tencent.bkrepo.replication.replica.base.replicator.Replicator
import com.tencent.bkrepo.replication.util.StreamRequestBody
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

class ReplicaContext(
    taskDetail: ReplicaTaskDetail,
    val taskObject: ReplicaObjectInfo,
    val taskRecord: ReplicaRecordInfo,
    val localRepo: RepositoryDetail,
    val remoteCluster: ClusterNodeInfo,
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

    // TODO: Feign暂时不支持Stream上传，11+之后支持，升级后可以移除HttpClient上传
    private val pushBlobUrl = "${remoteCluster.url}/replica/blob/push"
    val httpClient: OkHttpClient?
    var cluster: ClusterInfo

    // 只针对remote镜像仓库分发的时候，将源tag分发成多个不同的tag，仅支持源tag为一个指定的版本
    var targetVersions: List<String>?

    // 只针对remote镜像仓库分发的时候，将源tag分发成多个不同的tag，仅支持源tag为一个指定的版本
    var targetVersions: List<String>?

    init {
        cluster = ClusterInfo(
            name = remoteCluster.name,
            url = remoteCluster.url,
            username = remoteCluster.username,
            password = remoteCluster.password,
            certificate = remoteCluster.certificate
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
        // 远端集群仓库特殊处理, 远端集群请求鉴权特殊处理
        httpClient = if (remoteCluster.type != ClusterNodeType.REMOTE) {
            HttpClientBuilderFactory.create(cluster.certificate).addInterceptor(
                BasicAuthInterceptor(cluster.username.orEmpty(), cluster.password.orEmpty())
            ).build()
        } else {
            null
        }

        targetVersions = initImageTargetTag()
    }

    /**
     * 推送blob文件数据到远程集群
     */
    fun pushBlob(inputStream: InputStream, size: Long, sha256: String, storageKey: String? = null) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", sha256, StreamRequestBody(inputStream, size))
            .addFormDataPart("sha256", sha256).apply {
                storageKey?.let { addFormDataPart("storageKey", it) }
            }.build()
        val httpRequest = Request.Builder()
            .url(pushBlobUrl)
            .post(requestBody)
            .build()
        httpClient?.newCall(httpRequest)?.execute().use {
            it?.let { it1 -> check(it1.isSuccessful) { "Failed to replica file: ${it.body()?.string()}" } }
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
}
