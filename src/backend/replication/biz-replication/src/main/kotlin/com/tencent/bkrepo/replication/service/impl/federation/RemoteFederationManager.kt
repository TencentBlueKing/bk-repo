package com.tencent.bkrepo.replication.service.impl.federation

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.api.federation.FederatedRepositoryClient
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.FederatedClusterInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryConfigRequest
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.util.FederationDataBuilder.buildClusterInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 远程联邦管理器
 * 负责远程集群的项目、仓库创建和配置同步
 */
@Component
class RemoteFederationManager(
    private val localDataManager: LocalDataManager,
    private val clusterNodeService: ClusterNodeService,
) {

    /**
     * 在远程集群创建项目和仓库
     */
    fun createRemoteProjectAndRepo(
        currentProjectId: String,
        currentRepoName: String,
        remoteProjectId: String,
        remoteRepoName: String,
        remoteClusterNode: ClusterNodeInfo,
        currentCluster: ClusterNodeInfo,
    ) {
        logger.info(
            "Start to create remote project and repo for repo: $currentProjectId|$currentRepoName " +
                "with cluster ${remoteClusterNode.name}"
        )
        val remoteCluster = buildClusterInfo(remoteClusterNode)
        val artifactReplicaClient = FeignClientFactory.create<ArtifactReplicaClient>(remoteCluster)

        // 创建远程项目
        val localProject = localDataManager.findProjectById(currentProjectId)
        val projectRequest = ProjectCreateRequest(
            name = remoteProjectId,
            displayName = remoteProjectId,
            description = localProject.description,
            operator = localProject.createdBy,
            source = currentCluster.name
        )
        artifactReplicaClient.replicaProjectCreateRequest(projectRequest).data ?: run {
            throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_CREATE_ERROR,
                "Failed to create remote project $remoteProjectId"
            )
        }

        // 创建远程仓库
        val localRepo = localDataManager.findRepoByName(currentProjectId, currentRepoName)
        val repoRequest = RepoCreateRequest(
            projectId = remoteProjectId,
            name = remoteRepoName,
            type = localRepo.type,
            category = localRepo.category,
            public = localRepo.public,
            description = localRepo.description,
            configuration = localRepo.configuration,
            operator = localRepo.createdBy,
            source = currentCluster.name
        )
        artifactReplicaClient.replicaRepoCreateRequest(repoRequest).data ?: run {
            throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_CREATE_ERROR,
                "Failed to create remote repo $remoteProjectId|$remoteRepoName"
            )
        }
    }

    /**
     * 同步联邦配置到远程集群
     */
    fun syncFederationConfig(
        name: String,
        federationId: String,
        projectId: String,
        repoName: String,
        remoteClusterNode: ClusterNodeInfo,
        federatedClusters: List<FederatedCluster>,
    ) {
        logger.info(
            "Start to sync federation config with federationId $federationId " +
                "for repo: $projectId|$repoName to cluster ${remoteClusterNode.name}"
        )
        val cluster = buildClusterInfo(remoteClusterNode)
        val federatedClusterInfos = federatedClusters.map { fedCluster ->
            val federatedClusterInfo = clusterNodeService.getByClusterId(fedCluster.clusterId)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, fedCluster.clusterId)
            FederatedClusterInfo(
                projectId = fedCluster.projectId,
                repoName = fedCluster.repoName,
                clusterNodeInfo = RemoteClusterInfo(
                    id = federatedClusterInfo.id,
                    name = federatedClusterInfo.name,
                    url = federatedClusterInfo.url,
                    username = federatedClusterInfo.username,
                    password = federatedClusterInfo.password,
                    certificate = federatedClusterInfo.certificate,
                    accessKey = federatedClusterInfo.accessKey,
                    secretKey = federatedClusterInfo.secretKey,
                    appId = federatedClusterInfo.appId,
                ),
                enabled = fedCluster.enabled
            )
        }
        val configRequest = FederatedRepositoryConfigRequest(
            name = name,
            federationId = federationId,
            projectId = projectId,
            repoName = repoName,
            federatedClusters = federatedClusterInfos,
            selfCluster = remoteClusterNode
        )
        val federatedRepositoryClient = FeignClientFactory.create<FederatedRepositoryClient>(cluster)
        federatedRepositoryClient.createFederatedConfig(configRequest).data ?: run {
            val msg = "Failed to sync federation config with federationId $federationId "
            throw ErrorCodeException(ReplicationMessageCode.FEDERATION_REPOSITORY_CREATE_ERROR, msg)
        }
        logger.info(
            "Successfully synced federation config with federationId $federationId " +
                "for repo: $projectId|$repoName to cluster ${cluster.name}"
        )
    }

    /**
     * 删除远程联邦配置
     */
    fun deleteRemoteFederationConfig(key: String, fed: FederatedCluster) {
        logger.info("Start to delete remote federation config on cluster: ${fed.clusterId}")
        val remoteClusterNode = clusterNodeService.getByClusterId(fed.clusterId) ?: return
        val cluster = buildClusterInfo(remoteClusterNode)
        val federatedRepositoryClient = FeignClientFactory.create<FederatedRepositoryClient>(cluster)
        federatedRepositoryClient.deleteConfig(fed.projectId, fed.repoName, key)
        logger.info("Successfully deleted remote federation config on cluster: ${fed.clusterId}")
    }

    /**
     * 删除联邦集群上与目标集群相关配置
     */
    fun deleteRemoteConfigForTargetCluster(
        federationId: String,
        targetClusterId: String,
        remainingClusters: List<FederatedCluster>
    ) {
        logger.info("Start to delete remote config for target cluster: $targetClusterId")
        val targetCluster = clusterNodeService.getByClusterId(targetClusterId) ?: return
        remainingClusters.forEach { fed ->
            val remoteClusterNode = clusterNodeService.getByClusterId(targetClusterId) ?: return
            val cluster = buildClusterInfo(remoteClusterNode)
            val federatedRepositoryClient = FeignClientFactory.create<FederatedRepositoryClient>(cluster)
            federatedRepositoryClient.removeClusterFromFederation(
                fed.projectId, fed.repoName, federationId, targetCluster.name
            )
        }
        logger.info("Successfully deleted remote config for target cluster: $targetClusterId")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RemoteFederationManager::class.java)
    }
}