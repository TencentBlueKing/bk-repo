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

package com.tencent.bkrepo.replication.replica.replicator.standalone

import com.google.common.base.Throwables
import com.tencent.bkrepo.auth.api.ServiceAccountClient
import com.tencent.bkrepo.auth.api.ServiceExternalPermissionClient
import com.tencent.bkrepo.auth.api.ServiceKeyClient
import com.tencent.bkrepo.auth.api.ServiceOauthAuthorizationClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceProxyClient
import com.tencent.bkrepo.auth.api.ServiceRepoModeClient
import com.tencent.bkrepo.auth.api.ServiceRoleClient
import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.TraceUtils.trace
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.constant.DEFAULT_VERSION
import com.tencent.bkrepo.replication.constant.FEDERATED
import com.tencent.bkrepo.replication.exception.ReplicationMessageCode
import com.tencent.bkrepo.replication.manager.LocalDataManager
import com.tencent.bkrepo.replication.metrics.FederationMetricsCollector
import com.tencent.bkrepo.replication.pojo.request.AccountReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaAction
import com.tencent.bkrepo.replication.pojo.request.BlockNodeCreateFinishRequest
import com.tencent.bkrepo.replication.pojo.request.ExternalPermissionReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.KeyReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.OauthTokenReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.PackageDeleteRequest
import com.tencent.bkrepo.replication.pojo.request.PackageVersionDeleteRequest
import com.tencent.bkrepo.replication.pojo.request.PackageVersionDeleteSummary
import com.tencent.bkrepo.replication.pojo.request.PermissionReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.PersonalPathReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ProxyReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.RepoAuthConfigReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.RoleReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.TemporaryTokenReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.UserReplicaRequest
import com.tencent.bkrepo.replication.replica.context.ReplicaContext
import com.tencent.bkrepo.replication.replica.executor.FederationFileThreadPoolExecutor
import com.tencent.bkrepo.replication.replica.replicator.base.AbstractFileReplicator
import com.tencent.bkrepo.replication.replica.replicator.base.internal.ClusterArtifactReplicationHandler
import com.tencent.bkrepo.replication.replica.repository.internal.PackageNodeMappings
import com.tencent.bkrepo.replication.service.FederationMetadataTrackingService
import com.tencent.bkrepo.replication.service.FederationRepositoryService
import com.tencent.bkrepo.replication.service.ReplicaRecordService
import com.tencent.bkrepo.repository.pojo.blocknode.service.BlockNodeCreateRequest
import com.tencent.bkrepo.repository.pojo.metadata.DeletedNodeMetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.DeletedNodeReplicationRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.replication.util.extractProjectName
import com.tencent.bkrepo.replication.util.extractTenantId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 联邦仓库数据同步类
 */
