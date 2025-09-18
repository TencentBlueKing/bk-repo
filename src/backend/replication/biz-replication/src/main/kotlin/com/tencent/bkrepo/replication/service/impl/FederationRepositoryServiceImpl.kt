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

import com.tencent.bkrepo.common.api.constant.StringPool.uniqueId
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.model.TFederatedRepository
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryConfigRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryCreateRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryDeleteRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryUpdateRequest
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.FederationRepositoryService
import com.tencent.bkrepo.replication.service.impl.federation.FederationSyncManager
import com.tencent.bkrepo.replication.service.impl.federation.FederationTaskManager
import com.tencent.bkrepo.replication.service.impl.federation.LocalFederationManager
import com.tencent.bkrepo.replication.service.impl.federation.RemoteFederationManager
import com.tencent.bkrepo.replication.util.FederationDataBuilder.buildTFederatedRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FederationRepositoryServiceImpl(
    private val localFederationManager: LocalFederationManager,
    private val remoteFederationManager: RemoteFederationManager,
    private val federationTaskManager: FederationTaskManager,
    private val federationSyncManager: FederationSyncManager,
    private val clusterNodeService: ClusterNodeService,
) : FederationRepositoryService {

    override fun createFederationRepository(request: FederatedRepositoryCreateRequest): String {
        // TODO 需要校验用户是有有目标仓库的权限
        logger.info("Creating federation repository for project: ${request.projectId}, repo: ${request.repoName}")
        paramsCheck(request)
        val currentCluster = clusterNodeService.getByClusterId(request.clusterId)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, request.clusterId)
        try {
            val federationId = uniqueId()
            val federatedClusterList = doFederatedRepositoryCreate(request, currentCluster, federationId)
            val tFederatedRepository = buildTFederatedRepository(request, federatedClusterList, federationId)
            localFederationManager.saveFederationRepository(tFederatedRepository)
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
        return localFederationManager.saveFederationRepositoryConfig(request)
    }

    override fun listFederationRepository(
        projectId: String, repoName: String, federationId: String?,
    ): List<FederatedRepositoryInfo> {
        return localFederationManager.listFederationRepository(projectId, repoName, federationId)
    }

    override fun deleteFederationRepositoryConfig(
        projectId: String,
        repoName: String,
        federationId: String,
        deleteRemote: Boolean
    ) {
        deleteConfig(projectId, repoName, federationId, deleteRemote)
    }

    override fun removeClusterFromFederation(request: FederatedRepositoryDeleteRequest) {
        request.federatedClusters?.forEach { fed ->
            val fedClusterInfo = clusterNodeService.getByClusterId(fed.clusterId)
                ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, fed.clusterId)
            removeClusterFromFederation(
                projectId = request.projectId,
                repoName = request.repoName,
                federationId = request.federationId,
                remoteClusterName = fedClusterInfo.name,
                remoteProjectId = fed.projectId,
                remoteRepoName = fed.repoName,
                deleteRemote = true
            )
        }
    }

    override fun removeClusterFromFederation(
        projectId: String,
        repoName: String,
        federationId: String,
        remoteClusterName: String,
        remoteProjectId: String,
        remoteRepoName: String,
        deleteRemote: Boolean
    ) {
        val clusterId = localFederationManager.getClusterIdByName(remoteClusterName)
        val federationRepository = localFederationManager.getFederationRepository(projectId, repoName, federationId)
            ?: return

        // 检查是否存在要移除的集群
        val targetCluster = federationRepository.federatedClusters.find {
            it.clusterId == clusterId && it.projectId == remoteProjectId && it.repoName == remoteRepoName
        } ?: return

        try {
            // 获取剩余集群列表
            val remainingClusters = federationRepository.federatedClusters.filterNot {
                it.clusterId == clusterId && it.projectId == remoteProjectId && it.repoName == remoteRepoName
            }

            if (deleteRemote) {
                //  删除目标集群上的联邦配置
                logger.info("Deleting federation config on target cluster: $clusterId")
                remoteFederationManager.deleteRemoteFederationConfig(federationId, targetCluster)

                // 删除其他集群上到对应目标集群的同步配置
                logger.info(
                    "Deleting remote tasks for target cluster: $clusterId " +
                        "with $remoteProjectId|$remoteRepoName on remaining clusters"
                )
                remoteFederationManager.deleteRemoteConfigForTargetCluster(
                    federationId = federationId,
                    targetClusterName = remoteClusterName,
                    targetProjectId = remoteProjectId,
                    targetRepoName = remoteRepoName,
                    remainingClusters = remainingClusters
                )
            }

            // 删除本集群上到目标集群的同步任务
            logger.info("Deleting federation tasks for cluster: $clusterId")
            federationTaskManager.deleteFederationTasks(listOf(targetCluster))

            // 从本地联邦配置中移除该集群
            localFederationManager.updateFederationClusters(projectId, repoName, federationId, remainingClusters)

            logger.info(
                "Successfully removed cluster $clusterId from federation $federationId for repo: $projectId|$repoName"
            )
            return
        } catch (e: Exception) {
            throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_DELETE_ERROR,
                "Failed to remove cluster from federation: ${e.message}"
            )
        }
    }

    override fun getCurrentClusterName(projectId: String, repoName: String, federationId: String): String? {
        return localFederationManager.getCurrentClusterName(projectId, repoName, federationId)
    }

    override fun fullSyncFederationRepository(projectId: String, repoName: String, federationId: String) {
        federationSyncManager.executeFullSync(projectId, repoName, federationId)
    }

    override fun updateFederationRepository(request: FederatedRepositoryUpdateRequest): Boolean {
        logger.info(
            "Updating federation repository for repo: ${request.projectId}|${request.repoName}, " +
                "federationId: ${request.federationId}"
        )

        if (request.federatedClusters.isNullOrEmpty()) return true
        federatedClustersCheck(request.federatedClusters!!)

        // 获取现有的联邦配置
        val existingFederation = localFederationManager.getFederationRepository(
            request.projectId, request.repoName, request.federationId
        ) ?: throw ErrorCodeException(
            ReplicationMessageCode.FEDERATION_REPOSITORY_NOT_FOUND,
            "Federation repository not found: ${request.federationId}"
        )

        try {
            // 更新联邦集群配置
            val currentClusters = existingFederation.federatedClusters
            val newClusters = request.federatedClusters!!

            // 检查集群配置是否有变化（包括集群ID和配置属性）
            val hasClusterChanges = detectClusterChanges(currentClusters, newClusters)

            return if (hasClusterChanges) {
                // 处理集群变更
                updateFederationClusters(existingFederation, newClusters, request.federationId)
                logger.info("Successfully updated federation repository: ${request.federationId}")
                true
            } else {
                logger.info("No cluster changes detected for federation repository: ${request.federationId}")
                false
            }
        } catch (e: ErrorCodeException) {
            // 重新抛出业务异常
            logger.warn("Business error updating federation repository ${request.federationId}: ${e.message}")
            throw e
        } catch (e: Exception) {
            // 处理系统异常
            logger.error("System error updating federation repository ${request.federationId}", e)
            throw ErrorCodeException(
                ReplicationMessageCode.FEDERATION_REPOSITORY_UPDATE_ERROR,
                "Failed to update federation repository: ${e.message}"
            )
        }
    }

    /**
     * 检测集群配置是否有变化
     * 不仅检查集群ID，还检查集群的其他配置属性
     */
    private fun detectClusterChanges(
        currentClusters: List<FederatedCluster>,
        newClusters: List<FederatedCluster>
    ): Boolean {
        // 快速检查：数量不同
        if (currentClusters.size != newClusters.size) {
            return true
        }

        // 创建映射以便快速查找和比较
        val currentClusterMap = currentClusters.associateBy { it.clusterId }
        val newClusterMap = newClusters.associateBy { it.clusterId }

        // 检查集群ID是否有变化
        if (currentClusterMap.keys != newClusterMap.keys) {
            return true
        }

        // 检查每个集群的配置是否有变化
        return currentClusterMap.any { (clusterId, currentCluster) ->
            val newCluster = newClusterMap[clusterId]!!
            !areClusterConfigsEqual(currentCluster, newCluster)
        }
    }

    /**
     * 比较两个集群配置是否相等
     * 可以根据需要扩展比较的属性
     */
    private fun areClusterConfigsEqual(cluster1: FederatedCluster, cluster2: FederatedCluster): Boolean {
        return cluster1.clusterId == cluster2.clusterId &&
            cluster1.projectId == cluster2.projectId &&
            cluster1.repoName == cluster2.repoName
    }

    private fun paramsCheck(request: FederatedRepositoryCreateRequest) {
        require(request.federatedClusters.isNotEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "federatedClusters")
        }
        federatedClustersCheck(request.federatedClusters)
        // 检查联邦仓库名称是否已存在
        if (localFederationManager.isFederationNameExists(request.name)) {
            throw ErrorCodeException(ReplicationMessageCode.FEDERATION_REPOSITORY_NAME_EXISTS, request.name)
        }
    }

    private fun federatedClustersCheck(federatedClusters: List<FederatedCluster>) {
        // 检查是否有重复的集群配置
        val clusterKeys = federatedClusters.map { it.toKey() }
        if (clusterKeys.distinct().size != clusterKeys.size) {
            throw ErrorCodeException(
                CommonMessageCode.PARAMETER_INVALID, "Duplicate federated clusters found"
            )
        }
    }


    private fun deleteConfig(
        projectId: String,
        repoName: String,
        federationId: String,
        deleteRemote: Boolean = false,
    ) {
        try {
            val federationRepository = localFederationManager.getFederationRepository(projectId, repoName, federationId)
                ?: return

            // 删除远程配置（如果需要）
            if (deleteRemote) {
                federationRepository.federatedClusters.forEach { fedCluster ->
                    remoteFederationManager.deleteRemoteFederationConfig(federationId, fedCluster)
                }
            }

            // 删除联邦同步任务
            federationTaskManager.deleteFederationTasks(federationRepository.federatedClusters)

            // 删除本地联邦配置
            localFederationManager.deleteConfig(projectId, repoName, federationId)

            logger.info("Successfully deleted federation repository config: $projectId|$repoName|$federationId")
        } catch (e: Exception) {
            logger.error("Failed to delete federation repository config: $projectId|$repoName|$federationId", e)
            throw ErrorCodeException(ReplicationMessageCode.FEDERATION_REPOSITORY_DELETE_ERROR, e.message.orEmpty())
        }
    }

    private fun doFederatedRepositoryCreate(
        request: FederatedRepositoryCreateRequest,
        currentCluster: ClusterNodeInfo,
        federationId: String,
    ): List<FederatedCluster> {
        // 一次性获取所有cluster信息，避免重复查询
        val clusterInfoMap = federationTaskManager.getClusterInfoMap(request.federatedClusters)
        val federatedNameList = clusterInfoMap.values.map { it.name }.distinct()

        val newFederatedClusters = mutableListOf<FederatedCluster>()
        request.federatedClusters.forEach { fed ->
            // 直接从缓存的map中获取cluster信息，避免重复查询
            val remoteClusterNode = clusterInfoMap[fed.clusterId]!!
            val federatedClusters = request.federatedClusters
                .filterNot {
                    it.clusterId == fed.clusterId && it.projectId == fed.projectId && it.repoName == fed.repoName
                }.plus(FederatedCluster(request.projectId, request.repoName, currentCluster.id!!, true))

            val taskId = setupFederationForCluster(
                request.name, federationId, request.projectId, request.repoName,
                fed, federatedClusters, currentCluster, remoteClusterNode, federatedNameList
            )
            newFederatedClusters.add(
                FederatedCluster(
                    projectId = fed.projectId,
                    repoName = fed.repoName,
                    clusterId = fed.clusterId,
                    enabled = fed.enabled,
                    taskId = taskId
                )
            )
        }
        return newFederatedClusters
    }

    /**
     * 为单个集群设置联邦配置
     */
    private fun setupFederationForCluster(
        federationName: String,
        federationId: String,
        currentProjectId: String,
        currentRepoName: String,
        targetCluster: FederatedCluster,
        allClusters: List<FederatedCluster>,
        currentCluster: ClusterNodeInfo,
        remoteClusterNode: ClusterNodeInfo,
        federatedNameList: List<String>
    ): String {
        // 在远程集群创建项目和仓库
        remoteFederationManager.createRemoteProjectAndRepo(
            currentProjectId, currentRepoName,
            targetCluster.projectId, targetCluster.repoName,
            remoteClusterNode, currentCluster
        )

        // 在目标集群生成对应cluster以及对应同步task
        remoteFederationManager.syncFederationConfig(
            federationName, federationId,
            targetCluster.projectId, targetCluster.repoName,
            remoteClusterNode, allClusters
        )

        // 生成本地同步task
        return federationTaskManager.createOrUpdateTask(
            federationId, currentProjectId, currentRepoName,
            targetCluster, remoteClusterNode, federatedNameList
        )
    }

    /**
     * 更新联邦集群配置
     */
    private fun updateFederationClusters(
        existingFederation: TFederatedRepository,
        newClusters: List<FederatedCluster>,
        federationId: String
    ) {
        val currentClusters = existingFederation.federatedClusters

        // 计算集群变更
        val (clustersToKeep, clustersToRemove, clustersToAdd) =
            calculateClusterChanges(currentClusters, newClusters)

        val newFederatedClusters = clustersToKeep.toMutableList()

        // 处理移除集群
        processRemovedClusters(
            existingFederation,
            federationId,
            clustersToRemove
        )

        // 处理新增集群
        if (clustersToAdd.isNotEmpty()) {
            processNewClusters(
                existingFederation,
                federationId,
                clustersToAdd,
                newClusters,
                newFederatedClusters
            )
        }

        // 更新本地配置
        localFederationManager.updateFederationClusters(
            existingFederation.projectId,
            existingFederation.repoName,
            federationId,
            newFederatedClusters
        )

    }


    // 新增扩展函数
    private fun FederatedCluster.toKey() = Triple(clusterId, projectId, repoName)

    // 提取集群比较逻辑
    private fun calculateClusterChanges(
        currentClusters: List<FederatedCluster>,
        newClusters: List<FederatedCluster>
    ): Triple<List<FederatedCluster>, List<FederatedCluster>, List<FederatedCluster>> {
        val currentKeys = currentClusters.map { it.toKey() }.toSet()
        val newKeys = newClusters.map { it.toKey() }.toSet()

        val clustersToKeep = currentClusters.filter { it.toKey() in newKeys }
        val clustersToRemove = currentClusters.filter { it.toKey() !in newKeys }
        val clustersToAdd = newClusters.filter { it.toKey() !in currentKeys }

        return Triple(clustersToKeep, clustersToRemove, clustersToAdd)
    }

    // 新增集群处理函数
    private fun processNewClusters(
        existingFederation: TFederatedRepository,
        federationId: String,
        clustersToAdd: List<FederatedCluster>,
        allNewClusters: List<FederatedCluster>,
        resultList: MutableList<FederatedCluster>
    ) {
        logger.info("Adding ${clustersToAdd.size} new clusters to federation $federationId")

        val currentCluster = clusterNodeService.getByClusterId(existingFederation.clusterId)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, existingFederation.clusterId)

        val clusterInfoMap = federationTaskManager.getClusterInfoMap(clustersToAdd)
        val federatedNameList = clusterInfoMap.values.map { it.name }.distinct()

        clustersToAdd.forEach { addCluster ->
            val remoteClusterNode = clusterInfoMap[addCluster.clusterId]!!
            val otherClusters = allNewClusters
                .filterNot { it.toKey() == addCluster.toKey() }
                .plus(
                    FederatedCluster(
                        existingFederation.projectId,
                        existingFederation.repoName,
                        currentCluster.id!!,
                        true
                    )
                )

            val taskId = setupFederationForCluster(
                existingFederation.name, federationId,
                existingFederation.projectId, existingFederation.repoName,
                addCluster, otherClusters, currentCluster, remoteClusterNode, federatedNameList
            )

            resultList.add(
                FederatedCluster(
                    projectId = addCluster.projectId,
                    repoName = addCluster.repoName,
                    clusterId = addCluster.clusterId,
                    enabled = addCluster.enabled,
                    taskId = taskId
                )
            )
        }
    }

    // 移除集群处理函数
    private fun processRemovedClusters(
        existingFederation: TFederatedRepository,
        federationId: String,
        clustersToRemove: List<FederatedCluster>
    ) {
        clustersToRemove.forEach { cluster ->
            logger.info("Removing cluster ${cluster.clusterId} from federation $federationId")
            val clusterInfo = clusterNodeService.getByClusterId(cluster.clusterId)
            if (clusterInfo != null) {
                removeClusterFromFederation(
                    projectId = existingFederation.projectId,
                    repoName = existingFederation.repoName,
                    federationId = existingFederation.federationId,
                    remoteClusterName = clusterInfo.name,
                    remoteProjectId = cluster.projectId,
                    remoteRepoName = cluster.repoName,
                    deleteRemote = true
                )
            }
        }
    }


    companion object {
        private val logger = LoggerFactory.getLogger(FederationRepositoryServiceImpl::class.java)
    }
}