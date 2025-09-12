package com.tencent.bkrepo.replication.util

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.DetectType
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryCreateRequest
import java.time.LocalDateTime


object FederationDataBuilder {

    /**
     * 构建联邦仓库实体对象
     */
    fun buildTFederatedRepository(
        request: FederatedRepositoryCreateRequest,
        taskMap: Map<String, String>,
        federationId: String,
    ): TFederatedRepository {
        // 将请求中的联邦集群列表转换为实体列表，并添加对应的任务ID
        val federatedClusters = request.federatedClusters.map {
            FederatedCluster(
                projectId = it.projectId,
                repoName = it.repoName,
                clusterId = it.clusterId,
                enabled = it.enabled,
                taskId = taskMap[it.clusterId]
            )
        }.toMutableList()

        // 构建并返回联邦仓库实体
        return TFederatedRepository(
            projectId = request.projectId,
            repoName = request.repoName,
            clusterId = request.clusterId,
            federatedClusters = federatedClusters,
            createdBy = SecurityUtils.getUserId(),
            createdDate = LocalDateTime.now(),
            lastModifiedBy = SecurityUtils.getUserId(),
            lastModifiedDate = LocalDateTime.now(),
            federationId = federationId,
            name = request.name
        )
    }

    /**
     * 构建联邦仓库实体对象（重载方法）
     */
    fun buildTFederatedRepository(
        name: String,
        federationId: String,
        projectId: String,
        repoName: String,
        clusterId: String,
        federatedClusters: List<FederatedCluster>,
    ): TFederatedRepository {
        return TFederatedRepository(
            name = name,
            federationId = federationId,
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId,
            federatedClusters = federatedClusters,
            createdBy = SecurityUtils.getUserId(),
            createdDate = LocalDateTime.now(),
            lastModifiedBy = SecurityUtils.getUserId(),
            lastModifiedDate = LocalDateTime.now()
        )
    }

    /**
     * 转换为联邦仓库信息对象
     */
    fun convertToFederatedRepositoryInfo(tFederatedRepository: TFederatedRepository): FederatedRepositoryInfo {
        return FederatedRepositoryInfo(
            federationId = tFederatedRepository.federationId,
            name = tFederatedRepository.name,
            projectId = tFederatedRepository.projectId,
            repoName = tFederatedRepository.repoName,
            clusterId = tFederatedRepository.clusterId,
            federatedClusters = tFederatedRepository.federatedClusters,
            isFullSyncing = tFederatedRepository.isFullSyncing,
            lastFullSyncStartTime = tFederatedRepository.lastFullSyncStartTime,
            lastFullSyncEndTime = tFederatedRepository.lastFullSyncEndTime
        )
    }

    /**
     * 构建集群信息对象
     */
    fun buildClusterInfo(clusterNode: ClusterNodeInfo): ClusterInfo {
        return ClusterInfo(
            name = clusterNode.name,
            url = clusterNode.url,
            username = clusterNode.username,
            password = clusterNode.password,
            certificate = clusterNode.certificate,
            appId = clusterNode.appId,
            accessKey = clusterNode.accessKey,
            secretKey = clusterNode.secretKey,
            udpPort = clusterNode.udpPort
        )
    }

    /**
     * 构建集群节点创建请求
     */
    fun buildClusterNodeCreateRequest(cluster: RemoteClusterInfo): ClusterNodeCreateRequest {
        return ClusterNodeCreateRequest(
            name = cluster.name,
            url = cluster.url,
            certificate = cluster.certificate,
            appId = cluster.appId,
            accessKey = cluster.accessKey,
            secretKey = cluster.secretKey,
            username = cluster.username,
            password = cluster.password,
            type = ClusterNodeType.STANDALONE,
            detectType = DetectType.PING
        )
    }
}