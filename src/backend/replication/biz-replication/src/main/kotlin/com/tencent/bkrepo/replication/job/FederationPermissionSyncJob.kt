/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.job

import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.cluster.StandaloneJob
import com.tencent.bkrepo.common.service.feign.FeignClientFactory
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.dao.FederatedRepositoryDao
import com.tencent.bkrepo.replication.replica.replicator.standalone.FederationReplicator
import com.tencent.bkrepo.replication.service.ClusterNodeService
import com.tencent.bkrepo.replication.service.FederationGroupService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 联邦集群系统级数据定时全量同步任务（兜底补偿）
 *
 * 遍历所有联邦仓库的远端集群，将本端的用户权限及系统级数据全量推送到各远端，
 * 作为 FederationPermissionEventConsumer 增量事件同步的兜底补偿，
 * 修复因事件丢失或服务重启导致的数据不一致。
 *
 * 默认每周一凌晨2点执行（可通过 replication.federation.permissionSyncCron 覆盖）。
 *
 * 全局数据（users/keys/accounts/oauthTokens/externalPermissions）按 clusterId 去重，每个集群只推送一次。
 * 项目级数据（roles/permissions/temporaryTokens/personalPaths/proxies/repoAuthConfig）按 projectId+clusterId 去重。
 */
@Component
class FederationPermissionSyncJob(
    private val federatedRepositoryDao: FederatedRepositoryDao,
    private val clusterNodeService: ClusterNodeService,
    private val federationReplicator: FederationReplicator,
    private val federationGroupService: FederationGroupService,
) {

    @StandaloneJob
    @Scheduled(cron = "\${replication.federation.permissionSyncCron:0 0 2 ? * MON}")
    @SchedulerLock(name = "FederationPermissionSyncJob", lockAtMostFor = "PT6H")
    fun sync() {
        logger.info("Starting federation system data sync job")

        val allGroups = federationGroupService.listAll()
        if (allGroups.isEmpty()) {
            logger.info("No federation groups configured, skip system data sync")
            return
        }

        val allFederations = try {
            federatedRepositoryDao.findAll()
        } catch (e: Exception) {
            logger.error("Failed to query federation repositories: ${e.message}", e)
            return
        }

        if (allFederations.isEmpty()) {
            logger.info("No federation repositories found, skip")
            return
        }

        // 全局数据按 clusterId 去重；项目级数据按 projectId|clusterId 去重
        val syncedGlobal = mutableSetOf<String>()
        val syncedProject = mutableSetOf<String>()

        allFederations.forEach { federation ->
            federation.federatedClusters.forEach { fedCluster ->
                val clusterId = fedCluster.clusterId
                val projectKey = "${federation.projectId}|$clusterId"

                val clusterInfo = clusterNodeService.getByClusterId(clusterId)
                    ?: run {
                        logger.warn("Cluster[$clusterId] not found, skip sync for project[${federation.projectId}]")
                        return@forEach
                    }
                val cluster = ClusterInfo(
                    name = clusterInfo.name,
                    url = clusterInfo.url,
                    username = clusterInfo.username,
                    password = clusterInfo.password,
                    certificate = clusterInfo.certificate,
                    appId = clusterInfo.appId,
                    accessKey = clusterInfo.accessKey,
                    secretKey = clusterInfo.secretKey,
                    udpPort = clusterInfo.udpPort
                )
                val client: ArtifactReplicaClient = FeignClientFactory.create(cluster)

                // 全局数据：同一远端集群只推送一次
                if (syncedGlobal.add(clusterId)) {
                    syncGlobalData(client, clusterInfo.name)
                }

                // 项目级数据：同一 project+cluster 只推送一次
                if (syncedProject.add(projectKey)) {
                    syncProjectData(client, federation.projectId, clusterInfo.name)
                }
            }
        }

        logger.info(
            "Federation system data sync job completed: " +
                "clusters=${syncedGlobal.size}, project-cluster pairs=${syncedProject.size}"
        )
    }

    /**
     * 同步全局数据（不含 projectId，每个远端集群只推一次）
     * 顺序：accounts → oauthTokens → users → keys → externalPermissions
     * accounts 必须先于 oauthTokens（token 引用 accountId）；users 先于 keys（keys 关联 userId）
     */
    private fun syncGlobalData(client: ArtifactReplicaClient, clusterName: String) {
        try {
            federationReplicator.replicaAccountsTo(client, clusterName)
            federationReplicator.replicaOauthTokensTo(client, clusterName)
            federationReplicator.replicaUsersTo(client, clusterName)
            federationReplicator.replicaKeysTo(client, clusterName)
            federationReplicator.replicaExternalPermissionsTo(client, clusterName)
        } catch (e: Exception) {
            logger.warn("Failed to sync global data to cluster[$clusterName]: ${e.message}")
        }
    }

    /**
     * 同步项目级数据（含 projectId，每个 project+cluster 推一次）
     * 顺序：roles → permissions → temporaryTokens → personalPaths → proxies → repoAuthConfig
     * roles 必须先于 permissions（permissions 引用角色 ID）
     */
    private fun syncProjectData(client: ArtifactReplicaClient, projectId: String, clusterName: String) {
        try {
            federationReplicator.replicaRolesTo(client, projectId, clusterName)
            federationReplicator.replicaPermissionsTo(client, projectId, clusterName)
            federationReplicator.replicaTemporaryTokensTo(client, projectId, clusterName)
            federationReplicator.replicaPersonalPathsTo(client, projectId, clusterName)
            federationReplicator.replicaProxiesTo(client, projectId, clusterName)
            federationReplicator.replicaRepoAuthConfigTo(client, projectId, clusterName)
        } catch (e: Exception) {
            logger.warn("Failed to sync project[$projectId] data to cluster[$clusterName]: ${e.message}")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FederationPermissionSyncJob::class.java)
    }
}
