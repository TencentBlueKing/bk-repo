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
import com.tencent.bkrepo.replication.pojo.federation.FederatedCluster
import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryConfigRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryCreateRequest
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
            val taskMap = doFederatedRepositoryCreate(request, currentCluster, federationId)
            val tFederatedRepository = buildTFederatedRepository(request, taskMap, federationId)
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

    override fun removeClusterFromFederation(
        projectId: String,
        repoName: String,
        federationId: String,
        remoteClusterName: String,
        deleteRemote: Boolean
    ) {
        val clusterId = localFederationManager.getClusterIdByName(remoteClusterName)
        val federationRepository = localFederationManager.getFederationRepository(projectId, repoName, federationId)
            ?: return

        // 检查是否存在要移除的集群
        val targetCluster = federationRepository.federatedClusters.find { it.clusterId == clusterId }
            ?: return

        try {
            // 如果只剩一个集群，直接删除整个联邦配置
            if (federationRepository.federatedClusters.size <= 1) {
                logger.info("Only one cluster left in federation, deleting entire federation configuration")
                deleteFederationRepositoryConfig(projectId, repoName, federationId, deleteRemote)
                return
            }
            // 获取剩余集群列表
            val remainingClusters = federationRepository.federatedClusters.filter { it.clusterId != clusterId }

            if (deleteRemote) {
                //  删除目标集群上的联邦配置
                logger.info("Deleting federation config on target cluster: $clusterId")
                remoteFederationManager.deleteRemoteFederationConfig(federationId, targetCluster)


                // 删除其他集群上到应目标集群的同步配置
                logger.info("Deleting remote tasks for target cluster: $clusterId on remaining clusters")
                remoteFederationManager.deleteRemoteConfigForTargetCluster(federationId, clusterId, remainingClusters)
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

    private fun paramsCheck(request: FederatedRepositoryCreateRequest) {
        require(request.federatedClusters.isNotEmpty()) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "federatedClusters")
        }
    }

    private fun deleteConfig(
        projectId: String,
        repoName: String,
        federationId: String,
        deleteRemote: Boolean = false,
    ) {
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
    }

    private fun doFederatedRepositoryCreate(
        request: FederatedRepositoryCreateRequest,
        currentCluster: com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeInfo,
        federationId: String,
    ): Map<String, String> {
        val taskMap = mutableMapOf<String, String>()
        // 一次性获取所有cluster信息，避免重复查询
        val clusterInfoMap = federationTaskManager.getClusterInfoMap(request.federatedClusters)
        val federatedNameList = clusterInfoMap.values.map { it.name }.distinct()

        request.federatedClusters.forEach { fed ->
            // 直接从缓存的map中获取cluster信息，避免重复查询
            val remoteClusterNode = clusterInfoMap[fed.clusterId]!!
            val federatedClusters = request.federatedClusters
                .filter { it.clusterId != fed.clusterId }
                .plus(FederatedCluster(request.projectId, request.repoName, currentCluster.id!!, true))

            // 在远程集群创建项目和仓库
            remoteFederationManager.createRemoteProjectAndRepo(
                request.projectId, request.repoName, fed.projectId, fed.repoName, remoteClusterNode, currentCluster
            )

            // 在目标集群生成对应cluster以及对应同步task
            remoteFederationManager.syncFederationConfig(
                request.name, federationId, fed.projectId, fed.repoName, remoteClusterNode, federatedClusters
            )

            // 生成本地同步task
            val taskId = federationTaskManager.createOrUpdateTask(
                federationId, request.projectId, request.repoName, fed, remoteClusterNode, federatedNameList
            )
            taskMap[fed.clusterId] = taskId
        }
        return taskMap
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationRepositoryServiceImpl::class.java)
    }
}