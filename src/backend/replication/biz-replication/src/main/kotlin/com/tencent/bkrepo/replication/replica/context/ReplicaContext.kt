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

package com.tencent.bkrepo.replication.replica.context

import com.tencent.bkrepo.common.api.pojo.ClusterArchitecture
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.service.util.okhttp.BasicAuthInterceptor
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.api.BlobReplicaClient
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.record.ExecutionStatus
import com.tencent.bkrepo.replication.pojo.record.ReplicaRecordInfo
import com.tencent.bkrepo.replication.pojo.task.ReplicaTaskDetail
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.replica.base.interceptor.RetryInterceptor
import com.tencent.bkrepo.replication.replica.base.interceptor.SignInterceptor
import com.tencent.bkrepo.replication.replica.replicator.Replicator
import com.tencent.bkrepo.replication.replica.replicator.commitedge.CenterClusterReplicator
import com.tencent.bkrepo.replication.replica.replicator.commitedge.CenterRemoteReplicator
import com.tencent.bkrepo.replication.replica.replicator.standalone.ClusterReplicator
import com.tencent.bkrepo.replication.replica.replicator.standalone.EdgeNodeReplicator
import com.tencent.bkrepo.replication.replica.replicator.standalone.RemoteReplicator
import com.tencent.bkrepo.replication.util.OkHttpClientPool
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import java.time.Duration

class ReplicaContext(
    val taskDetail: ReplicaTaskDetail,
    val taskObject: ReplicaObjectInfo,
    val taskRecord: ReplicaRecordInfo,
    val localRepo: RepositoryDetail,
    val remoteCluster: ClusterNodeInfo,
    replicationProperties: ReplicationProperties
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

    val httpClient: OkHttpClient

    init {
        cluster = ClusterInfo(
            name = remoteCluster.name,
            url = remoteCluster.url,
            username = remoteCluster.username,
            password = remoteCluster.password,
            certificate = remoteCluster.certificate,
            appId = remoteCluster.appId,
            accessKey = remoteCluster.accessKey,
            secretKey = remoteCluster.secretKey,
            udpPort = remoteCluster.udpPort
        )

        // 远端集群仓库特殊处理, 远端集群走对应制品类型协议传输
        if (remoteCluster.type != ClusterNodeType.REMOTE) {
            artifactReplicaClient = FeignClientFactory.create(cluster)
            blobReplicaClient = FeignClientFactory.create(cluster)
        }
        replicator = buildReplicator()

        targetVersions = initImageTargetTag()
        val readTimeout = Duration.ofMillis(READ_TIMEOUT)
        val writeTimeout = Duration.ofMillis(WRITE_TIMEOUT)
        val closeTimeout = Duration.ofMillis(CLOSE_TIMEOUT)
        httpClient = if (cluster.username != null && cluster.password != null) {
            OkHttpClientPool.getHttpClient(
                replicationProperties.timoutCheckHosts,
                cluster,
                readTimeout,
                writeTimeout,
                closeTimeout,
                BasicAuthInterceptor(cluster.username!!, cluster.password!!),
                RetryInterceptor()
                )
        } else {
            OkHttpClientPool.getHttpClient(
                replicationProperties.timoutCheckHosts,
                cluster,
                readTimeout,
                writeTimeout,
                closeTimeout,
                SignInterceptor(cluster),
                RetryInterceptor()
                )
        }
    }

    private fun buildReplicator(): Replicator {
        val clusterProperties = SpringContextUtils.getBean<ClusterProperties>()
        val isCommitEdgeCenterNode = clusterProperties.role == ClusterNodeType.CENTER &&
            clusterProperties.architecture == ClusterArchitecture.COMMIT_EDGE
        return when {
            remoteCluster.type == ClusterNodeType.STANDALONE && isCommitEdgeCenterNode ->
                SpringContextUtils.getBean<CenterClusterReplicator>()
            remoteCluster.type == ClusterNodeType.STANDALONE && !isCommitEdgeCenterNode ->
                SpringContextUtils.getBean<ClusterReplicator>()
            remoteCluster.type == ClusterNodeType.EDGE -> SpringContextUtils.getBean<EdgeNodeReplicator>()
            remoteCluster.type == ClusterNodeType.REMOTE && isCommitEdgeCenterNode ->
                SpringContextUtils.getBean<CenterRemoteReplicator>()
            remoteCluster.type == ClusterNodeType.REMOTE && !isCommitEdgeCenterNode ->
                SpringContextUtils.getBean<RemoteReplicator>()
            else -> throw UnsupportedOperationException()
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
        const val READ_TIMEOUT = 60 * 60 * 1000L
        const val WRITE_TIMEOUT = 5 * 1000L
        const val CLOSE_TIMEOUT = 10 * 1000L
    }
}
