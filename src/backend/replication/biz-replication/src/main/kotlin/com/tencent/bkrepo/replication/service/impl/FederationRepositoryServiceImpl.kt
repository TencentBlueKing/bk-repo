/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.replication.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.util.version.SemVersion
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.api.cluster.ClusterClusterNodeClient
import com.tencent.bkrepo.replication.api.federation.FederatedRepositoryClient
import com.tencent.bkrepo.replication.constant.DEFAULT_VERSION
import com.tencent.bkrepo.replication.dao.FederatedRepositoryDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.DetectType
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryCreateRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskUpdateRequest
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.FederationRepositoryService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class FederationRepositoryServiceImpl(
    private val federatedRepositoryDao: FederatedRepositoryDao,
    private val localDataManager: LocalDataManager,
    private val clusterNodeService: ClusterNodeService,
    private val replicaTaskService: ReplicaTaskService,
) : FederationRepositoryService {

    @Value("\${spring.application.version:$DEFAULT_VERSION}")
    var version: String = DEFAULT_VERSION

    override fun createFederationRepository(request: FederatedRepositoryCreateRequest) {
        paramsCheck(request)
        val currentCluster = clusterNodeService.getByClusterId(request.clusterId)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, request.clusterId)
        try {
            val taskMap = doFederatedRepositoryCreate(request, currentCluster)
            val tFederatedRepository = buildTFederatedRepository(request, taskMap)
            federatedRepositoryDao.save(tFederatedRepository)
        } catch (e: RemoteErrorCodeException) {
            logger.warn("create federation repository failed, request: ${e.message}")
            throw ErrorCodeException(ReplicationMessageCode.UNSUPPORTED_CLUSTER_VERSION)
        }

    }

    override fun saveFederationRepositoryConfig(request: FederatedRepositoryCreateRequest) {
        val tFederatedRepository = buildTFederatedRepository(request)
        federatedRepositoryDao.save(tFederatedRepository)
    }

    override fun listFederationRepository(projectId: String, repoName: String): List<FederatedCluster> {
        return federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName)
            .flatMap { it.federatedClusters }
            .filter { it.enabled }
    }

    override fun deleteFederationRepositoryConfig(projectId: String, repoName: String) {
        federatedRepositoryDao.deleteByProjectIdAndRepoName(projectId, repoName)
    }

    override fun updateFederationRepository(request: FederatedRepositoryCreateRequest) {
        paramsCheck(request)
        val currentCluster = clusterNodeService.getByClusterId(request.clusterId)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, request.clusterId)
        val taskMap = doFederatedRepositoryCreate(request, currentCluster)
        val tFederatedRepository = buildTFederatedRepository(request, taskMap)
        federatedRepositoryDao.save(tFederatedRepository)
    }

    override fun getCurrentClusterName(projectId: String, repoName: String): String? {
        return federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName).firstOrNull()?.let {
            clusterNodeService.getByClusterId(it.clusterId)?.name
        }
    }

    private fun paramsCheck(request: FederatedRepositoryCreateRequest) {
        require(request.federatedClusters.isNotEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "federatedClusters")
        }
        val repo = localDataManager.findRepoByName(request.projectId, request.repoName)
        if (repo.type != RepositoryType.GENERIC) {
            logger.warn("Unsupported repo type: ${repo.type}")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, request.repoName)
        }
    }

    private fun doFederatedRepositoryCreate(
        request: FederatedRepositoryCreateRequest,
        currentCluster: ClusterNodeInfo,
    ): Map<String, String> {
        val taskMap = mutableMapOf<String, String>()
        request.federatedClusters.forEach { fed ->
            val clusterNode = clusterNodeService.getByClusterId(fed.clusterId)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, fed.clusterId)
            val federatedClusters = request.federatedClusters
                .filter { it.clusterId != fed.clusterId }
                .plus(FederatedCluster(request.projectId, request.repoName, currentCluster.id!!, true))
            createFederatedClusterAndSyncConfig(fed.projectId, fed.repoName, clusterNode, federatedClusters)
            val taskId = createOrUpdateTask(request.projectId, request.repoName, fed, clusterNode)
            taskMap[fed.clusterId] = taskId
        }
        return taskMap
    }

    private fun createFederatedClusterAndSyncConfig(
        projectId: String,
        repoName: String,
        clusterNode: ClusterNodeInfo,
        federatedClusters: List<FederatedCluster>,
    ) {
        val cluster = buildClusterInfo(clusterNode)
        val artifactReplicaClient = FeignClientFactory.create<ArtifactReplicaClient>(cluster)
        checkClusterVersion(artifactReplicaClient)

        val clusterClusterNodeClient = FeignClientFactory.create<ClusterClusterNodeClient>(cluster)
        val existCluster = getOrCreateCluster(cluster, clusterClusterNodeClient)

        val fedList = buildFederatedClusterList(
            projectId, repoName, federatedClusters, clusterClusterNodeClient
        )
        syncFederationConfig(projectId, repoName, existCluster.id!!, fedList, cluster)
    }

    private fun buildClusterInfo(clusterNode: ClusterNodeInfo): ClusterInfo {
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

    private fun checkClusterVersion(artifactReplicaClient: ArtifactReplicaClient) {
        val currentVersion = SemVersion.parse(version)
        val federatedClusterVersion = SemVersion.parse(artifactReplicaClient.version().data!!)
        if (currentVersion > federatedClusterVersion) {
            throw ErrorCodeException(
                ReplicationMessageCode.UNSUPPORTED_CLUSTER_VERSION,
                version,
                artifactReplicaClient.version()
            )
        }
    }

    private fun getOrCreateCluster(
        cluster: ClusterInfo,
        clusterClusterNodeClient: ClusterClusterNodeClient,
    ): ClusterNodeInfo {
        val existCluster = clusterClusterNodeClient.getCluster(cluster.name!!).data
        return when {
            existCluster == null -> {
                val clusterCreateRequest = buildClusterNodeCreateRequest(cluster)
                clusterClusterNodeClient.create(SYSTEM_USER, clusterCreateRequest)
                clusterClusterNodeClient.getCluster(cluster.name!!).data!!
            }

            existCluster.url != cluster.url -> {
                throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_EXISTS, cluster.name!!)
            }

            else -> existCluster
        }
    }

    private fun buildFederatedClusterList(
        projectId: String,
        repoName: String,
        federatedClusters: List<FederatedCluster>,
        clusterClusterNodeClient: ClusterClusterNodeClient,
    ): MutableList<FederatedCluster> {
        return federatedClusters.mapNotNull { fed ->
            val fedCluster = clusterNodeService.getByClusterId(fed.clusterId)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, fed.clusterId)
            val fedClusterInfo = buildClusterInfo(fedCluster)
            val clusterCreateRequest = buildClusterNodeCreateRequest(fedClusterInfo)
            clusterClusterNodeClient.create(SYSTEM_USER, clusterCreateRequest)
            val savedFedCluster = clusterClusterNodeClient.getCluster(fedClusterInfo.name!!).data
            savedFedCluster?.let {
                val taskId = createOrUpdateTask(projectId, repoName, fed, savedFedCluster)
                FederatedCluster(fed.projectId, fed.repoName, it.id!!, fed.enabled, taskId)
            }
        }.toMutableList()
    }

    private fun syncFederationConfig(
        projectId: String,
        repoName: String,
        clusterId: String,
        fedList: MutableList<FederatedCluster>,
        cluster: ClusterInfo,
    ) {
        val request = FederatedRepositoryCreateRequest(
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId,
            federatedClusters = fedList
        )
        val federatedRepositoryClient = FeignClientFactory.create<FederatedRepositoryClient>(cluster)
        federatedRepositoryClient.configSync(request)
    }

    /**
     * 当远端集群创建后，创建/更新对应的任务
     */
    private fun createOrUpdateTask(
        projectId: String,
        repoName: String,
        federatedCluster: FederatedCluster,
        clusterInfo: ClusterNodeInfo,
    ): String {
        val repositoryDetail = localDataManager.findRepoByName(projectId, repoName)
        val replicaTaskObjects = buildReplicaTaskObjects(
            repoName, repositoryDetail.type, federatedCluster
        )
        val taskName = FEDERATION_TASK_NAME.format(projectId, repoName, clusterInfo.name)
        var task = replicaTaskService.getByTaskName(taskName)
        if (task == null) {
            val taskCreateRequest = ReplicaTaskCreateRequest(
                name = taskName,
                localProjectId = projectId,
                replicaObjectType = ReplicaObjectType.REPOSITORY,
                replicaTaskObjects = replicaTaskObjects,
                replicaType = ReplicaType.FEDERATION,
                setting = ReplicaSetting(),
                remoteClusterIds = setOf(clusterInfo.id!!),
                enabled = true
            )
            task = replicaTaskService.create(taskCreateRequest)
        } else {
            val taskUpdateRequest = ReplicaTaskUpdateRequest(
                key = task.key,
                name = task.name,
                localProjectId = projectId,
                replicaTaskObjects = replicaTaskObjects,
                replicaObjectType = ReplicaObjectType.REPOSITORY,
                setting = ReplicaSetting(),
                remoteClusterIds = setOf(clusterInfo.id!!),
            )
            replicaTaskService.update(taskUpdateRequest)?.let {
                task = it.copy()
            }
        }
        return task!!.id
    }

    private fun buildReplicaTaskObjects(
        repoName: String,
        repoType: RepositoryType,
        federatedCluster: FederatedCluster,
    ): List<ReplicaObjectInfo> {
        with(federatedCluster) {
            val taskObjects = mutableListOf<ReplicaObjectInfo>()
            taskObjects.add(
                ReplicaObjectInfo(
                    localRepoName = repoName,
                    repoType = repoType,
                    remoteProjectId = federatedCluster.projectId,
                    remoteRepoName = federatedCluster.repoName,
                    packageConstraints = null,
                    pathConstraints = null
                )
            )
            return taskObjects
        }
    }

    private fun buildClusterNodeCreateRequest(cluster: ClusterInfo): ClusterNodeCreateRequest {
        return ClusterNodeCreateRequest(
            name = cluster.name!!,
            url = cluster.url,
            certificate = cluster.certificate,
            appId = cluster.appId,
            accessKey = cluster.accessKey,
            secretKey = cluster.secretKey,
            udpPort = cluster.udpPort,
            username = cluster.username,
            password = cluster.password,
            type = ClusterNodeType.STANDALONE,
            detectType = DetectType.PING
        )
    }

    /**
     * 构建联邦仓库实体对象
     * @param request 创建联邦仓库请求参数
     * @param taskMap 集群ID与任务ID的映射关系
     * @return 构建好的联邦仓库实体
     */
    private fun buildTFederatedRepository(
        request: FederatedRepositoryCreateRequest,
        taskMap: Map<String, String>,
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
            lastModifiedDate = LocalDateTime.now()
        )
    }

    private fun buildTFederatedRepository(request: FederatedRepositoryCreateRequest): TFederatedRepository {
        return TFederatedRepository(
            projectId = request.projectId,
            repoName = request.repoName,
            clusterId = request.clusterId,
            federatedClusters = request.federatedClusters,
            createdBy = SecurityUtils.getUserId(),
            createdDate = LocalDateTime.now(),
            lastModifiedBy = SecurityUtils.getUserId(),
            lastModifiedDate = LocalDateTime.now()
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationRepositoryServiceImpl::class.java)
        const val FEDERATION_TASK_NAME = "federation/%s/%s/%s"

    }
}