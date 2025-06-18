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

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.constant.StringPool.uniqueId
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
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
import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
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
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
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

    override fun createFederationRepository(request: FederatedRepositoryCreateRequest): String {
        logger.info("Creating federation repository for project: ${request.projectId}, repo: ${request.repoName}")
        paramsCheck(request)
        val currentCluster = clusterNodeService.getByClusterId(request.clusterId)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, request.clusterId)
        try {
            val key = uniqueId()
            val taskMap = doFederatedRepositoryCreate(request, currentCluster, key)
            val tFederatedRepository = buildTFederatedRepository(request, taskMap, key)
            federatedRepositoryDao.save(tFederatedRepository)
            logger.info(
                "Successfully created federation repository for repo:" +
                    " ${request.projectId}|${request.repoName} with key: $key"
            )
            return key
        } catch (e: Exception) {
            logger.warn("Failed to create federation repository, request: ${e.message}")
            throw ErrorCodeException(ReplicationMessageCode.FEDERATION_REPOSITORY_CREATE_ERROR, e.message.orEmpty())
        }
    }

    override fun saveFederationRepositoryConfig(request: FederatedRepositoryConfigRequest) {
        createFederatedClusterAndSaveConfig(request)
    }

    override fun listFederationRepository(
        projectId: String, repoName: String, key: String?,
    ): List<FederatedRepositoryInfo> {
        return federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, key).map {
            convertToFederatedRepositoryInfo(it)
        }
    }

    override fun deleteFederationRepositoryConfig(projectId: String, repoName: String, key: String) {
        deleteConfig(projectId, repoName, key, true)
    }

    override fun deleteLocalFederationRepositoryConfig(projectId: String, repoName: String, key: String) {
        deleteConfig(projectId, repoName, key, false)
    }

    override fun getCurrentClusterName(projectId: String, repoName: String, key: String): String? {
        return federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, key).firstOrNull()?.let {
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

    private fun deleteConfig(
        projectId: String,
        repoName: String,
        key: String,
        deleteRemote: Boolean = false,
    ) {
        val federationRepository = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, key)
            .firstOrNull() ?: return
        federationRepository.federatedClusters.forEach {
            if (deleteRemote) {
                deleteRemoteFederationConfig(key, it)
            }
            val task = replicaTaskService.getByTaskId(it.taskId!!)
            task?.let {
                replicaTaskService.deleteByTaskKey(task.key)
            }
        }
        federatedRepositoryDao.deleteByProjectIdAndRepoName(projectId, repoName, key)
    }

    private fun doFederatedRepositoryCreate(
        request: FederatedRepositoryCreateRequest,
        currentCluster: ClusterNodeInfo,
        key: String,
    ): Map<String, String> {
        val taskMap = mutableMapOf<String, String>()
        request.federatedClusters.forEach { fed ->
            val remoteClusterNode = clusterNodeService.getByClusterId(fed.clusterId)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, fed.clusterId)
            val federatedClusters = request.federatedClusters
                .filter { it.clusterId != fed.clusterId }
                .plus(FederatedCluster(request.projectId, request.repoName, currentCluster.id!!, true))
            remoteProjectAndRepoCreate(
                request.projectId, request.repoName, fed.projectId, fed.repoName, remoteClusterNode, currentCluster
            )
            // 在目标集群生成对应cluster以及对应同步task
            syncFederationConfig(request.name, key, fed.projectId, fed.repoName, remoteClusterNode, federatedClusters)
            // 生成本地同步task
            val taskId = createOrUpdateTask(key, request.projectId, request.repoName, fed, remoteClusterNode)
            taskMap[fed.clusterId] = taskId
        }
        return taskMap
    }

    private fun deleteRemoteFederationConfig(key: String, fed: FederatedCluster) {
        logger.info("Start to delete remote federation config")
        val remoteClusterNode = clusterNodeService.getByClusterId(fed.clusterId) ?: return
        val cluster = buildClusterInfo(remoteClusterNode)
        val federatedRepositoryClient = FeignClientFactory.create<FederatedRepositoryClient>(cluster)
        federatedRepositoryClient.deleteConfig(fed.projectId, fed.repoName, key)
    }

    private fun syncFederationConfig(
        name: String,
        key: String,
        projectId: String,
        repoName: String,
        remoteClusterNode: ClusterNodeInfo,
        federatedClusters: List<FederatedCluster>,
    ) {
        logger.info(
            "Start to sync federation config with key $key " +
                "for repo: $projectId|$repoName to cluster ${remoteClusterNode.name}"
        )
        val cluster = buildClusterInfo(remoteClusterNode)
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
            name = name,
            key = key,
            projectId = projectId,
            repoName = repoName,
            federatedClusters = federatedClusterInfos,
            selfCluster = remoteClusterNode
        )
        val federatedRepositoryClient = FeignClientFactory.create<FederatedRepositoryClient>(cluster)
        federatedRepositoryClient.createFederatedConfig(configRequest)
        logger.info(
            "Successfully synced federation config with key $key " +
                "for repo: $projectId|$repoName to cluster ${cluster.name}"
        )
    }

    private fun remoteProjectAndRepoCreate(
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
        val localProject = localDataManager.findProjectById(currentProjectId)
        val projectRequest = ProjectCreateRequest(
            name = remoteProjectId,
            displayName = remoteProjectId,
            description = localProject.description,
            operator = localProject.createdBy,
            source = currentCluster.name
        )
        artifactReplicaClient.replicaProjectCreateRequest(projectRequest)
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
        artifactReplicaClient.replicaRepoCreateRequest(repoRequest)
    }

    private fun createFederatedClusterAndSaveConfig(request: FederatedRepositoryConfigRequest) {
        with(request) {
            logger.info("Start to sync federation config for project: $projectId, repo: $repoName")
            val selfCluster = getOrCreateCluster(selfCluster)
            val fedList = buildFederatedClusterList(key, projectId, repoName, federatedClusters)
            saveFederationConfig(name, key, projectId, repoName, selfCluster.id!!, fedList)
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
        key: String,
        projectId: String,
        repoName: String,
        federatedClusters: List<FederatedClusterInfo>,
    ): MutableList<FederatedCluster> {
        logger.info("Starting to build federated cluster list for project: $projectId, repo: $repoName")
        return federatedClusters.mapNotNull { it ->
            val federatedCluster = getOrCreateCluster(it.clusterNodeInfo)
            val fed = FederatedCluster(it.projectId, it.repoName, federatedCluster.id!!, it.enabled)
            val taskId = createOrUpdateTask(key, projectId, repoName, fed, federatedCluster)
            fed.taskId = taskId
            fed
        }.toMutableList()
    }

    private fun saveFederationConfig(
        name: String,
        key: String,
        projectId: String,
        repoName: String,
        clusterId: String,
        fedList: MutableList<FederatedCluster>,
    ) {
        val request = buildTFederatedRepository(
            name = name,
            key = key,
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
        key: String,
        projectId: String,
        repoName: String,
        federatedCluster: FederatedCluster,
        clusterInfo: ClusterNodeInfo,
    ): String {
        val repositoryDetail = localDataManager.findRepoByName(projectId, repoName)
        val replicaTaskObjects = buildReplicaTaskObjects(
            repoName, repositoryDetail.type, federatedCluster
        )
        val sourceRepo = projectId + StringPool.DASH + repoName
        val taskName = FEDERATION_TASK_NAME.format(key, sourceRepo, clusterInfo.name)
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
        logger.info(
            "Successfully created federation task: $taskName" +
                " with key $key for repo: $projectId|$repoName to cluster: ${clusterInfo.name}"
        )
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
        key: String,
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
            key = key,
            name = request.name
        )
    }

    private fun buildTFederatedRepository(
        name: String,
        key: String,
        projectId: String,
        repoName: String,
        federatedClusters: List<FederatedCluster>,
        clusterId: String,
    ): TFederatedRepository {
        return TFederatedRepository(
            name = name,
            key = key,
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

    private fun convertToFederatedRepositoryInfo(tFederatedRepository: TFederatedRepository): FederatedRepositoryInfo {
        return FederatedRepositoryInfo(
            key = tFederatedRepository.key,
            name = tFederatedRepository.name,
            projectId = tFederatedRepository.projectId,
            repoName = tFederatedRepository.repoName,
            clusterId = tFederatedRepository.clusterId,
            federatedClusters = tFederatedRepository.federatedClusters
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationRepositoryServiceImpl::class.java)
        const val FEDERATION_TASK_NAME = "federation/%s/%s/%s"

    }
}