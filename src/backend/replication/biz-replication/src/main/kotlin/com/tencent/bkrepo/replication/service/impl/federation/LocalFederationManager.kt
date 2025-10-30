package com.tencent.bkrepo.replication.service.impl.federation

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.replication.dao.FederatedRepositoryDao
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.cluster.RemoteClusterInfo
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.FederatedClusterInfo
import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryConfigRequest
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import com.tencent.bkrepo.replication.util.FederationDataBuilder.buildClusterNodeCreateRequest
import com.tencent.bkrepo.replication.util.FederationDataBuilder.buildTFederatedRepository
import com.tencent.bkrepo.replication.util.FederationDataBuilder.convertToFederatedRepositoryInfo
import com.tencent.bkrepo.repository.constant.SYSTEM_USER
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 本地联邦管理器
 * 负责本地联邦仓库的创建、配置保存、删除等操作
 */
@Component
class LocalFederationManager(
    private val federatedRepositoryDao: FederatedRepositoryDao,
    private val clusterNodeService: ClusterNodeService,
    private val federationTaskManager: FederationTaskManager,
    private val replicaTaskService: ReplicaTaskService,
    private val replicaRecordService: ReplicaRecordService,
) {

    // taskId-recordId
    private val recordIdCache: Cache<String, String> = CacheBuilder.newBuilder().maximumSize(1000).build()

    /**
     * 保存联邦仓库配置
     */
    fun saveFederationRepositoryConfig(request: FederatedRepositoryConfigRequest): Boolean {
        return createFederatedClusterAndSaveConfig(request)
    }

    /**
     * 查询联邦仓库列表
     */
    fun listFederationRepository(
        projectId: String,
        repoName: String,
        federationId: String?
    ): List<FederatedRepositoryInfo> {
        return federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).map {
            convertToFederatedRepositoryInfo(it).apply {
                // 填充recordId
                federatedClusters.filter { cluster -> cluster.taskId != null }.forEach { cluster ->
                    val cachedRecordId = recordIdCache.getIfPresent(cluster.taskId!!)
                    if (cachedRecordId != null) {
                        cluster.recordId = cachedRecordId
                    } else {
                        val taskKey = replicaTaskService.getByTaskId(cluster.taskId!!)?.key ?: return@forEach
                        replicaRecordService.findLatestRecord(taskKey)?.id?.let { recordId ->
                            cluster.recordId = recordId
                            recordIdCache.put(cluster.taskId!!, recordId)
                        }
                    }
                }
            }
        }
    }

    /**
     * 删除联邦仓库配置
     */
    fun deleteConfig(
        projectId: String,
        repoName: String,
        federationId: String
    ) {
        federatedRepositoryDao.deleteByProjectIdAndRepoName(projectId, repoName, federationId)
    }

    /**
     * 获取当前集群名称
     */
    fun getCurrentClusterName(projectId: String, repoName: String, federationId: String): String? {
        return federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).firstOrNull()?.let {
            clusterNodeService.getByClusterId(it.clusterId)?.name
        }
    }

    fun getClusterIdByName(name: String): String {
        return clusterNodeService.getByClusterName(name)?.id
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, name)
    }

    /**
     * 保存联邦仓库实体
     */
    fun saveFederationRepository(tFederatedRepository: TFederatedRepository) {
        federatedRepositoryDao.save(tFederatedRepository)
    }

    /**
     * 检查是否正在全量同步
     */
    fun isFullSyncing(projectId: String, repoName: String, federationId: String): Boolean {
        return federatedRepositoryDao.isFullSyncing(projectId, repoName, federationId)
    }

    /**
     * 更新全量同步开始状态
     */
    fun updateFullSyncStart(projectId: String, repoName: String, federationId: String): Boolean {
        return federatedRepositoryDao.updateFullSyncStart(projectId, repoName, federationId)
    }

    /**
     * 更新全量同步结束状态
     */
    fun updateFullSyncEnd(projectId: String, repoName: String, federationId: String) {
        federatedRepositoryDao.updateFullSyncEnd(projectId, repoName, federationId)
    }

    /**
     * 根据联邦ID查询联邦仓库
     */
    fun getFederationRepository(projectId: String, repoName: String, federationId: String): TFederatedRepository? {
        return federatedRepositoryDao.findByProjectIdAndRepoName(projectId, repoName, federationId).firstOrNull()
    }

    /**
     * 更新联邦仓库的集群列表
     */
    fun updateFederationClusters(
        projectId: String,
        repoName: String,
        federationId: String,
        updatedClusters: List<FederatedCluster>
    ) {
        federatedRepositoryDao.updateFederatedClusters(projectId, repoName, federationId, updatedClusters)
    }

    /**
     * 检查联邦仓库名称是否已存在
     */
    fun isFederationNameExists(name: String): Boolean {
        return federatedRepositoryDao.findByName(name).isNotEmpty()
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
        return federatedClusters.map { clusterInfo ->
            val federatedCluster = getOrCreateCluster(clusterInfo.clusterNodeInfo)
            val fed = FederatedCluster(
                clusterInfo.projectId, clusterInfo.repoName, federatedCluster.id!!, clusterInfo.enabled
            )
            val taskId = federationTaskManager.createOrUpdateTask(
                federationId, projectId, repoName, fed, federatedCluster, clusterNameList
            )
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

    companion object {
        private val logger = LoggerFactory.getLogger(LocalFederationManager::class.java)
    }
}