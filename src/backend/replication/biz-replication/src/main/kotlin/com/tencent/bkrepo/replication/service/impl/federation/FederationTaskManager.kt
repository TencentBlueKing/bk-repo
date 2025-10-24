package com.tencent.bkrepo.replication.service.impl.federation

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.request.ReplicaObjectType
import com.tencent.bkrepo.replication.pojo.request.ReplicaType
import com.tencent.bkrepo.replication.pojo.task.objects.ReplicaObjectInfo
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskCreateRequest
import com.tencent.bkrepo.replication.pojo.task.request.ReplicaTaskUpdateRequest
import com.tencent.bkrepo.replication.pojo.task.setting.ConflictStrategy
import com.tencent.bkrepo.replication.pojo.task.setting.ReplicaSetting
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.ReplicaTaskService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 联邦任务管理器
 * 负责联邦同步任务的创建和更新
 */
@Component
class FederationTaskManager(
    private val replicaTaskService: ReplicaTaskService,
    private val localDataManager: LocalDataManager,
    private val clusterNodeService: ClusterNodeService,
) {

    /**
     * 创建或更新联邦同步任务
     */
    fun createOrUpdateTask(
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
        val targetRepo = federatedCluster.projectId + StringPool.DASH + federatedCluster.repoName
        val taskName = FEDERATION_TASK_NAME.format(federationId, sourceRepo, targetRepo, clusterInfo.name)
        var task = replicaTaskService.getByTaskName(taskName)
        if (task != null) {
            replicaTaskService.deleteByTaskKey(task.key)
            val taskUpdateRequest = ReplicaTaskUpdateRequest(
                key = task.key,
                name = taskName,
                localProjectId = projectId,
                replicaObjectType = ReplicaObjectType.REPOSITORY,
                replicaTaskObjects = replicaTaskObjects,
                setting = ReplicaSetting(conflictStrategy = ConflictStrategy.OVERWRITE),
                remoteClusterIds = setOf(clusterInfo.id!!),
            )
            task = replicaTaskService.update(taskUpdateRequest)
        } else {
            logger.info("Creating new federation task: $taskName")
            val taskCreateRequest = ReplicaTaskCreateRequest(
                name = taskName,
                localProjectId = projectId,
                replicaObjectType = ReplicaObjectType.REPOSITORY,
                replicaTaskObjects = replicaTaskObjects,
                replicaType = ReplicaType.FEDERATION,
                setting = ReplicaSetting(conflictStrategy = ConflictStrategy.OVERWRITE),
                remoteClusterIds = setOf(clusterInfo.id!!),
                enabled = federatedCluster.enabled
            )
            task = replicaTaskService.create(taskCreateRequest)
        }
        logger.info(
            "Successfully created federation task: $taskName" +
                " with federationId $federationId for repo: $projectId|$repoName to cluster: ${clusterInfo.name}"
        )
        return task!!.id
    }

    /**
     * 获取集群信息映射，避免重复查询
     * @return Map<clusterId, ClusterNodeInfo>
     */
    fun getClusterInfoMap(federatedClusters: List<FederatedCluster>): Map<String, ClusterNodeInfo> {
        return federatedClusters.associate { fed ->
            val clusterInfo = clusterNodeService.getByClusterId(fed.clusterId)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, fed.clusterId)
            fed.clusterId to clusterInfo
        }
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

    /**
     * 删除联邦集群相关的同步任务
     */
    fun deleteFederationTasks(federatedClusters: List<FederatedCluster>) {
        federatedClusters.forEach { fedCluster ->
            fedCluster.taskId?.let { taskId ->
                val task = replicaTaskService.getByTaskId(taskId)
                task?.let {
                    logger.info("Deleting federation task: ${task.key} (id: ${task.id})")
                    replicaTaskService.deleteByTaskKey(task.key)
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationTaskManager::class.java)
        const val FEDERATION_TASK_NAME = "federation/%s/%s/%s/%s"
    }
}