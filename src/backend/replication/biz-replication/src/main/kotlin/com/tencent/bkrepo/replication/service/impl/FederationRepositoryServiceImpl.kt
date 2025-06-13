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
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.exception.RemoteErrorCodeException
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.federation.FederatedRepositoryClient
import com.tencent.bkrepo.replication.dao.FederatedRepositoryDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.DetectType
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.FederatedClusterInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryConfigRequest
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
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class FederationRepositoryServiceImpl(
    private val federatedRepositoryDao: FederatedRepositoryDao,
    private val localDataManager: LocalDataManager,
    private val clusterNodeService: ClusterNodeService,
    private val replicaTaskService: ReplicaTaskService,
) : FederationRepositoryService {

    override fun createFederationRepository(request: FederatedRepositoryCreateRequest) {
        logger.info("Creating federation repository for project: ${request.projectId}, repo: ${request.repoName}")
        paramsCheck(request)
        val currentCluster = clusterNodeService.getByClusterId(request.clusterId)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, request.clusterId)
        try {
            val taskMap = doFederatedRepositoryCreate(request, currentCluster)
            val tFederatedRepository = buildTFederatedRepository(request, taskMap)
            federatedRepositoryDao.save(tFederatedRepository)
            logger.info(
                "Successfully created federation repository for " +
                    "project: ${request.projectId}, repo: ${request.repoName}"
            )
        } catch (e: RemoteErrorCodeException) {
            logger.warn("Failed to create federation repository, request: ${e.message}")
            throw ErrorCodeException(ReplicationMessageCode.UNSUPPORTED_CLUSTER_VERSION)
        }
    }

    override fun saveFederationRepositoryConfig(request: FederatedRepositoryConfigRequest) {
        createFederatedClusterAndSaveConfig(request)
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
            // 在目标集群生成对应cluster以及对应同步task
            syncFederationConfig(fed.projectId, fed.repoName, clusterNode, federatedClusters)
            // 生成本地同步task
            val taskId = createOrUpdateTask(request.projectId, request.repoName, fed, clusterNode)
            taskMap[fed.clusterId] = taskId
        }
        return taskMap
    }

    private fun syncFederationConfig(
        projectId: String,
        repoName: String,
        clusterNode: ClusterNodeInfo,
        federatedClusters: List<FederatedCluster>,
    ) {
        logger.info("Start to sync federation config for repo: $projectId|$repoName with cluster ${clusterNode.name}")
        val cluster = buildClusterInfo(clusterNode)
        val federatedClusterInfos = federatedClusters.map {
            val federatedClusterInfo = clusterNodeService.getByClusterId(it.clusterId)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, it.clusterId)
            FederatedClusterInfo(
                projectId = it.projectId,
                repoName = it.repoName,
                clusterNodeInfo = federatedClusterInfo,
                enabled = it.enabled
            )
        }
        val configRequest = FederatedRepositoryConfigRequest(
            projectId = projectId,
            repoName = repoName,
            federatedClusters = federatedClusterInfos,
            selfCluster = clusterNode
        )
        val federatedRepositoryClient = FeignClientFactory.create<FederatedRepositoryClient>(cluster)
        federatedRepositoryClient.createFederatedConfig(configRequest)
        logger.info("Successfully synced federation config for repo: $projectId|$repoName with cluster ${cluster.name}")
    }

    fun createFederatedClusterAndSaveConfig(request: FederatedRepositoryConfigRequest) {
        with(request) {
            logger.info("Start to sync federation config for project: $projectId, repo: $repoName")
            val selfCluster = getOrCreateCluster(selfCluster)
            val fedList = buildFederatedClusterList(projectId, repoName, federatedClusters)
            saveFederationConfig(projectId, repoName, selfCluster.id!!, fedList)
            logger.info("Successfully synced federation config for project: $projectId, repo: $repoName")
        }
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

    private fun getOrCreateCluster(cluster: ClusterNodeInfo): ClusterNodeInfo {
        val existCluster = clusterNodeService.getByClusterName(cluster.name)
        return when {
            existCluster == null -> {
                val clusterCreateRequest = buildClusterNodeCreateRequest(cluster)
                clusterNodeService.create(SYSTEM_USER, clusterCreateRequest)
            }

            existCluster.url != cluster.url -> {
                throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_EXISTS, cluster.name)
            }

            else -> existCluster
        }
    }

    private fun buildFederatedClusterList(
        projectId: String,
        repoName: String,
        federatedClusters: List<FederatedClusterInfo>,
    ): MutableList<FederatedCluster> {
        logger.info("Starting to build federated cluster list for project: $projectId, repo: $repoName")
        return federatedClusters.mapNotNull { it ->
            val federatedCluster = getOrCreateCluster(it.clusterNodeInfo)
            val fed = FederatedCluster(it.projectId, it.repoName, federatedCluster.id!!, it.enabled)
            val taskId = createOrUpdateTask(projectId, repoName, fed, federatedCluster)
            fed.taskId = taskId
            fed
        }.toMutableList()
    }

    private fun saveFederationConfig(
        projectId: String,
        repoName: String,
        clusterId: String,
        fedList: MutableList<FederatedCluster>,
    ) {
        val request = buildTFederatedRepository(
            projectId = projectId,
            repoName = repoName,
            clusterId = clusterId,
            federatedClusters = fedList
        )
        federatedRepositoryDao.save(request)
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
            logger.info("Creating new federation task: $taskName")
            val taskCreateRequest = ReplicaTaskCreateRequest(
                name = taskName,
                localProjectId = projectId,
                replicaObjectType = ReplicaObjectType.REPOSITORY,
                replicaTaskObjects = replicaTaskObjects,
                replicaType = ReplicaType.FEDERATION,
                setting = ReplicaSetting(),
                remoteClusterIds = setOf(clusterInfo.id!!),
                enabled = federatedCluster.enabled
            )
            task = replicaTaskService.create(taskCreateRequest)
        } else {
            logger.info("Updating existing federation task: ${task.id}")
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
            if (task!!.enabled != federatedCluster.enabled) {
                replicaTaskService.toggleStatus(task!!.key)
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

    private fun buildClusterNodeCreateRequest(cluster: ClusterNodeInfo): ClusterNodeCreateRequest {
        return ClusterNodeCreateRequest(
            name = cluster.name,
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

    private fun buildTFederatedRepository(
        projectId: String,
        repoName: String,
        federatedClusters: List<FederatedCluster>,
        clusterId: String,
    ): TFederatedRepository {
        return TFederatedRepository(
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

    companion object {
        private val logger = LoggerFactory.getLogger(FederationRepositoryServiceImpl::class.java)
        const val FEDERATION_TASK_NAME = "federation/%s/%s/%s"

    }
}