@Component
class FederationReplicator(
    localDataManager: LocalDataManager,
    artifactReplicationHandler: ClusterArtifactReplicationHandler,
    replicationProperties: ReplicationProperties,
    private val federationRepositoryService: FederationRepositoryService,
    private val federationMetadataTrackingService: FederationMetadataTrackingService,
    private val replicaRecordService: ReplicaRecordService,
    private val metricsCollector: FederationMetricsCollector?,
    internal val localUserClient: ServiceUserClient,
    internal val localPermissionClient: ServicePermissionClient,
    internal val localRoleClient: ServiceRoleClient,
    internal val localAccountClient: ServiceAccountClient,
    internal val localExternalPermissionClient: ServiceExternalPermissionClient,
    internal val localTemporaryTokenClient: ServiceTemporaryTokenClient,
    internal val localOauthAuthorizationClient: ServiceOauthAuthorizationClient,
    internal val localProxyClient: ServiceProxyClient,
    internal val localKeyClient: ServiceKeyClient,
    internal val localRepoModeClient: ServiceRepoModeClient,
) : AbstractFileReplicator(artifactReplicationHandler, replicationProperties, localDataManager) {

    @Value("\${spring.application.version:$DEFAULT_VERSION}")
    private var version: String = DEFAULT_VERSION

    private val remoteRepoCache = ConcurrentHashMap<String, RepositoryDetail>()

    private val executor = FederationFileThreadPoolExecutor.instance

    /**
     * 全量同步用户到联邦集群（初始化联邦或全量同步时调用）
     */
    fun replicaUsers(context: ReplicaContext) {
        replicaUsersTo(context.artifactReplicaClient!!, context.remoteCluster.name)
    }

    fun replicaUsersTo(client: ArtifactReplicaClient, clusterName: String) {
        var pageNumber = 1
        var totalCount = 0
        while (true) {
            val users = try {
                localUserClient.listUsersForFederationPage(null, pageNumber, PAGE_SIZE).data ?: break
            } catch (e: Exception) {
                logger.warn("Failed to list users (page=$pageNumber) for federation sync: ${e.message}")
                break
            }
            if (users.isEmpty()) break
            users.forEach { user ->
                try {
                    client.replicaUserRequest(
                        UserReplicaRequest(
                            userId = user.userId,
                            name = user.name,
                            pwd = user.hashedPwd,
                            admin = user.admin,
                            asstUsers = user.asstUsers,
                            group = user.group,
                            email = user.email,
                            phone = user.phone,
                            tenantId = user.tenantId
                        )
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to sync user [${user.userId}] to federation cluster: ${e.message}")
                }
            }
            totalCount += users.size
            if (users.size < PAGE_SIZE) break
            pageNumber++
        }
        logger.info("Synced $totalCount users to federated cluster [$clusterName]")
    }

    /**
     * 全量同步权限到联邦集群（初始化联邦或全量同步时调用）
     */
    fun replicaPermissions(context: ReplicaContext) {
        replicaPermissionsTo(context.artifactReplicaClient!!, context.localProjectId, context.remoteCluster.name)
    }

    fun replicaPermissionsTo(client: ArtifactReplicaClient, projectId: String, clusterName: String) {
        val permissions = try {
            val projectPerms = localPermissionClient.listPermission(
                projectId, null, "PROJECT"
            ).data ?: emptyList()
            val repoPerms = localPermissionClient.listPermission(
                projectId, null, "REPO"
            ).data ?: emptyList()
            projectPerms + repoPerms
        } catch (e: Exception) {
            logger.warn("Failed to list permissions for federation sync: ${e.message}")
            return
        }
        permissions.forEach { perm ->
            try {
                client.replicaPermissionRequest(
                    PermissionReplicaRequest(
                        resourceType = perm.resourceType,
                        projectId = perm.projectId,
                        permName = perm.permName,
                        repos = perm.repos,
                        includePattern = perm.includePattern,
                        excludePattern = perm.excludePattern,
                        users = perm.users,
                        roles = perm.roles,
                        departments = perm.departments,
                        actions = perm.actions,
                        createBy = perm.createBy,
                        updatedBy = perm.updatedBy
                    )
                )
            } catch (e: Exception) {
                logger.warn("Failed to sync permission [${perm.permName}] to federation cluster: ${e.message}")
            }
        }
        logger.info("Synced ${permissions.size} permissions to federated cluster [$clusterName]")
    }

    fun replicaRoles(context: ReplicaContext) {
        replicaRolesTo(context.artifactReplicaClient!!, context.localProjectId, context.remoteCluster.name)
    }

    fun replicaRolesTo(client: ArtifactReplicaClient, projectId: String, clusterName: String) {
        var pageNumber = 1
        var totalCount = 0
        while (true) {
            val roles = try {
                localRoleClient.listRoleByProjectPage(projectId, pageNumber, PAGE_SIZE).data ?: break
            } catch (e: Exception) {
                logger.warn("Failed to list roles (page=$pageNumber) for project[$projectId]: ${e.message}")
                break
            }
            if (roles.isEmpty()) break
            roles.forEach { role ->
                try {
                    client.replicaRoleRequest(
                        RoleReplicaRequest(
                            id = role.id,
                            roleId = role.roleId,
                            name = role.name,
                            type = role.type,
                            projectId = role.projectId,
                            repoName = role.repoName,
                            admin = role.admin,
                            users = role.users,
                            description = role.description
                        )
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to sync role [${role.roleId}] to federation cluster: ${e.message}")
                }
            }
            totalCount += roles.size
            if (roles.size < PAGE_SIZE) break
            pageNumber++
        }
        logger.info("Synced $totalCount roles to federated cluster [$clusterName]")
    }

    fun replicaAccounts(context: ReplicaContext) {
        replicaAccountsTo(context.artifactReplicaClient!!, context.remoteCluster.name)
    }

    fun replicaAccountsTo(client: ArtifactReplicaClient, clusterName: String) {
        // Account 为全局资源，不含 projectId；仅全量同步时触发
        val accounts = try {
            localAccountClient.listAccountsForFederation().data ?: return
        } catch (e: Exception) {
            logger.warn("Failed to list accounts for federation sync: ${e.message}")
            return
        }
        accounts.forEach { acc ->
            try {
                client.replicaAccountRequest(
                    AccountReplicaRequest(
                        appId = acc.appId,
                        locked = acc.locked,
                        authorizationGrantTypes = acc.authorizationGrantTypes,
                        homepageUrl = acc.homepageUrl,
                        redirectUri = acc.redirectUri,
                        avatarUrl = acc.avatarUrl,
                        scope = acc.scope,
                        description = acc.description,
                        credentials = acc.credentials
                    )
                )
            } catch (e: Exception) {
                logger.warn("Failed to sync account [${acc.appId}] to federation cluster: ${e.message}")
            }
        }
        logger.info("Synced ${accounts.size} accounts to federated cluster [$clusterName]")
    }

    fun replicaExternalPermissions(context: ReplicaContext) {
        replicaExternalPermissionsTo(context.artifactReplicaClient!!, context.remoteCluster.name)
    }

    fun replicaExternalPermissionsTo(client: ArtifactReplicaClient, clusterName: String) {
        val perms = try {
            localExternalPermissionClient.listExternalPermission().data ?: return
        } catch (e: Exception) {
            logger.warn("Failed to list external permissions for federation sync: ${e.message}")
            return
        }
        perms.forEach { perm ->
            try {
                client.replicaExternalPermissionRequest(
                    ExternalPermissionReplicaRequest(
                        id = perm.id,
                        url = perm.url,
                        headers = perm.headers,
                        projectId = perm.projectId,
                        repoName = perm.repoName,
                        scope = perm.scope,
                        platformWhiteList = perm.platformWhiteList,
                        enabled = perm.enabled
                    )
                )
            } catch (e: Exception) {
                logger.warn("Failed to sync external permission [${perm.id}] to federation cluster: ${e.message}")
            }
        }
        logger.info("Synced ${perms.size} external permissions to federated cluster [$clusterName]")
    }

    fun replicaTemporaryTokens(context: ReplicaContext) {
        replicaTemporaryTokensTo(context.artifactReplicaClient!!, context.localProjectId, context.remoteCluster.name)
    }

    fun replicaTemporaryTokensTo(client: ArtifactReplicaClient, projectId: String, clusterName: String) {
        var pageNumber = 1
        var totalCount = 0
        while (true) {
            val tokens = try {
                localTemporaryTokenClient.listActiveByProjectPage(projectId, pageNumber, PAGE_SIZE).data ?: break
            } catch (e: Exception) {
                logger.warn(
                    "Failed to list temporary tokens (page=$pageNumber) for project[$projectId]: ${e.message}"
                )
                break
            }
            if (tokens.isEmpty()) break
            tokens.forEach { token ->
                try {
                    client.replicaTemporaryTokenRequest(
                        TemporaryTokenReplicaRequest(
                            projectId = token.projectId,
                            repoName = token.repoName,
                            fullPath = token.fullPath,
                            token = token.token,
                            authorizedUserList = token.authorizedUserList,
                            authorizedIpList = token.authorizedIpList,
                            expireDate = token.expireDate,
                            permits = token.permits,
                            type = token.type.name,
                            createdBy = token.createdBy
                        )
                    )
                } catch (e: Exception) {
                    logger.warn(
                        "Failed to sync temporary token [${token.token}] to federation cluster: ${e.message}"
                    )
                }
            }
            totalCount += tokens.size
            if (tokens.size < PAGE_SIZE) break
            pageNumber++
        }
        logger.info("Synced $totalCount temporary tokens to federated cluster [$clusterName]")
    }

    fun replicaOauthTokens(context: ReplicaContext) {
        replicaOauthTokensTo(context.artifactReplicaClient!!, context.remoteCluster.name)
    }

    fun replicaOauthTokensTo(client: ArtifactReplicaClient, clusterName: String) {
        // OAuth token 不含 projectId，需全局同步；只在全量同步时触发，增量事件不处理
        val tokens = try {
            localOauthAuthorizationClient.listActiveTokens().data ?: return
        } catch (e: Exception) {
            logger.warn("Failed to list oauth tokens for federation sync: ${e.message}")
            return
        }
        tokens.forEach { token ->
            try {
                client.replicaOauthTokenRequest(
                    OauthTokenReplicaRequest(
                        accessToken = token.accessToken,
                        refreshToken = token.refreshToken,
                        expireSeconds = token.expireSeconds,
                        type = token.type,
                        accountId = token.accountId,
                        userId = token.userId,
                        scope = token.scope,
                        issuedAt = token.issuedAt
                    )
                )
            } catch (e: Exception) {
                logger.warn(
                    "Failed to sync oauth token for user [${token.userId}] to federation cluster: ${e.message}"
                )
            }
        }
        logger.info("Synced ${tokens.size} oauth tokens to federated cluster [$clusterName]")
    }

    fun replicaPersonalPaths(context: ReplicaContext) {
        replicaPersonalPathsTo(context.artifactReplicaClient!!, context.localProjectId, context.remoteCluster.name)
    }

    fun replicaPersonalPathsTo(client: ArtifactReplicaClient, projectId: String, clusterName: String) {
        val paths = try {
            localPermissionClient.listPersonalPath(projectId).data ?: return
        } catch (e: Exception) {
            logger.warn("Failed to list personal paths for federation sync: ${e.message}")
            return
        }
        paths.forEach { path ->
            try {
                client.replicaPersonalPathRequest(
                    PersonalPathReplicaRequest(
                        userId = path.userId,
                        projectId = path.projectId,
                        repoName = path.repoName,
                        fullPath = path.fullPath
                    )
                )
            } catch (e: Exception) {
                logger.warn(
                    "Failed to sync personal path [${path.userId}/${path.projectId}/${path.repoName}]" +
                        " to federation cluster: ${e.message}"
                )
            }
        }
        logger.info("Synced ${paths.size} personal paths to federated cluster [$clusterName]")
    }

    fun replicaProxies(context: ReplicaContext) {
        replicaProxiesTo(context.artifactReplicaClient!!, context.localProjectId, context.remoteCluster.name)
    }

    fun replicaProxiesTo(client: ArtifactReplicaClient, projectId: String, clusterName: String) {
        val proxies = try {
            localProxyClient.listProxyByProject(projectId).data ?: return
        } catch (e: Exception) {
            logger.warn("Failed to list proxies for federation sync: ${e.message}")
            return
        }
        proxies.forEach { proxy ->
            try {
                client.replicaProxyRequest(
                    ProxyReplicaRequest(
                        name = proxy.name,
                        displayName = proxy.displayName,
                        projectId = proxy.projectId,
                        clusterName = proxy.clusterName,
                        domain = proxy.domain,
                        syncRateLimit = proxy.syncRateLimit,
                        syncTimeRange = proxy.syncTimeRange,
                        cacheExpireDays = proxy.cacheExpireDays
                    )
                )
            } catch (e: Exception) {
                logger.warn("Failed to sync proxy [${proxy.name}] to federation cluster: ${e.message}")
            }
        }
        logger.info("Synced ${proxies.size} proxies to federated cluster [$clusterName]")
    }

    fun replicaKeys(context: ReplicaContext) {
        replicaKeysTo(context.artifactReplicaClient!!, context.remoteCluster.name)
    }

    fun replicaKeysTo(client: ArtifactReplicaClient, clusterName: String) {
        var pageNumber = 1
        var totalCount = 0
        while (true) {
            val users = try {
                localUserClient.listUsersForFederationPage(null, pageNumber, PAGE_SIZE).data ?: break
            } catch (e: Exception) {
                logger.warn("Failed to list users (page=$pageNumber) for key federation sync: ${e.message}")
                break
            }
            if (users.isEmpty()) break
            users.forEach { user ->
                val keys = try {
                    localKeyClient.listKeyByUserId(user.userId).data ?: return@forEach
                } catch (e: Exception) {
                    logger.warn("Failed to list keys for user [${user.userId}]: ${e.message}")
                    return@forEach
                }
                keys.forEach { keyInfo ->
                    try {
                        client.replicaKeyRequest(
                            KeyReplicaRequest(
                                id = keyInfo.id,
                                name = keyInfo.name,
                                key = keyInfo.key,
                                fingerprint = keyInfo.fingerprint,
                                userId = keyInfo.userId,
                                createAt = keyInfo.createAt.toString()
                            )
                        )
                        totalCount++
                    } catch (e: Exception) {
                        logger.warn(
                            "Failed to sync key [${keyInfo.fingerprint}] for user [${keyInfo.userId}]: ${e.message}"
                        )
                    }
                }
            }
            if (users.size < PAGE_SIZE) break
            pageNumber++
        }
        logger.info("Synced $totalCount keys to federated cluster [$clusterName]")
    }

    fun replicaRepoAuthConfig(context: ReplicaContext) {
        replicaRepoAuthConfigTo(context.artifactReplicaClient!!, context.localProjectId, context.remoteCluster.name)
    }

    fun replicaRepoAuthConfigTo(client: ArtifactReplicaClient, projectId: String, clusterName: String) {
        val configs = try {
            localRepoModeClient.listByProject(projectId).data ?: return
        } catch (e: Exception) {
            logger.warn("Failed to list repo auth configs for federation sync: ${e.message}")
            return
        }
        configs.forEach { config ->
            try {
                client.replicaRepoAuthConfigRequest(
                    RepoAuthConfigReplicaRequest(
                        id = config.id,
                        projectId = config.projectId,
                        repoName = config.repoName,
                        accessControlMode = config.accessControlMode?.name ?: "DEFAULT",
                        officeDenyGroupSet = config.officeDenyGroupSet,
                        bkiamv3Check = config.bkiamv3Check
                    )
                )
            } catch (e: Exception) {
                logger.warn("Failed to sync repo auth config [${config.projectId}/${config.repoName}]: ${e.message}")
            }
        }
        logger.info("Synced ${configs.size} repo auth configs to federated cluster [$clusterName]")
    }

    fun replicaUserChangeTo(client: ArtifactReplicaClient, userId: String, deleted: Boolean, clusterName: String) {
        try {
            if (deleted) {
                client.replicaUserRequest(UserReplicaRequest(action = ReplicaAction.DELETE, userId = userId))
            } else {
                val userInfo = localUserClient.userInfoById(userId).data ?: run {
                    logger.warn("User[$userId] not found for incremental federation sync")
                    return
                }
                val hashedPwd = try {
                    localUserClient.userPwdById(userId).data
                } catch (e: Exception) {
                    logger.warn("Failed to get pwd for user[$userId]: ${e.message}")
                    null
                }
                client.replicaUserRequest(
                    UserReplicaRequest(
                        action = ReplicaAction.UPSERT,
                        userId = userId,
                        name = userInfo.name,
                        pwd = hashedPwd,
                        admin = userInfo.admin,
                        asstUsers = userInfo.asstUsers,
                        group = userInfo.group,
                        email = userInfo.email,
                        phone = userInfo.phone,
                        tenantId = userInfo.tenantId
                    )
                )
            }
            logger.info("Incremental user[$userId] (deleted=$deleted) synced to cluster[$clusterName]")
        } catch (e: Exception) {
            logger.warn("Failed to sync user[$userId] to cluster[$clusterName]: ${e.message}")
        }
    }

    fun replicaRoleChangeTo(
        client: ArtifactReplicaClient,
        roleId: String,
        projectId: String,
        deleted: Boolean,
        clusterName: String
    ) {
        try {
            if (deleted) {
                client.replicaRoleRequest(RoleReplicaRequest(action = ReplicaAction.DELETE, id = roleId))
            } else {
                val role = localRoleClient.listRoleByProject(projectId).data
                    ?.find { it.id == roleId } ?: run {
                    logger.warn("Role[$roleId] not found in project[$projectId] for incremental federation sync")
                    return
                }
                client.replicaRoleRequest(
                    RoleReplicaRequest(
                        action = ReplicaAction.UPSERT,
                        id = role.id,
                        roleId = role.roleId,
                        name = role.name,
                        type = role.type,
                        projectId = role.projectId,
                        repoName = role.repoName,
                        admin = role.admin,
                        users = role.users,
                        description = role.description
                    )
                )
            }
            logger.info(
                "Incremental role[$roleId] in project[$projectId] (deleted=$deleted) " +
                    "synced to cluster[$clusterName]"
            )
        } catch (e: Exception) {
            logger.warn(
                "Failed to sync role[$roleId] in project[$projectId] " +
                    "to cluster[$clusterName]: ${e.message}"
            )
        }
    }

    fun replicaPermissionChangeTo(
        client: ArtifactReplicaClient,
        permId: String,
        projectId: String?,
        deleted: Boolean,
        permName: String?,
        resourceType: String?,
        clusterName: String
    ) {
        try {
            if (deleted) {
                if (permName.isNullOrBlank() || resourceType.isNullOrBlank()) {
                    logger.warn("Missing permName or resourceType for permission[$permId] DELETE, skipping")
                    return
                }
                client.replicaPermissionRequest(
                    PermissionReplicaRequest(
                        action = ReplicaAction.DELETE,
                        resourceType = resourceType,
                        projectId = projectId,
                        permName = permName
                    )
                )
            } else {
                // projectId 为空（系统级权限）时直接按 id 查，避免用空字符串查询
                val perm = if (projectId.isNullOrEmpty()) {
                    localPermissionClient.getPermissionById(permId).data ?: run {
                        logger.warn("Permission[$permId] not found for incremental federation sync")
                        return
                    }
                } else {
                    listOf("PROJECT", "REPO").flatMap { type ->
                        runCatching { localPermissionClient.listPermission(projectId, null, type).data ?: emptyList() }
                            .getOrDefault(emptyList())
                    }.find { it.id == permId } ?: run {
                        logger.warn("Permission[$permId] not found in project[$projectId] for incremental federation sync")
                        return
                    }
                }
                client.replicaPermissionRequest(
                    PermissionReplicaRequest(
                        action = ReplicaAction.UPSERT,
                        resourceType = perm.resourceType,
                        projectId = perm.projectId,
                        permName = perm.permName,
                        repos = perm.repos,
                        includePattern = perm.includePattern,
                        excludePattern = perm.excludePattern,
                        users = perm.users,
                        roles = perm.roles,
                        departments = perm.departments,
                        actions = perm.actions,
                        createBy = perm.createBy,
                        updatedBy = perm.updatedBy
                    )
                )
            }
            logger.info("Incremental permission[$permId] (deleted=$deleted) synced to cluster[$clusterName]")
        } catch (e: Exception) {
            logger.warn("Failed to sync permission[$permId] to cluster[$clusterName]: ${e.message}")
        }
    }

    fun replicaAccountChangeTo(
        client: ArtifactReplicaClient,
        appId: String,
        deleted: Boolean,
        clusterName: String
    ) {
        try {
            if (deleted) {
                client.replicaAccountRequest(AccountReplicaRequest(action = ReplicaAction.DELETE, appId = appId))
            } else {
                val acc = localAccountClient.listAccountsForFederation().data
                    ?.find { it.appId == appId } ?: run {
                    logger.warn("Account[$appId] not found for incremental federation sync")
                    return
                }
                client.replicaAccountRequest(
                    AccountReplicaRequest(
                        action = ReplicaAction.UPSERT,
                        appId = acc.appId,
                        locked = acc.locked,
                        authorizationGrantTypes = acc.authorizationGrantTypes,
                        homepageUrl = acc.homepageUrl,
                        redirectUri = acc.redirectUri,
                        avatarUrl = acc.avatarUrl,
                        scope = acc.scope,
                        description = acc.description,
                        credentials = acc.credentials
                    )
                )
            }
            logger.info("Incremental account[$appId] (deleted=$deleted) synced to cluster[$clusterName]")
        } catch (e: Exception) {
            logger.warn("Failed to sync account[$appId] to cluster[$clusterName]: ${e.message}")
        }
    }

    fun replicaKeyChangeTo(
        client: ArtifactReplicaClient,
        fingerprint: String,
        keyUserId: String,
        deleted: Boolean,
        clusterName: String
    ) {
        try {
            if (deleted) {
                client.replicaKeyRequest(
                    KeyReplicaRequest(action = ReplicaAction.DELETE, fingerprint = fingerprint, userId = keyUserId)
                )
            } else {
                val keyInfo = localKeyClient.listKeyByUserId(keyUserId).data
                    ?.find { it.fingerprint == fingerprint } ?: run {
                    logger.warn(
                        "Key[fingerprint=$fingerprint, userId=$keyUserId] not found" +
                            " for incremental federation sync"
                    )
                    return
                }
                client.replicaKeyRequest(
                    KeyReplicaRequest(
                        action = ReplicaAction.UPSERT,
                        id = keyInfo.id,
                        name = keyInfo.name,
                        key = keyInfo.key,
                        fingerprint = keyInfo.fingerprint,
                        userId = keyInfo.userId,
                        createAt = keyInfo.createAt.toString()
                    )
                )
            }
            logger.info(
                "Incremental key[fingerprint=$fingerprint] user[$keyUserId] " +
                    "(deleted=$deleted) synced to cluster[$clusterName]"
            )
        } catch (e: Exception) {
            logger.warn("Failed to sync key[fingerprint=$fingerprint] to cluster[$clusterName]: ${e.message}")
        }
    }

    fun replicaOauthTokenChangeTo(
        client: ArtifactReplicaClient,
        accessToken: String,
        deleted: Boolean,
        clusterName: String
    ) {
        try {
            if (deleted) {
                client.replicaOauthTokenRequest(
                    OauthTokenReplicaRequest(action = ReplicaAction.DELETE, accessToken = accessToken)
                )
            } else {
                val tokenInfo = localOauthAuthorizationClient.getTokenInfo(accessToken).data ?: run {
                    logger.warn("OauthToken[$accessToken] not found for incremental federation sync")
                    return
                }
                client.replicaOauthTokenRequest(
                    OauthTokenReplicaRequest(
                        action = ReplicaAction.UPSERT,
                        accessToken = tokenInfo.accessToken,
                        refreshToken = tokenInfo.refreshToken,
                        expireSeconds = tokenInfo.expireSeconds,
                        type = tokenInfo.type,
                        accountId = tokenInfo.accountId,
                        userId = tokenInfo.userId,
                        scope = tokenInfo.scope,
                        issuedAt = tokenInfo.issuedAt
                    )
                )
            }
            logger.info("Incremental oauthToken (deleted=$deleted) synced to cluster[$clusterName]")
        } catch (e: Exception) {
            logger.warn("Failed to sync oauthToken to cluster[$clusterName]: ${e.message}")
        }
    }

    fun replicaProxyChangeTo(
        client: ArtifactReplicaClient,
        proxyName: String,
        projectId: String,
        deleted: Boolean,
        clusterName: String
    ) {
        try {
            if (deleted) {
                client.replicaProxyRequest(
                    ProxyReplicaRequest(action = ReplicaAction.DELETE, name = proxyName, projectId = projectId)
                )
            } else {
                val proxy = localProxyClient.listProxyByProject(projectId).data
                    ?.find { it.name == proxyName } ?: run {
                    logger.warn("Proxy[$proxyName] in project[$projectId] not found for incremental federation sync")
                    return
                }
                client.replicaProxyRequest(
                    ProxyReplicaRequest(
                        action = ReplicaAction.UPSERT,
                        name = proxy.name,
                        displayName = proxy.displayName,
                        projectId = proxy.projectId,
                        clusterName = proxy.clusterName,
                        domain = proxy.domain,
                        syncRateLimit = proxy.syncRateLimit,
                        syncTimeRange = proxy.syncTimeRange,
                        cacheExpireDays = proxy.cacheExpireDays
                    )
                )
            }
            logger.info(
                "Incremental proxy[$proxyName] in project[$projectId] " +
                    "(deleted=$deleted) synced to cluster[$clusterName]"
            )
        } catch (e: Exception) {
            logger.warn(
                "Failed to sync proxy[$proxyName] in project[$projectId]" +
                    " to cluster[$clusterName]: ${e.message}"
            )
        }
    }

    fun replicaRepoAuthConfigChangeTo(
        client: ArtifactReplicaClient,
        projectId: String,
        repoName: String,
        clusterName: String
    ) {
        try {
            val config = localRepoModeClient.listByProject(projectId).data
                ?.find { it.repoName == repoName } ?: run {
                logger.warn("RepoAuthConfig[$projectId/$repoName] not found for incremental federation sync")
                return
            }
            client.replicaRepoAuthConfigRequest(
                RepoAuthConfigReplicaRequest(
                    action = ReplicaAction.UPSERT,
                    id = config.id,
                    projectId = config.projectId,
                    repoName = config.repoName,
                    accessControlMode = config.accessControlMode?.name ?: "DEFAULT",
                    officeDenyGroupSet = config.officeDenyGroupSet,
                    bkiamv3Check = config.bkiamv3Check
                )
            )
            logger.info("Incremental repoAuthConfig[$projectId/$repoName] synced to cluster[$clusterName]")
        } catch (e: Exception) {
            logger.warn("Failed to sync repoAuthConfig[$projectId/$repoName] to cluster[$clusterName]: ${e.message}")
        }
    }

    override fun checkVersion(context: ReplicaContext) {
        with(context) {
            val remoteVersion = artifactReplicaClient!!.version().data.orEmpty()
            if (version != remoteVersion) {
                logger.warn("Local cluster's version[$version] is different from federated cluster[$remoteVersion].")
            }
        }
    }

    override fun replicaProject(context: ReplicaContext) {
        with(context) {
            // 外部集群仓库没有project/repoName
            if (remoteProjectId.isNullOrBlank()) return
            val localProject = localDataManager.findProjectById(localProjectId)
            val tenantId = remoteProjectId.extractTenantId()
            val projectName = remoteProjectId.extractProjectName()
            val request = ProjectCreateRequest(
                name = projectName,
                displayName = projectName,
                description = localProject.description,
                operator = localProject.createdBy,
                source = getCurrentClusterName(localProjectId, localRepoName, task.name)
            )
            artifactReplicaClient!!.replicaProjectCreateRequest(request, tenantId)
        }
    }

    override fun replicaRepo(context: ReplicaContext) {
        with(context) {
            // 外部集群仓库没有project/repoName
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return
            val localRepo = localDataManager.findRepoByName(localProjectId, localRepoName, localRepoType.name)
            val key = buildRemoteRepoCacheKey(cluster, remoteProjectId, remoteRepoName)
            context.remoteRepo = remoteRepoCache.getOrPut(key) {
                val request = RepoCreateRequest(
                    projectId = remoteProjectId,
                    name = remoteRepoName,
                    type = remoteRepoType,
                    category = localRepo.category,
                    public = localRepo.public,
                    description = localRepo.description,
                    configuration = localRepo.configuration,
                    operator = localRepo.createdBy,
                    source = getCurrentClusterName(localProjectId, localRepoName, task.name)
                )
                val createdRemoteRepo = artifactReplicaClient!!.replicaRepoCreateRequest(request).data!!
                // 目标节点已有名称相同但类型不同的仓库时抛出异常
                if (createdRemoteRepo.type != remoteRepoType) {
                    throw ErrorCodeException(
                        ArtifactMessageCode.REPOSITORY_EXISTED,
                        "$remoteProjectId/$remoteRepoName",
                        status = HttpStatus.CONFLICT
                    )
                }
                createdRemoteRepo
            }
        }
    }

    override fun replicaPackage(context: ReplicaContext, packageSummary: PackageSummary) {
        // do nothing
    }

    override fun replicaPackageVersion(
        context: ReplicaContext,
        packageSummary: PackageSummary,
        packageVersion: PackageVersion,
    ): Boolean {
        with(context) {
            // 外部集群仓库没有project/repoName
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return true
            PackageNodeMappings.map(
                packageSummary = packageSummary,
                packageVersion = packageVersion,
                type = localRepoType
            ).forEach {
                val node = try {
                    localDataManager.findNodeDetailInVersion(
                        projectId = localProjectId,
                        repoName = localRepoName,
                        fullPath = it
                    )
                } catch (e: NodeNotFoundException) {
                    logger.warn("Node $it not found in repo $localProjectId|$localRepoName")
                    throw e
                }
                replicaFile(context, node.nodeInfo)
            }
            val packageMetadata = packageVersion.packageMetadata as MutableList<MetadataModel>
            packageMetadata.add(MetadataModel(FEDERATED, true, true))
            // 包数据
            val request = PackageVersionCreateRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                packageName = packageSummary.name,
                packageKey = packageSummary.key,
                packageType = packageSummary.type,
                packageDescription = packageSummary.description,
                versionName = packageVersion.name,
                size = packageVersion.size,
                manifestPath = packageVersion.manifestPath,
                artifactPath = packageVersion.contentPath,
                stageTag = packageVersion.stageTag,
                packageMetadata = packageMetadata,
                extension = packageVersion.extension,
                overwrite = true,
                createdBy = packageVersion.createdBy,
                source = getCurrentClusterName(localProjectId, localRepoName, task.name)
            )
            artifactReplicaClient!!.replicaPackageVersionCreatedRequest(request)
        }
        return true
    }

    override fun replicaDeletedPackage(
        context: ReplicaContext,
        packageVersionDeleteSummary: PackageVersionDeleteSummary,
    ): Boolean {
        with(context) {
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return false
            if (packageVersionDeleteSummary.versionName.isNullOrEmpty()) {
                // 构建包删除请求
                val packageDeleteRequest = PackageDeleteRequest(
                    projectId = remoteProjectId,
                    repoName = remoteRepoName,
                    packageKey = packageVersionDeleteSummary.packageKey,
                    source = getCurrentClusterName(localProjectId, localRepoName, task.name),
                    deletedDate = packageVersionDeleteSummary.deletedDate
                )
                artifactReplicaClient!!.replicaPackageDeleteRequest(packageDeleteRequest)
            } else {
                // 构建包版本删除请求
                val versionDeleteRequest = PackageVersionDeleteRequest(
                    projectId = remoteProjectId,
                    repoName = remoteRepoName,
                    packageKey = packageVersionDeleteSummary.packageKey,
                    versionName = packageVersionDeleteSummary.versionName!!,
                    source = getCurrentClusterName(localProjectId, localRepoName, task.name),
                    deletedDate = packageVersionDeleteSummary.deletedDate
                )
                artifactReplicaClient!!.replicaPackageVersionDeleteRequest(versionDeleteRequest)
            }
        }
        return true
    }

    override fun replicaFile(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            //  同步block node
            if (unNormalNode(node)) {
                return replicaBlockNode(context, node)
            }

            // 同步Node节点
            if (!syncNodeToFederatedCluster(this, node)) return false

            // 2. 记录文件传输开始标识
            recordFileTransferStart(this, node)

            //  同步文件
            return replicaNormalFile(context, node)
        }
    }

    /**
     * 复制块节点文件
     */
    private fun replicaBlockNode(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            val blockNodeList = validateAndGetBlockNodeList(context, node) ?: return false
            val uploadId = "${StringPool.uniqueId()}/${node.id}"

            // 传输所有blocknode元数据
            blockNodeList.forEach { blockNode ->
                buildBlockNodeCreateRequest(this, blockNode, uploadId)?.let { blockNodeCreateRequest ->
                    context.artifactReplicaClient!!.replicaBlockNodeCreateRequest(blockNodeCreateRequest)
                }
            }
            artifactReplicaClient!!.replicaBlockNodeCreateFinishRequest(
                BlockNodeCreateFinishRequest(
                    projectId = remoteProjectId!!,
                    repoName = remoteRepoName!!,
                    uploadId = uploadId,
                    fullPath = node.fullPath
                )
            )

            // 同步节点
            if (!syncNodeToFederatedCluster(this, node)) return false

            // 2. 记录文件传输开始标识
            recordFileTransferStart(this, node)

            // 并发传输文件并保存元数据
            val result = transferBlockNodeFilesAndSaveMetadata(context, node, blockNodeList)
            if (result) {
                // 记录文件传输完成标识
                recordFileTransferComplete(context, node)
            }
            return result
        }
    }

    /**
     * 复制普通文件
     */
    private fun replicaNormalFile(context: ReplicaContext, node: NodeInfo): Boolean {
        return if (executor.activeCount < replicationProperties.federatedFileConcurrencyNum) {
            // 异步执行
            executeFileTransferAsync(context, node)
        } else {
            // 同步执行
            executeFileTransferSync(context, node)
        }
    }


    /**
     * 异步执行文件传输
     */
    private fun executeFileTransferAsync(context: ReplicaContext, node: NodeInfo): Boolean {
        val latch = CountDownLatch(1)
        val result = AtomicBoolean(true)
        val startTime = System.currentTimeMillis()

        executor.execute(
            Runnable {
                try {
                    pushFileToFederatedCluster(context, node)
                    // 记录文件传输完成标识
                    recordFileTransferComplete(context, node)

                    // 记录文件传输指标
                    val duration = System.currentTimeMillis() - startTime
                    metricsCollector?.recordFileTransfer(
                        projectId = context.localProjectId,
                        repoName = context.localRepoName,
                        success = true,
                        bytes = node.size,
                        durationMillis = duration,
                        taskKey = context.task.key
                    )
                } catch (throwable: Throwable) {
                    handleFileTransferError(context, node, throwable)
                    result.set(false)

                    // 记录文件传输失败指标
                    val duration = System.currentTimeMillis() - startTime
                    metricsCollector?.recordFileTransfer(
                        projectId = context.localProjectId,
                        repoName = context.localRepoName,
                        success = false,
                        bytes = node.size,
                        durationMillis = duration,
                        taskKey = context.task.key
                    )
                } finally {
                    latch.countDown()
                }
            }.trace()
        )

        latch.await()
        return result.get()
    }

    /**
     * 同步执行文件传输
     */
    private fun executeFileTransferSync(context: ReplicaContext, node: NodeInfo): Boolean {
        val startTime = System.currentTimeMillis()
        return try {
            pushFileToFederatedCluster(context, node)
            // 记录文件传输完成标识
            recordFileTransferComplete(context, node)

            // 记录文件传输指标
            val duration = System.currentTimeMillis() - startTime
            metricsCollector?.recordFileTransfer(
                projectId = context.localProjectId,
                repoName = context.localRepoName,
                success = true,
                bytes = node.size,
                durationMillis = duration,
                taskKey = context.task.key
            )
            true
        } catch (throwable: Throwable) {
            handleFileTransferError(context, node, throwable)

            // 记录文件传输失败指标
            val duration = System.currentTimeMillis() - startTime
            metricsCollector?.recordFileTransfer(
                projectId = context.localProjectId,
                repoName = context.localRepoName,
                success = false,
                bytes = node.size,
                durationMillis = duration,
                taskKey = context.task.key
            )
            false
        }
    }

    /**
     * 处理块文件传输错误
     */
    private fun handleBlockFileTransferError(context: ReplicaContext, node: NodeInfo, throwable: Throwable) {
        logger.warn(
            "replica block file of ${node.fullPath} with sha256 ${node.sha256} in repo " +
                "${node.projectId}|${node.repoName} failed, error is ${Throwables.getStackTraceAsString(throwable)}"
        )
        completeFileReplicaRecord(context, false)
    }

    /**
     * 处理文件传输错误
     */
    private fun handleFileTransferError(context: ReplicaContext, node: NodeInfo, throwable: Throwable) {
        logger.warn(
            "replica file ${node.fullPath} with sha256 ${node.sha256} in repo " +
                "${node.projectId}|${node.repoName} failed, error is ${Throwables.getStackTraceAsString(throwable)}"
        )
        completeFileReplicaRecord(context, false)
    }

    /**
     * 保存节点元数据
     */
    private fun saveNodeMetadata(context: ReplicaContext, node: NodeInfo) {
        if (node.deleted != null) {
            context.artifactReplicaClient!!.replicaMetadataSaveRequestForDeletedNode(
                buildDeletedNodeMetadataSaveRequest(context, node, context.task.name)
            )
        } else {
            context.artifactReplicaClient!!.replicaMetadataSaveRequest(
                buildMetadataSaveRequest(context, node, context.task.name)
            )
        }
    }

    private fun completeFileReplicaRecord(context: ReplicaContext, success: Boolean = true) {
        with(context) {
            if (task.record == false || recordDetailId.isNullOrEmpty()) return
            replicaRecordService.updateRecordDetailProgress(recordDetailId!!, success)
        }
    }

    /**
     * 记录文件传输开始标识
     */
    private fun recordFileTransferStart(context: ReplicaContext, node: NodeInfo) {
        with(context) {
            try {
                if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return

                federationMetadataTrackingService.createTrackingRecord(
                    taskKey = task.key,
                    remoteClusterId = remoteCluster.id!!,
                    projectId = localProjectId,
                    localRepoName = localRepoName,
                    remoteProjectId = remoteProjectId,
                    remoteRepoName = remoteRepoName,
                    nodePath = node.fullPath,
                    nodeId = node.id!!
                )
            } catch (e: Exception) {
                logger.warn("Failed to record file transfer start for node ${node.fullPath}: ${e.message}")
            }
        }
    }

    /**
     * 记录文件传输完成标识
     */
    private fun recordFileTransferComplete(context: ReplicaContext, node: NodeInfo) {
        with(context) {
            try {
                if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return

                // 删除跟踪记录，表示传输完成
                federationMetadataTrackingService.deleteByTaskKeyAndNodeId(task.key, node.id!!)
            } catch (e: Exception) {
                logger.warn("Failed to delete file transfer record for node ${node.fullPath}: ${e.message}")
            }
        }
    }

    private fun pushBlockFileToFederatedCluster(context: ReplicaContext, blockNode: TBlockNode) {
        executeFilePush(
            context = context,
            node = blockNode,
            logPrefix = "[Federation-Block] ",
            afterCompletion = { ctx, _ ->
                completeFileReplicaRecord(ctx)
            }
        )
    }

    /**
     * 推送文件到联邦集群
     */
    private fun pushFileToFederatedCluster(context: ReplicaContext, node: NodeInfo) {
        executeFilePush(
            context = context,
            node = node,
            logPrefix = "[Federation] ",
            afterCompletion = { ctx, nodeInfo ->
                // 通过文件传输完成标识
                if (nodeInfo.deleted != null) {
                    ctx.artifactReplicaClient!!.replicaMetadataSaveRequestForDeletedNode(
                        buildDeletedNodeMetadataSaveRequest(ctx, nodeInfo, ctx.task.name)
                    )
                } else {
                    ctx.artifactReplicaClient!!.replicaMetadataSaveRequest(
                        buildMetadataSaveRequest(ctx, nodeInfo, ctx.task.name)
                    )
                }
            }
        )
    }

    /**
     * 推送文件到联邦集群（供定时任务调用）
     */
    fun pushFileToFederatedClusterPublic(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            // 同步block node
            if (unNormalNode(node)) {
                return pushBlockNodeFiles(context, node)
            }

            // 同步普通文件
            pushFileToFederatedCluster(context, node)
            return true
        }
    }

    /**
     * 推送块节点文件（仅传输文件，不处理元数据同步）
     */
    private fun pushBlockNodeFiles(context: ReplicaContext, node: NodeInfo): Boolean {
        val blockNodeList = validateAndGetBlockNodeList(context, node) ?: return false
        return transferBlockNodeFilesAndSaveMetadata(context, node, blockNodeList)
    }


    /**
     * 传输块节点文件并保存元数据
     */
    private fun transferBlockNodeFilesAndSaveMetadata(
        context: ReplicaContext,
        node: NodeInfo,
        blockNodeList: List<TBlockNode>
    ): Boolean {
        // 并发传输文件
        val success = executeBlockFileTransfer(
            blockNodeExecutor = executor,
            context = context,
            node = node,
            blockNodeList = blockNodeList,
            pushBlockFile = { ctx, blockNode -> pushBlockFileToFederatedCluster(ctx, blockNode) },
            handleError = { ctx, nodeInfo, throwable -> handleBlockFileTransferError(ctx, nodeInfo, throwable) }
        )
        if (!success) return false

        // 保存元数据标识传输完成
        saveNodeMetadata(context, node)
        return true
    }

    override fun replicaDir(context: ReplicaContext, node: NodeInfo) {
        with(context) {
            buildNodeCreateRequest(this, node)?.let {
                artifactReplicaClient!!.replicaNodeCreateRequest(it)
            }
        }
    }

    override fun replicaDeletedNode(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            buildNodeDeleteRequest(this, node)?.let {
                artifactReplicaClient!!.replicaNodeDeleteRequest(it)
                return true
            }
            return false
        }
    }

    override fun replicaNodeMove(context: ReplicaContext, moveOrCopyRequest: NodeMoveCopyRequest): Boolean {
        with(context) {
            buildNodeMoveCopyRequest(this, moveOrCopyRequest).let {
                artifactReplicaClient!!.replicaNodeMoveRequest(it)
            }
            return true
        }
    }

    override fun replicaNodeCopy(context: ReplicaContext, moveOrCopyRequest: NodeMoveCopyRequest): Boolean {
        with(context) {
            buildNodeMoveCopyRequest(this, moveOrCopyRequest).let {
                artifactReplicaClient!!.replicaNodeCopyRequest(it)
            }
            return true
        }
    }

    override fun replicaNodeRename(context: ReplicaContext, nodeRenameRequest: NodeRenameRequest): Boolean {
        with(context) {
            buildNodeRenameRequest(this, nodeRenameRequest).let {
                artifactReplicaClient!!.replicaNodeRenameRequest(it)
            }
            return true
        }
    }

    override fun replicaMetadataSave(context: ReplicaContext, metadataSaveRequest: MetadataSaveRequest): Boolean {
        with(context) {
            buildMetadataSaveRequest(this, metadataSaveRequest).let {
                artifactReplicaClient!!.replicaMetadataSaveRequest(it)
            }
            return true
        }
    }

    override fun replicaMetadataDelete(context: ReplicaContext, metadataDeleteRequest: MetadataDeleteRequest): Boolean {
        with(context) {
            buildMetadataDeleteRequest(this, metadataDeleteRequest).let {
                artifactReplicaClient!!.replicaMetadataDeleteRequest(it)
            }
            return true
        }
    }

    private fun getCurrentClusterName(projectId: String, repoName: String, taskName: String): String {
        val key = parseKeyFromTaskName(taskName)
        return federationRepositoryService.getCurrentClusterName(projectId, repoName, key)
            ?: throw ErrorCodeException(ReplicationMessageCode.CLUSTER_NODE_NOT_FOUND, "self")
    }

    private fun parseKeyFromTaskName(taskName: String): String {
        val parts = taskName.split("/")
        require(parts.size >= 3) { "Invalid task name format" }
        return parts[1]
    }

    private fun buildNodeMoveCopyRequest(
        context: ReplicaContext,
        moveOrCopyRequest: NodeMoveCopyRequest
    ): NodeMoveCopyRequest {
        with(moveOrCopyRequest) {
            return moveOrCopyRequest.copy(
                source = getCurrentClusterName(context.localProjectId, context.localRepoName, context.task.name),
            )
        }
    }

    private fun buildNodeRenameRequest(
        context: ReplicaContext,
        nodeRenameRequest: NodeRenameRequest
    ): NodeRenameRequest {
        with(nodeRenameRequest) {
            return nodeRenameRequest.copy(
                source = getCurrentClusterName(context.localProjectId, context.localRepoName, context.task.name),
            )
        }
    }

    private fun buildMetadataSaveRequest(
        context: ReplicaContext,
        metadataSaveRequest: MetadataSaveRequest
    ): MetadataSaveRequest {
        with(metadataSaveRequest) {
            return metadataSaveRequest.copy(
                source = getCurrentClusterName(context.localProjectId, context.localRepoName, context.task.name),
            )
        }
    }

    private fun buildMetadataDeleteRequest(
        context: ReplicaContext,
        metadataDeleteRequest: MetadataDeleteRequest
    ): MetadataDeleteRequest {
        with(metadataDeleteRequest) {
            return metadataDeleteRequest.copy(
                source = getCurrentClusterName(context.localProjectId, context.localRepoName, context.task.name),
            )
        }
    }

    private fun buildNodeDeleteRequest(context: ReplicaContext, node: NodeInfo): NodeDeleteRequest? {
        with(context) {
            // 外部集群仓库没有project/repoName
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return null
            return NodeDeleteRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                fullPath = node.fullPath,
                operator = node.createdBy,
                source = getCurrentClusterName(localProjectId, localRepoName, task.name),
                deletedDate = node.deleted
            )
        }
    }


    private fun buildDeletedNodeReplicaRequest(
        context: ReplicaContext, node: NodeInfo,
    ): DeletedNodeReplicationRequest? {
        return buildNodeCreateRequest(context, node)?.let { baseRequest ->
            DeletedNodeReplicationRequest(
                nodeCreateRequest = baseRequest,
                deleted = LocalDateTime.parse(node.deleted, DateTimeFormatter.ISO_DATE_TIME)
            )
        }
    }

    private fun syncNodeToFederatedCluster(context: ReplicaContext, node: NodeInfo): Boolean {
        with(context) {
            val request = if (node.deleted != null) {
                buildDeletedNodeReplicaRequest(this, node)?.also {
                    logger.info("The deleted node [${node.fullPath}] will be pushed to the federated cluster server!")
                }
            } else {
                buildNodeCreateRequest(this, node)?.also {
                    logger.info("The node [${node.fullPath}] will be pushed to the federated cluster server!")
                }
            } ?: return false

            if (node.deleted != null) {
                artifactReplicaClient!!.replicaDeletedNodeReplicationRequest(request as DeletedNodeReplicationRequest)
            } else {
                artifactReplicaClient!!.replicaNodeCreateRequest(request as NodeCreateRequest)
            }
            return true
        }
    }

    private fun buildNodeCreateRequest(context: ReplicaContext, node: NodeInfo): NodeCreateRequest? {
        with(context) {
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return null
            val metadata = if (task.setting.includeMetadata) {
                node.nodeMetadata ?: emptyList()
            } else {
                emptyList()
            }
            val updatedMetadata = mergeMetadata(
                metadata, mutableListOf(MetadataModel(FEDERATED, false, true))
            )

            return NodeCreateRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                fullPath = node.fullPath,
                folder = node.folder,
                overwrite = if (node.folder) false else true,
                size = if (node.folder) null else node.size,
                sha256 = if (node.folder) null else node.sha256!!,
                md5 = if (node.folder) null else node.md5!!,
                crc64ecma = if (node.folder) null else node.crc64ecma,
                nodeMetadata = if (node.folder) emptyList() else updatedMetadata,
                operator = node.createdBy,
                createdBy = node.createdBy,
                createdDate = LocalDateTime.parse(node.createdDate, DateTimeFormatter.ISO_DATE_TIME),
                lastModifiedBy = node.lastModifiedBy,
                lastModifiedDate = LocalDateTime.parse(node.lastModifiedDate, DateTimeFormatter.ISO_DATE_TIME),
                source = getCurrentClusterName(localProjectId, localRepoName, task.name)
            )
        }
    }

    private fun mergeMetadata(
        oldMetadata: List<MetadataModel>,
        addMetadata: List<MetadataModel>
    ): List<MetadataModel> {
        val metadataMap = oldMetadata.associateByTo(HashMap(oldMetadata.size + addMetadata.size)) { it.key }
        addMetadata.forEach { metadataMap[it.key] = it }
        return metadataMap.values.toMutableList()
    }

    private fun buildBlockNodeCreateRequest(
        context: ReplicaContext,
        blockNode: TBlockNode,
        uploadId: String
    ): BlockNodeCreateRequest? {
        with(context) {
            // 外部集群仓库没有project/repoName
            if (remoteProjectId.isNullOrBlank() || remoteRepoName.isNullOrBlank()) return null
            return BlockNodeCreateRequest(
                projectId = remoteProjectId,
                repoName = remoteRepoName,
                fullPath = blockNode.nodeFullPath,
                expireDate = blockNode.expireDate,
                size = blockNode.size,
                sha256 = blockNode.sha256,
                crc64ecma = blockNode.crc64ecma,
                startPos = blockNode.startPos,
                endPos = blockNode.endPos,
                createdBy = blockNode.createdBy,
                createdDate = blockNode.createdDate,
                uploadId = uploadId,
                deleted = blockNode.deleted,
                source = getCurrentClusterName(localProjectId, localRepoName, task.name)
            )
        }
    }

    private fun buildDeletedNodeMetadataSaveRequest(
        context: ReplicaContext,
        node: NodeInfo,
        taskName: String,
    ): DeletedNodeMetadataSaveRequest {
        return DeletedNodeMetadataSaveRequest(
            metadataSaveRequest = buildMetadataSaveRequest(context, node, taskName),
            deleted = LocalDateTime.parse(node.deleted, DateTimeFormatter.ISO_DATE_TIME)
        )
    }

    private fun buildMetadataSaveRequest(
        context: ReplicaContext,
        node: NodeInfo,
        taskName: String,
    ): MetadataSaveRequest {
        return MetadataSaveRequest(
            projectId = context.remoteProjectId!!,
            repoName = context.remoteRepoName!!,
            fullPath = node.fullPath,
            nodeMetadata = listOf(MetadataModel(FEDERATED, true, true)),
            operator = node.createdBy,
            source = getCurrentClusterName(node.projectId, node.repoName, taskName)
        )
    }

    companion object {

        private val logger = LoggerFactory.getLogger(FederationReplicator::class.java)
        private const val PAGE_SIZE = 500

        fun buildRemoteRepoCacheKey(clusterInfo: ClusterInfo, projectId: String, repoName: String): String {
            return "$projectId/$repoName/${clusterInfo.hashCode()}"
        }
    }
}