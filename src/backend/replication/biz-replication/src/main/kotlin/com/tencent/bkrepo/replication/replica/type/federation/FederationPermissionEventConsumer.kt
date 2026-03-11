/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.replica.type.federation

import com.tencent.bkrepo.common.artifact.event.base.ArtifactEvent
import com.tencent.bkrepo.common.artifact.event.base.EventType
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.dao.FederatedRepositoryDao
import com.tencent.bkrepo.replication.pojo.request.ReplicaAction
import com.tencent.bkrepo.replication.pojo.request.TemporaryTokenReplicaRequest
import com.tencent.bkrepo.replication.replica.executor.FederationThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.replicator.standalone.FederationReplicator
import com.tencent.bkrepo.replication.replica.type.event.EventConsumer
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.FederationGroupService
import com.tencent.bkrepo.replication.util.FederationDataBuilder
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/**
 * 联邦集群权限/系统数据事件消费者，用于增量实时同步。
 *
 * 独立消费者，不依赖 ReplicaTask 体系：
 *  - 全局事件（用户、账号等）：遍历所有联邦组，向各远端集群推送单项变更
 *  - 项目级事件（角色、权限等）：按 projectId 查找联邦仓库，向相关集群推送单项变更
 */
@Component
class FederationPermissionEventConsumer(
    private val federationGroupService: FederationGroupService,
    private val federatedRepositoryDao: FederatedRepositoryDao,
    private val clusterNodeService: ClusterNodeService,
    private val federationReplicator: FederationReplicator,
) : EventConsumer() {

    private val executor = FederationThreadPoolExecutor.instance

    override fun getAcceptTypes(): Set<EventType> = GLOBAL_TYPES + PROJECT_TYPES

    override fun action(message: Message<ArtifactEvent>) {
        val event = message.payload
        val clusterIds = resolveTargetClusterIds(event)
        if (clusterIds.isEmpty()) return

        executor.execute {
            clusterIds.forEach { clusterId ->
                try {
                    val clusterNode = clusterNodeService.getByClusterId(clusterId) ?: run {
                        logger.warn("Cluster[$clusterId] not found, skipping event[${event.type}]")
                        return@forEach
                    }
                    val clusterInfo = FederationDataBuilder.buildClusterInfo(clusterNode)
                    val client = FeignClientFactory.create<ArtifactReplicaClient>(clusterInfo)
                    dispatchToCluster(client, event, clusterNode.name)
                } catch (e: Exception) {
                    logger.warn("Failed to sync event[${event.type}] to cluster[$clusterId]: ${e.message}")
                }
            }
        }
    }

    private fun resolveTargetClusterIds(event: ArtifactEvent): Set<String> {
        // projectId 为空字符串时（旧逻辑 null ?: "" 兜底）视为全局事件，同步到所有联邦集群
        return if (event.type in GLOBAL_TYPES || event.projectId.isNullOrEmpty()) {
            // 全局事件：遍历所有联邦组，收集各组中不是当前节点的远端 clusterId
            federationGroupService.listAll()
                .flatMap { group -> group.clusterIds.filter { it != group.currentClusterId } }
                .toSet()
        } else {
            // 项目级事件：查找该 project 下所有联邦仓库，收集其远端 clusterId
            federatedRepositoryDao.findByProjectId(event.projectId)
                .flatMap { repo -> repo.federatedClusters.map { it.clusterId } }
                .toSet()
        }
    }

    private fun dispatchToCluster(client: ArtifactReplicaClient, event: ArtifactEvent, clusterName: String) {
        when (event.type) {
            EventType.USER_CREATED, EventType.USER_UPDATED ->
                federationReplicator.replicaUserChangeTo(client, event.resourceKey, false, clusterName)
            EventType.USER_DELETED ->
                federationReplicator.replicaUserChangeTo(client, event.resourceKey, true, clusterName)
            EventType.ROLE_CREATED, EventType.ROLE_UPDATED -> {
                // RoleServiceImpl 中 projectId 为 null 时用 ?: "" 兜底，空字符串无法定位项目，跳过
                val projectId = event.projectId.ifEmpty { null }
                    ?: return logger.warn("Skip ROLE_CREATED/UPDATED event[${event.resourceKey}]: projectId is empty")
                federationReplicator.replicaRoleChangeTo(client, event.resourceKey, projectId, false, clusterName)
            }
            EventType.ROLE_DELETED -> {
                val projectId = event.projectId.ifEmpty { null }
                    ?: return logger.warn("Skip ROLE_DELETED event[${event.resourceKey}]: projectId is empty")
                federationReplicator.replicaRoleChangeTo(client, event.resourceKey, projectId, true, clusterName)
            }
            EventType.PERMISSION_CREATED, EventType.PERMISSION_UPDATED -> {
                federationReplicator.replicaPermissionChangeTo(
                    client, event.resourceKey, event.projectId.ifEmpty { null }, false, null, null, clusterName
                )
            }
            EventType.PERMISSION_DELETED -> {
                federationReplicator.replicaPermissionChangeTo(
                    client, event.resourceKey, event.projectId.ifEmpty { null }, true,
                    event.data["permName"]?.toString(), event.data["resourceType"]?.toString(), clusterName
                )
            }
            EventType.ACCOUNT_CREATE, EventType.ACCOUNT_UPDATE ->
                federationReplicator.replicaAccountChangeTo(client, event.resourceKey, false, clusterName)
            EventType.ACCOUNT_DELETE ->
                federationReplicator.replicaAccountChangeTo(client, event.resourceKey, true, clusterName)
            EventType.KEYS_CREATE -> {
                val keyUserId = event.data["userId"]?.toString() ?: event.userId
                federationReplicator.replicaKeyChangeTo(client, event.resourceKey, keyUserId, false, clusterName)
            }
            EventType.KEYS_DELETE -> {
                val keyUserId = event.data["userId"]?.toString() ?: event.userId
                federationReplicator.replicaKeyChangeTo(client, event.resourceKey, keyUserId, true, clusterName)
            }
            EventType.OAUTH_TOKEN_CREATED ->
                federationReplicator.replicaOauthTokenChangeTo(client, event.resourceKey, false, clusterName)
            EventType.OAUTH_TOKEN_DELETED ->
                federationReplicator.replicaOauthTokenChangeTo(client, event.resourceKey, true, clusterName)
            EventType.TEMP_TOKEN_CREATED ->
                federationReplicator.replicaTemporaryTokensTo(client, event.projectId, clusterName)
            EventType.TEMP_TOKEN_DELETED ->
                client.replicaTemporaryTokenRequest(
                    TemporaryTokenReplicaRequest(action = ReplicaAction.DELETE, token = event.resourceKey)
                )
            EventType.PROXY_CREATED, EventType.PROXY_UPDATED ->
                federationReplicator.replicaProxyChangeTo(client, event.resourceKey, event.projectId, false, clusterName)
            EventType.PROXY_DELETED ->
                federationReplicator.replicaProxyChangeTo(client, event.resourceKey, event.projectId, true, clusterName)
            EventType.REPO_AUTH_CONFIG_UPDATED ->
                federationReplicator.replicaRepoAuthConfigChangeTo(client, event.projectId, event.repoName, clusterName)
            else -> logger.warn("Unhandled event type[${event.type}] in FederationPermissionEventConsumer")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationPermissionEventConsumer::class.java)

        /** 全局范围事件：与 projectId 无关，同步到所有联邦集群 */
        val GLOBAL_TYPES: Set<EventType> = setOf(
            EventType.USER_CREATED, EventType.USER_UPDATED, EventType.USER_DELETED,
            EventType.ACCOUNT_CREATE, EventType.ACCOUNT_UPDATE, EventType.ACCOUNT_DELETE,
            EventType.OAUTH_TOKEN_CREATED, EventType.OAUTH_TOKEN_DELETED,
            EventType.KEYS_CREATE, EventType.KEYS_DELETE,
        )

        /** 项目级事件：只同步到该 projectId 对应的联邦集群 */
        val PROJECT_TYPES: Set<EventType> = setOf(
            EventType.ROLE_CREATED, EventType.ROLE_UPDATED, EventType.ROLE_DELETED,
            EventType.PERMISSION_CREATED, EventType.PERMISSION_UPDATED, EventType.PERMISSION_DELETED,
            EventType.TEMP_TOKEN_CREATED, EventType.TEMP_TOKEN_DELETED,
            EventType.PROXY_CREATED, EventType.PROXY_UPDATED, EventType.PROXY_DELETED,
            EventType.REPO_AUTH_CONFIG_UPDATED,
        )
    }
}
