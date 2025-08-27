/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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
import com.tencent.bkrepo.common.api.util.AsyncUtils.trace
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.lock.service.LockOperation
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
import com.tencent.bkrepo.replication.pojo.cluster.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.cluster.request.ClusterNodeCreateRequest
import com.tencent.bkrepo.replication.pojo.cluster.request.DetectType
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.FederatedClusterInfo
import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryConfigRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryCreateRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskUpdateRequest
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import com.tencent.bkrepo.replication.replica.executor.FederationFullSyncThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.type.federation.FederationManualReplicaJobExecutor
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
    private val lockOperation: LockOperation,
    private val federationManualReplicaJobExecutor: FederationManualReplicaJobExecutor,
) : FederationRepositoryService {

    private val executor = FederationFullSyncThreadPoolExecutor.instance

    override fun createFederationRepository(request: FederatedRepositoryCreateRequest): String {
        // TODO 需要校验用户是有有目标仓库的权限
        logger.info("Creating federation repository for project: ${request.projectId}, repo: ${request.repoName}")
        paramsCheck(request)
        val currentCluster = clusterNodeService.getByClusterId(request.clusterId)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, request.clusterId)
        try {
            val federationId = uniqueId()
            val taskMap = doFederatedRepositoryCreate(request, currentCluster, federationId)
            val tFederatedRepository = buildTFederatedRepository(request, taskMap, federationId)
            federatedRepositoryDao.save(tFederatedRepository)
            logger.info(
                "Successfully created federation repository for repo:" +
                    " ${request.projectId}|${request.repoName} with key: $federationId"
            )
            return federationId
        } catch (e: Exception) {
            logger.warn("Failed to create federation repository, request: ${e.message}")
            throw ErrorCodeException(ReplicationMessageCode.FEDERATION_REPOSITORY_CREATE_ERROR, e.message.orEmpty())
        }
    }

    override fun saveFederationRepositoryConfig(request: FederatedRepositoryConfigRequest): Boolean {
        return createFederatedClusterAndSaveConfig(request)
    }

    override fun listFederationRepository(
        projectId: String, repoName: String, federationId: String?,
    ): List<FederatedRepositoryInfo> {
        return federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).map {
            convertToFederatedRepositoryInfo(it)
        }
    }

    override fun deleteFederationRepositoryConfig(projectId: String, repoName: String, federationId: String) {
        deleteConfig(projectId, repoName, federationId, true)
    }

    override fun deleteLocalFederationRepositoryConfig(projectId: String, repoName: String, federationId: String) {
        deleteConfig(projectId, repoName, federationId, false)
    }

    override fun getCurrentClusterName(projectId: String, repoName: String, federationId: String): String? {
        return federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).firstOrNull()?.let {
            clusterNodeService.getByClusterId(it.clusterId)?.name
        }
    }

    override fun fullSyncFederationRepository(projectId: String, repoName: String, federationId: String) {
        val federationRepository = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId)
            .firstOrNull() ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, federationId)

        val lock = getLock(projectId, repoName, federationId) ?: {
            logger.info("Full sync federation repository is still running!")
            throw ErrorCodeException(ReplicationMessageCode.FEDERATION_REPOSITORY_FULL_SYNC_RUNNING)
        }
        try {
            // TODO 这里锁释放的时候任务还没执行完成
            // 使用线程池异步执行同步任务
            executor.submit {
                Runnable {
                    try {
                        // TODO 多个任务并发执行
                        federationRepository.federatedClusters.forEach {
                            val taskInfo = replicaTaskService.getByTaskId(it.taskId!!)
                            taskInfo?.let { task ->
                                val taskDetail = replicaTaskService.getDetailByTaskKey(task.key)
                                federationManualReplicaJobExecutor.execute(taskDetail)
                            }
                        }
                    } finally {
                        unlock(projectId, repoName, federationId, lock)
                        logger.info("Released lock for federation sync: $federationId")
                    }
                }.trace()
            }
        } catch (ignore: Exception) {
            logger.error("Failed to full sync federation repository, error: ${ignore.message}")
            unlock(projectId, repoName, federationId, lock)
            throw ErrorCodeException(ReplicationMessageCode.FEDERATION_REPOSITORY_FULL_SYNC_FAILED)
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
        federationId: String,
        deleteRemote: Boolean = false,
    ) {
        val federationRepository = federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId)
            .firstOrNull() ?: return
        federationRepository.federatedClusters.forEach {
            if (deleteRemote) {
                deleteRemoteFederationConfig(federationId, it)
            }
            val task = replicaTaskService.getByTaskId(it.taskId!!)
            task?.let {
                replicaTaskService.deleteByTaskKey(task.key)
            }
        }
        federatedRepositoryDao.deleteByProjectIdAndRepoName(projectId, repoName, federationId)
    }

    private fun doFederatedRepositoryCreate(
        request: FederatedRepositoryCreateRequest,
        currentCluster: ClusterNodeInfo,
        federationId: String,
    ): Map<String, String> {
        val taskMap = mutableMapOf<String, String>()
        val federatedNameList = getClusterNames(request.federatedClusters)
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
//            syncFederationConfig(
//                request.name, federationId, fed.projectId, fed.repoName, remoteClusterNode, federatedClusters
//            )
            // 生成本地同步task
            val taskId = createOrUpdateTask(
                federationId, request.projectId, request.repoName, fed, remoteClusterNode, federatedNameList
            )
            taskMap[fed.clusterId] = taskId
        }
        return taskMap
    }

    private fun getClusterNames(federatedClusters: List<FederatedCluster>): List<String> {
        return federatedClusters
            .mapNotNull { fed ->
                clusterNodeService.getByClusterId(fed.clusterId)?.name
            }
            .distinct()
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
        val federatedClusterInfos = federatedClusters.map {
            val federatedClusterInfo = clusterNodeService.getByClusterId(it.clusterId)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, it.clusterId)
            FederatedClusterInfo(
                projectId = it.projectId,
                repoName = it.repoName,
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
                enabled = it.enabled
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
        federatedRepositoryClient.createFederatedConfig(configRequest).data ?: {
            val msg = "Failed to sync federation config with federationId $federationId "
            throw ErrorCodeException(ReplicationMessageCode.FEDERATION_REPOSITORY_CREATE_ERROR, msg)
        }
        logger.info(
            "Successfully synced federation config with federationId $federationId " +
                "for repo: $projectId|$repoName to cluster ${cluster.name}"
        )
    }

    fun getLock(
        projectId: String,
        repoName: String,
        federationId: String,
        keyPrefix: String = FEDERATION_FULL_SYNC_KEY_PREFIX,
    ): Any? {
        val lockKey = "$keyPrefix$projectId/$repoName/$federationId"
        val lock = lockOperation.getLock(lockKey)
        return if (lockOperation.acquireLock(lockKey = lockKey, lock = lock)) {
            logger.info("Lock for key $lockKey has been acquired.")
            lock
        } else {
            null
        }
    }

    fun unlock(
        projectId: String,
        repoName: String,
        federationId: String,
        lock: Any,
        keyPrefix: String = FEDERATION_FULL_SYNC_KEY_PREFIX,
    ) {
        val lockKey = "$keyPrefix$projectId/$repoName/$federationId"
        lockOperation.close(lockKey, lock)
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
        artifactReplicaClient.replicaProjectCreateRequest(projectRequest).data ?: {
            throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_CREATE_ERROR,
                "Failed to create remote project ${remoteProjectId}"
            )
        }
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
        artifactReplicaClient.replicaRepoCreateRequest(repoRequest).data ?: {
            throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_CREATE_ERROR,
                "Failed to create remote repo ${remoteProjectId}|$remoteRepoName"
            )
        }
    }

    private fun createFederatedClusterAndSaveConfig(request: FederatedRepositoryConfigRequest): Boolean {
        with(request) {
            logger.info("Start to sync federation config for project: $projectId, repo: $repoName")
            val selfCluster = getOrCreateCluster(
                RemoteClusterInfo(
                    id = selfCluster.id,
                    name = selfCluster.name,
                    url = selfCluster.url,
                    username = selfCluster.username,
                    password = selfCluster.password,
                    certificate = selfCluster.certificate,
                    accessKey = selfCluster.accessKey,
                    secretKey = selfCluster.secretKey,
                    appId = selfCluster.appId
                )
            )
            val fedList = buildFederatedClusterList(federationId, projectId, repoName, federatedClusters)
            saveFederationConfig(name, federationId, projectId, repoName, selfCluster.id!!, fedList)
            logger.info("Successfully synced federation config for project: $projectId, repo: $repoName")
            return true
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

    private fun getOrCreateCluster(cluster: RemoteClusterInfo): ClusterNodeInfo {
        // TODO 通过host+name确认唯一clusterNode
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
        federationId: String,
        projectId: String,
        repoName: String,
        federatedClusters: List<FederatedClusterInfo>,
    ): MutableList<FederatedCluster> {
        logger.info("Starting to build federated cluster list for project: $projectId, repo: $repoName")
        val clusterNameList = federatedClusters.mapNotNull { it.clusterNodeInfo?.name }.distinct()
        return federatedClusters.mapNotNull { it ->
            val federatedCluster = getOrCreateCluster(it.clusterNodeInfo)
            val fed = FederatedCluster(it.projectId, it.repoName, federatedCluster.id!!, it.enabled)
            val taskId = createOrUpdateTask(federationId, projectId, repoName, fed, federatedCluster, clusterNameList)
            fed.taskId = taskId
            fed
        }.toMutableList()
    }

    private fun saveFederationConfig(
        name: String,
        federationId: String,
        projectId: String,
        repoName: String,
        clusterId: String,
        fedList: MutableList<FederatedCluster>,
    ) {
        val request = buildTFederatedRepository(
            name = name,
            federationId = federationId,
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
        federationId: String,
        projectId: String,
        repoName: String,
        federatedCluster: FederatedCluster,
        clusterInfo: ClusterNodeInfo,
        federatedNameList: List<String>,
    ): String {
        val repositoryDetail = localDataManager.findRepoByName(projectId, repoName)
        val replicaTaskObjects = buildReplicaTaskObjects(
            repoName, repositoryDetail.type, federatedCluster, federatedNameList
        )
        val sourceRepo = projectId + StringPool.DASH + repoName
        val taskName = FEDERATION_TASK_NAME.format(federationId, sourceRepo, clusterInfo.name)
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
                " with federationId $federationId for repo: $projectId|$repoName to cluster: ${clusterInfo.name}"
        )
        return task!!.id
    }

    private fun buildReplicaTaskObjects(
        repoName: String,
        repoType: RepositoryType,
        federatedCluster: FederatedCluster,
        federatedNameList: List<String>,
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
                    pathConstraints = null,
                    sourceFilter = federatedNameList
                )
            )
            return taskObjects
        }
    }

    private fun buildClusterNodeCreateRequest(cluster: RemoteClusterInfo): ClusterNodeCreateRequest {
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

    /**
     * 构建联邦仓库实体对象
     * @param request 创建联邦仓库请求参数
     * @param taskMap 集群ID与任务ID的映射关系
     * @return 构建好的联邦仓库实体
     */
    private fun buildTFederatedRepository(
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

    private fun buildTFederatedRepository(
        name: String,
        federationId: String,
        projectId: String,
        repoName: String,
        federatedClusters: List<FederatedCluster>,
        clusterId: String,
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

    private fun convertToFederatedRepositoryInfo(tFederatedRepository: TFederatedRepository): FederatedRepositoryInfo {
        return FederatedRepositoryInfo(
            federationId = tFederatedRepository.federationId,
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
        const val FEDERATION_FULL_SYNC_KEY_PREFIX = "replication:lock:fullSync:"

    }
}