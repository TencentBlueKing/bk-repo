/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.controller.service

import com.tencent.bkrepo.auth.api.ServiceAccountClient
import com.tencent.bkrepo.auth.pojo.account.AccountInfo
import com.tencent.bkrepo.auth.api.ServiceExternalPermissionClient
import com.tencent.bkrepo.auth.api.ServiceKeyClient
import com.tencent.bkrepo.auth.api.ServiceOauthAuthorizationClient
import com.tencent.bkrepo.auth.api.ServicePermissionClient
import com.tencent.bkrepo.auth.api.ServiceProxyClient
import com.tencent.bkrepo.auth.api.ServiceRepoModeClient
import com.tencent.bkrepo.auth.api.ServiceRoleClient
import com.tencent.bkrepo.auth.api.ServiceTemporaryTokenClient
import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.auth.pojo.externalPermission.ExternalPermission
import com.tencent.bkrepo.auth.pojo.key.KeyInfo
import com.tencent.bkrepo.auth.pojo.oauth.OauthTokenInfo
import com.tencent.bkrepo.auth.pojo.permission.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.permission.PersonalPathInfo
import com.tencent.bkrepo.auth.pojo.proxy.ProxyInfo
import com.tencent.bkrepo.auth.pojo.proxy.ProxyStatus
import com.tencent.bkrepo.auth.pojo.role.RoleInfo
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.auth.pojo.user.CreateUserRequest
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.metadata.service.metadata.PackageMetadataService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.context.FederationReplicaContext
import com.tencent.bkrepo.replication.constant.DEFAULT_VERSION
import com.tencent.bkrepo.replication.enums.FederatedNodeAction
import com.tencent.bkrepo.replication.pojo.request.AccountReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.BlockNodeCreateFinishRequest
import com.tencent.bkrepo.replication.pojo.request.CheckPermissionRequest
import com.tencent.bkrepo.replication.pojo.request.ExternalPermissionReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.KeyReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.OauthTokenReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.PermissionReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.PersonalPathReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ProxyReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.RepoAuthConfigReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.ReplicaAction
import com.tencent.bkrepo.replication.pojo.request.RoleReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.TemporaryTokenReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.UserReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.DirectChildrenPage
import com.tencent.bkrepo.replication.pojo.request.DirectChildrenRequest
import com.tencent.bkrepo.replication.pojo.request.NodeExistCheckRequest
import com.tencent.bkrepo.replication.pojo.request.NodeCountRequest
import com.tencent.bkrepo.replication.pojo.request.NodeCountResult
import com.tencent.bkrepo.replication.pojo.request.PackageDeleteRequest
import com.tencent.bkrepo.replication.pojo.request.PathCountRequest
import com.tencent.bkrepo.replication.pojo.request.PathStatsRequest
import com.tencent.bkrepo.replication.pojo.request.PathStatsResult
import com.tencent.bkrepo.replication.pojo.request.PackageVersionDeleteRequest
import com.tencent.bkrepo.replication.pojo.request.PackageVersionExistCheckRequest
import com.tencent.bkrepo.repository.pojo.blocknode.BlockNodeDetail
import com.tencent.bkrepo.repository.pojo.blocknode.service.BlockNodeCreateRequest
import com.tencent.bkrepo.repository.pojo.metadata.DeletedNodeMetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.packages.PackageMetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.packages.PackageMetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.DeletedNodeReplicationRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.tencent.bkrepo.replication.manager.LocalDataManager

/**
 * 集群间数据同步接口
 */
@RestController
class ArtifactReplicaController(
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
    private val nodeService: NodeService,
    private val packageService: PackageService,
    private val metadataService: MetadataService,
    private val packageMetadataService: PackageMetadataService,
    private val userResource: ServiceUserClient,
    private val permissionManager: PermissionManager,
    private val blockNodeService: BlockNodeService,
    private val localDataManager: LocalDataManager,
    private val localPermissionClient: ServicePermissionClient,
    private val localRoleClient: ServiceRoleClient,
    private val localAccountClient: ServiceAccountClient,
    private val localExternalPermissionClient: ServiceExternalPermissionClient,
    private val localTemporaryTokenClient: ServiceTemporaryTokenClient,
    private val localOauthAuthorizationClient: ServiceOauthAuthorizationClient,
    private val localProxyClient: ServiceProxyClient,
    private val localKeyClient: ServiceKeyClient,
    private val localRepoModeClient: ServiceRepoModeClient,
) : ArtifactReplicaClient {

    @Value("\${spring.application.version:$DEFAULT_VERSION}")
    private var version: String = DEFAULT_VERSION

    @Principal(type = PrincipalType.GENERAL)
    override fun ping(token: String) = ResponseBuilder.success()

    @Permission(ResourceType.REPLICATION, PermissionAction.VIEW)
    override fun version() = ResponseBuilder.success(version)

    @Permission(ResourceType.REPLICATION, PermissionAction.VIEW)
    override fun checkNodeExist(
        projectId: String,
        repoName: String,
        fullPath: String,
        deleted: String?
    ): Response<Boolean> {
        val result = deleted?.let {
            nodeService.getDeletedNodeDetail(projectId, repoName, fullPath, it.toLocalDateTime()) != null
        } ?: nodeService.checkExist(ArtifactInfo(projectId, repoName, fullPath))
        return ResponseBuilder.success(result)
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.VIEW)
    override fun checkNodeExistList(
        request: NodeExistCheckRequest,
    ): Response<List<String>> {
        return ResponseBuilder.success(
            nodeService.listExistFullPath(
                request.projectId,
                request.repoName,
                request.fullPathList
            )
        )
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaNodeCreateRequest(request: NodeCreateRequest): Response<NodeDetail> {
        val checkResult = checkFederatedNodeConflict(request)
        val nodePath = buildNodePath(request.projectId, request.repoName, request.fullPath)
        logReplicaSync(checkResult.action, nodePath, request.source, checkResult.reason)

        val resultNode = when (checkResult.action) {
            FederatedNodeAction.CREATE -> nodeService.createNode(request)
            FederatedNodeAction.SKIP -> checkResult.existNode!!
            FederatedNodeAction.OVERWRITE -> nodeService.createNode(request.copy(overwrite = true))
            FederatedNodeAction.MERGE_METADATA -> {
                mergeNodeMetadata(request)
                checkResult.existNode!!
            }
        }
        return ResponseBuilder.success(resultNode)
    }

    /** 合并节点元数据 */
    private fun mergeNodeMetadata(request: NodeCreateRequest) {
        request.nodeMetadata?.takeIf { it.isNotEmpty() }?.let { metadata ->
            metadataService.saveMetadata(
                MetadataSaveRequest(
                    projectId = request.projectId,
                    repoName = request.repoName,
                    fullPath = request.fullPath,
                    nodeMetadata = metadata,
                    operator = request.operator,
                    source = request.source
                )
            )
        }
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaDeletedNodeReplicationRequest(request: DeletedNodeReplicationRequest): Response<NodeDetail> {
        val nodeReq = request.nodeCreateRequest
        val checkResult = checkFederatedDeletedNodeConflict(request)
        val nodePath = buildNodePath(nodeReq.projectId, nodeReq.repoName, nodeReq.fullPath)
        logReplicaSync(checkResult.action, "deleted:$nodePath", nodeReq.source, checkResult.reason)

        val resultNode = when (checkResult.action) {
            FederatedNodeAction.CREATE -> nodeService.replicaDeletedNode(request)
            FederatedNodeAction.SKIP, FederatedNodeAction.MERGE_METADATA -> checkResult.existNode!!
            FederatedNodeAction.OVERWRITE -> markNodeAsDeleted(request, checkResult.existNode!!)
        }
        return ResponseBuilder.success(resultNode)
    }

    /** 将活跃节点标记为删除 */
    private fun markNodeAsDeleted(request: DeletedNodeReplicationRequest, existNode: NodeDetail): NodeDetail {
        with(request.nodeCreateRequest) {
            nodeService.deleteNodeById(
                projectId, repoName, fullPath, operator,
                existNode.nodeInfo.id!!, request.deleted, source
            )
        }
        return nodeService.getDeletedNodeDetail(
            request.nodeCreateRequest.projectId,
            request.nodeCreateRequest.repoName,
            request.nodeCreateRequest.fullPath,
            request.deleted
        )!!
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaNodeRenameRequest(request: NodeRenameRequest): Response<Void> {
        nodeService.renameNode(request)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaNodeUpdateRequest(request: NodeUpdateRequest): Response<Void> {
        nodeService.updateNode(request)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaNodeCopyRequest(request: NodeMoveCopyRequest): Response<NodeDetail> {
        return ResponseBuilder.success(nodeService.copyNode(request))
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaNodeMoveRequest(request: NodeMoveCopyRequest): Response<NodeDetail> {
        return ResponseBuilder.success(nodeService.moveNode(request))
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaNodeDeleteRequest(request: NodeDeleteRequest): Response<NodeDeleteResult> {
        val nodePath = buildNodePath(request.projectId, request.repoName, request.fullPath)

        // 如果指定了 deletedDate，检查是否存在更新的节点
        request.deletedDate?.takeIf { it.isNotEmpty() }?.let { deletedDate ->
            val checkResult = checkFederatedNodeDeleteConflict(
                projectId = request.projectId,
                repoName = request.repoName,
                fullPath = request.fullPath,
                sourceDeletedDate = deletedDate.toLocalDateTime(),
                source = request.source
            )
            if (checkResult.action == FederatedNodeAction.SKIP) {
                logReplicaSync(FederatedNodeAction.SKIP, nodePath, request.source, checkResult.reason)
                return ResponseBuilder.success(NodeDeleteResult(0, 0, LocalDateTime.now()))
            }
        }

        logReplicaSync(FederatedNodeAction.CREATE, nodePath, request.source, "DELETE")
        return ResponseBuilder.success(nodeService.deleteNode(request))
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaRepoCreateRequest(request: RepoCreateRequest): Response<RepositoryDetail> {
        return repositoryService.getRepoDetail(request.projectId, request.name)?.let { ResponseBuilder.success(it) }
            ?: ResponseBuilder.success(repositoryService.createRepo(request))
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaRepoUpdateRequest(request: RepoUpdateRequest): Response<Void> {
        repositoryService.updateRepo(request)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaRepoDeleteRequest(request: RepoDeleteRequest): Response<Void> {
        repositoryService.deleteRepo(request)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.VIEW)
    override fun checkRepoPermission(request: CheckPermissionRequest): Response<Boolean> {
        try {
            // 认证
            if (userResource.checkToken(request.username, request.password).data != true) {
                throw PermissionException()
            }
            // 鉴权
            permissionManager.checkRepoPermission(
                action = PermissionAction.valueOf(request.action),
                projectId = request.projectId,
                repoName = request.repoName,
                userId = request.username
            )
        } catch (e: PermissionException) {
            return ResponseBuilder.success(false)
        }
        return ResponseBuilder.success(true)
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaProjectCreateRequest(request: ProjectCreateRequest, tenantId: String?): Response<ProjectInfo> {
        return projectService.getProjectInfo(request.name)?.let { ResponseBuilder.success(it) }
            ?: ResponseBuilder.success(projectService.createProject(request))
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaMetadataSaveRequest(request: MetadataSaveRequest): Response<Void> {
        metadataService.saveMetadata(request)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaMetadataSaveRequestForDeletedNode(request: DeletedNodeMetadataSaveRequest): Response<Void> {
        metadataService.saveMetadataForDeletedNode(request)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaMetadataDeleteRequest(request: MetadataDeleteRequest): Response<Void> {
        metadataService.deleteMetadata(request)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaPackageMetadataSaveRequest(request: PackageMetadataSaveRequest): Response<Void> {
        packageMetadataService.saveMetadata(request)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaPackageMetadataDeleteRequest(request: PackageMetadataDeleteRequest): Response<Void> {
        packageMetadataService.deleteMetadata(request)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.VIEW)
    override fun checkPackageVersionExist(
        request: PackageVersionExistCheckRequest,
    ): Response<Boolean> {
        val packageVersion = packageService.findVersionByName(
            request.projectId,
            request.repoName,
            request.packageKey,
            request.versionName
        )
        return ResponseBuilder.success(packageVersion != null)
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaPackageVersionCreatedRequest(
        request: PackageVersionCreateRequest,
    ): Response<Void> {
        packageService.createPackageVersion(request)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaPackageDeleteRequest(request: PackageDeleteRequest): Response<Void> {
        with(request) {
            val packagePath = buildPackagePath(projectId, repoName, packageKey)
            val checkResult = checkFederatedPackageDeleteConflict(
                projectId, repoName, packageKey, deletedDate.toLocalDateTime(), source
            )
            if (checkResult.action == FederatedNodeAction.SKIP) {
                logReplicaSync(FederatedNodeAction.SKIP, packagePath, source, checkResult.reason)
            } else {
                logReplicaSync(FederatedNodeAction.CREATE, packagePath, source, "DELETE")
                packageService.deletePackage(projectId, repoName, packageKey)
            }
        }
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaPackageVersionDeleteRequest(request: PackageVersionDeleteRequest): Response<Void> {
        with(request) {
            val versionPath = buildVersionPath(projectId, repoName, packageKey, versionName)
            val checkResult = checkFederatedVersionDeleteConflict(
                projectId, repoName, packageKey, versionName, deletedDate.toLocalDateTime(), source
            )
            if (checkResult.action == FederatedNodeAction.SKIP) {
                logReplicaSync(FederatedNodeAction.SKIP, versionPath, source, checkResult.reason)
            } else {
                logReplicaSync(FederatedNodeAction.CREATE, versionPath, source, "DELETE")
                packageService.deleteVersion(projectId, repoName, packageKey, versionName)
            }
        }
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaBlockNodeCreateRequest(request: BlockNodeCreateRequest): Response<BlockNodeDetail> {
        // 获取仓库信息，如果不存在则抛出异常
        val repo = repositoryService.getRepoDetail(request.projectId, request.repoName)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, request.repoName)

        // 构建块节点对象
        val blockNode = buildTBlockNode(request)

        // 检查块是否已存在，如果存在直接返回
        if (blockNodeService.checkBlockExist(blockNode)) {
            return ResponseBuilder.success(toBlockNodeDetail(blockNode))
        }

        // 创建新的块节点
        val createdBlockNode = blockNodeService.createBlock(blockNode, repo.storageCredentials)
        return ResponseBuilder.success(toBlockNodeDetail(createdBlockNode))
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaBlockNodeCreateFinishRequest(request: BlockNodeCreateFinishRequest): Response<Void> {
        with(request) {
            blockNodeService.updateBlockUploadId(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                uploadId = uploadId
            )
            return ResponseBuilder.success()
        }
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.VIEW)
    override fun countNodes(request: NodeCountRequest): Response<NodeCountResult> {
        val count = nodeService.countFileNode(
            ArtifactInfo(request.projectId, request.repoName, request.rootPath)
        )
        return ResponseBuilder.success(NodeCountResult(fileCount = count))
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.VIEW)
    override fun countPackages(projectId: String, repoName: String): Response<Long> {
        val count = localDataManager.countPackages(projectId, repoName)
        return ResponseBuilder.success(count)
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.VIEW)
    override fun listPackages(
        projectId: String,
        repoName: String,
        pageNumber: Int,
        pageSize: Int
    ): Response<List<Any>> {
        val packages = localDataManager.listPackagesForDiff(projectId, repoName, pageNumber, pageSize)
        return ResponseBuilder.success(packages)
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.VIEW)
    override fun listDirectChildren(request: DirectChildrenRequest): Response<DirectChildrenPage> {
        val page = localDataManager.listDirectChildren(
            projectId = request.projectId,
            repoName = request.repoName,
            parentPath = request.parentPath,
            pageNumber = request.pageNumber,
            pageSize = request.pageSize
        )
        return ResponseBuilder.success(page)
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.VIEW)
    override fun countFilesUnderPath(request: PathCountRequest): Response<Long> {
        val count = localDataManager.countFilesUnderPath(
            projectId = request.projectId,
            repoName = request.repoName,
            path = request.path
        )
        return ResponseBuilder.success(count)
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.VIEW)
    override fun getPathStats(request: PathStatsRequest): Response<PathStatsResult> {
        val stats = localDataManager.getPathStats(
            projectId = request.projectId,
            repoName = request.repoName,
            rootPath = request.path,
            depth = request.depth
        )
        return ResponseBuilder.success(stats)
    }

    private fun buildTBlockNode(request: BlockNodeCreateRequest): TBlockNode {
        return with(request) {
            TBlockNode(
                projectId = projectId,
                repoName = repoName,
                nodeFullPath = fullPath,
                size = size,
                createdDate = createdDate,
                createdBy = createdBy,
                startPos = startPos,
                endPos = endPos,
                sha256 = sha256,
                crc64ecma = crc64ecma,
                uploadId = uploadId,
                expireDate = expireDate,
                deleted = deleted
            )
        }
    }

    private fun toBlockNodeDetail(blockNode: TBlockNode): BlockNodeDetail {
        return with(blockNode) {
            BlockNodeDetail(
                id = id,
                projectId = projectId,
                repoName = repoName,
                nodeFullPath = nodeFullPath,
                size = size,
                createdDate = createdDate,
                createdBy = createdBy,
                startPos = startPos,
                endPos = endPos,
                sha256 = sha256,
                crc64ecma = crc64ecma,
                uploadId = uploadId,
                expireDate = expireDate,
                deleted = deleted
            )
        }
    }

    /**
     * 检查联邦仓库节点创建冲突
     *
     * 处理场景：
     * 1. 目标节点不存在 -> CREATE
     * 2. sha256 相同 -> MERGE_METADATA（只合并元数据）
     * 3. sha256 不同，比较 lastModifiedDate：
     *    - 源节点更新 -> OVERWRITE
     *    - 目标节点更新或相等 -> SKIP
     */
    private fun checkFederatedNodeConflict(request: NodeCreateRequest): FederatedNodeCheckResult {
        with(request) {
            if (source.isNullOrEmpty()) return FederatedNodeCheckResult.allowNonFederated()

            val existNode = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
                ?: return FederatedNodeCheckResult.allowNotExist()

            // 目录类型不做覆盖处理
            if (folder || existNode.folder) {
                return FederatedNodeCheckResult.skip(existNode, "Folder node, skip overwrite")
            }

            // sha256 相同，只合并元数据
            if (sha256 == existNode.sha256) {
                return FederatedNodeCheckResult.mergeMetadata(existNode, "Same sha256 [$sha256]")
            }

            // sha256 不同，基于 lastModifiedDate 判断
            val sourceTime = lastModifiedDate ?: createdDate!!
            val targetTime = existNode.lastModifiedDate.toLocalDateTime()
            return compareAndDecide(existNode, sourceTime, targetTime)
        }
    }

    /** 比较时间戳并决定操作：源更新则覆盖，目标更新或相等则跳过 */
    private fun compareAndDecide(
        existNode: NodeDetail,
        sourceTime: LocalDateTime,
        targetTime: LocalDateTime
    ): FederatedNodeCheckResult = when {
        sourceTime.isAfter(targetTime) -> FederatedNodeCheckResult.overwrite(
            existNode, "Source [$sourceTime] > target [$targetTime]"
        )

        else -> FederatedNodeCheckResult.skip(
            existNode, "Target [$targetTime] >= source [$sourceTime]"
        )
    }

    /**
     * 检查联邦仓库节点删除冲突
     *
     * 处理场景：
     * 1. 目标节点不存在 -> 允许删除
     * 2. 目标节点在源删除时间之后创建或修改 -> SKIP（保护新数据）
     * 3. 目标节点在源删除时间之前最后修改 -> 允许删除
     */
    private fun checkFederatedNodeDeleteConflict(
        projectId: String,
        repoName: String,
        fullPath: String,
        sourceDeletedDate: LocalDateTime,
        source: String?
    ): FederatedNodeCheckResult {
        if (source.isNullOrEmpty()) return FederatedNodeCheckResult.allowNonFederated()

        val existNode = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
            ?: return FederatedNodeCheckResult.allowNotExist()

        return checkDeleteTimeConflict(
            existNode = existNode,
            createdDate = existNode.createdDate.toLocalDateTime(),
            lastModifiedDate = existNode.lastModifiedDate.toLocalDateTime(),
            sourceDeletedDate = sourceDeletedDate,
            resourceType = "node"
        )
    }

    /** 检查删除时间冲突：如果目标在源删除时间之后创建或修改，则跳过 */
    private fun checkDeleteTimeConflict(
        existNode: NodeDetail? = null,
        createdDate: LocalDateTime,
        lastModifiedDate: LocalDateTime,
        sourceDeletedDate: LocalDateTime,
        resourceType: String
    ): FederatedNodeCheckResult {
        if (createdDate.isAfter(sourceDeletedDate)) {
            return FederatedNodeCheckResult.skip(
                existNode, "Target $resourceType created [$createdDate] after source deleted [$sourceDeletedDate]"
            )
        }
        if (lastModifiedDate.isAfter(sourceDeletedDate)) {
            return FederatedNodeCheckResult.skip(
                existNode, "Target $resourceType modified [$lastModifiedDate] after source deleted [$sourceDeletedDate]"
            )
        }
        return FederatedNodeCheckResult(
            action = FederatedNodeAction.CREATE,
            existNode = existNode,
            reason = "Target $resourceType is older than source delete time"
        )
    }

    /**
     * 检查联邦仓库包删除冲突
     *
     * 处理场景：目标包在源删除时间之后创建或修改则跳过
     */
    private fun checkFederatedPackageDeleteConflict(
        projectId: String,
        repoName: String,
        packageKey: String,
        sourceDeletedDate: LocalDateTime,
        source: String?
    ): FederatedNodeCheckResult {
        if (source.isNullOrEmpty()) return FederatedNodeCheckResult.allowNonFederated()

        val existPackage = packageService.findPackageByKey(projectId, repoName, packageKey)
            ?: return FederatedNodeCheckResult.allowNotExist("package")

        return checkDeleteTimeConflict(
            createdDate = existPackage.createdDate,
            lastModifiedDate = existPackage.lastModifiedDate,
            sourceDeletedDate = sourceDeletedDate,
            resourceType = "package"
        )
    }

    /**
     * 检查联邦仓库版本删除冲突
     *
     * 处理场景：目标包或版本在源删除时间之后创建或修改则跳过
     */
    private fun checkFederatedVersionDeleteConflict(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String,
        sourceDeletedDate: LocalDateTime,
        source: String?
    ): FederatedNodeCheckResult {
        if (source.isNullOrEmpty()) return FederatedNodeCheckResult.allowNonFederated()

        // 检查包是否存在及其时间冲突
        val existPackage = packageService.findPackageByKey(projectId, repoName, packageKey)
            ?: return FederatedNodeCheckResult.allowNotExist("package")

        if (existPackage.createdDate.isAfter(sourceDeletedDate)) {
            return FederatedNodeCheckResult.skip(
                reason = "Target package created [${existPackage.createdDate}]" +
                    " after source deleted [$sourceDeletedDate]"
            )
        }

        // 检查版本是否存在及其时间冲突
        val existVersion = packageService.findVersionByName(projectId, repoName, packageKey, versionName)
            ?: return FederatedNodeCheckResult.allowNotExist("version")

        return checkDeleteTimeConflict(
            createdDate = existVersion.createdDate,
            lastModifiedDate = existVersion.lastModifiedDate,
            sourceDeletedDate = sourceDeletedDate,
            resourceType = "version"
        )
    }

    /**
     * 检查联邦仓库已删除节点同步冲突
     *
     * 处理场景：
     * 1. 目标已存在相同删除记录 -> SKIP
     * 2. 目标存在活跃节点且是同一个节点（创建时间相同） -> OVERWRITE（标记为删除）
     * 3. 目标存在活跃节点但不是同一个节点（创建时间不同） -> CREATE（创建删除记录，保留活跃节点）
     * 4. 目标不存在任何节点 -> CREATE（创建删除记录）
     */
    private fun checkFederatedDeletedNodeConflict(request: DeletedNodeReplicationRequest): FederatedNodeCheckResult {
        val nodeReq = request.nodeCreateRequest
        if (nodeReq.source.isNullOrEmpty()) return FederatedNodeCheckResult.allowNonFederated()

        // 检查是否已存在相同的删除记录
        val deletedNode = nodeService.getDeletedNodeDetail(
            nodeReq.projectId, nodeReq.repoName, nodeReq.fullPath, request.deleted
        )
        if (deletedNode != null) {
            return FederatedNodeCheckResult.skip(deletedNode, "Deleted record already exists [${request.deleted}]")
        }

        // 检查是否存在活跃节点
        val existNode = nodeService.getNodeDetail(
            ArtifactInfo(nodeReq.projectId, nodeReq.repoName, nodeReq.fullPath)
        ) ?: return FederatedNodeCheckResult(
            action = FederatedNodeAction.CREATE,
            reason = "No active node exists, create deleted record"
        )

        val existCreatedDate = existNode.createdDate.toLocalDateTime()

        // 判断是否为同一个节点（通过创建时间判断）
        val isSameNode = nodeReq.createdDate?.let { existCreatedDate.isEqual(it) } == true

        return if (isSameNode) {
            // 同一个节点，标记为删除
            return FederatedNodeCheckResult.overwrite(existNode, "Same node (same createdDate), mark as deleted")
        } else {
            // 不是同一个节点，创建删除记录（保留活跃节点）
            FederatedNodeCheckResult(
                action = FederatedNodeAction.CREATE,
                existNode = existNode,
                reason = "Different node (different createdDate), create deleted record and keep active node"
            )
        }
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaUserRequest(request: UserReplicaRequest): Response<Void> {
        FederationReplicaContext.markAsFederationWrite()
        try {
            when (request.action) {
                ReplicaAction.UPSERT -> {
                    userResource.upsertUserForFederation(
                        CreateUserRequest(
                            userId = request.userId,
                            name = request.name,
                            admin = request.admin,
                            asstUsers = request.asstUsers,
                            group = request.group,
                            email = request.email,
                            phone = request.phone,
                            tenantId = request.tenantId,
                        ),
                        hashedPwd = request.pwd
                    )
                }

                ReplicaAction.DELETE -> {
                    userResource.deleteUser(request.userId)
                }
            }
        } finally {
            FederationReplicaContext.clear()
        }
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaPermissionRequest(request: PermissionReplicaRequest): Response<Void> {
        FederationReplicaContext.markAsFederationWrite()
        try {
            val existing = findExistingPermission(request)
            when (request.action) {
                ReplicaAction.UPSERT -> {
                    // 先删旧权限（若存在），再创建，实现全量 upsert 语义
                    existing?.id?.let { localPermissionClient.deletePermission(it) }
                    localPermissionClient.createPermission(buildCreatePermissionRequest(request))
                }

                ReplicaAction.DELETE -> {
                    existing?.id?.let { localPermissionClient.deletePermission(it) }
                }
            }
        } finally {
            FederationReplicaContext.clear()
        }
        return ResponseBuilder.success()
    }

    private fun findExistingPermission(request: PermissionReplicaRequest):
        com.tencent.bkrepo.auth.pojo.permission.Permission? {
        val projectId = request.projectId
        return if (projectId != null) {
            localPermissionClient.listPermission(
                projectId = projectId,
                repoName = null,
                resourceType = request.resourceType
            ).data?.find { it.permName == request.permName }
        } else {
            // projectId 为 null 时（系统级权限），按 permName+resourceType 精确查找
            localPermissionClient.getPermissionByName(null, request.resourceType, request.permName).data
        }
    }

    private fun buildCreatePermissionRequest(request: PermissionReplicaRequest) = CreatePermissionRequest(
        resourceType = ResourceType.valueOf(request.resourceType),
        projectId = request.projectId,
        permName = request.permName,
        repos = request.repos,
        includePattern = request.includePattern,
        excludePattern = request.excludePattern,
        users = request.users,
        roles = request.roles,
        departments = request.departments,
        actions = request.actions.map { PermissionAction.valueOf(it) },
        createBy = request.createBy,
        updatedBy = request.updatedBy
    )

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaRoleRequest(request: RoleReplicaRequest): Response<Void> {
        FederationReplicaContext.markAsFederationWrite()
        try {
            val projectId = request.projectId ?: return ResponseBuilder.success()
            when (request.action) {
                ReplicaAction.UPSERT -> {
                    val existing = runCatching {
                        localRoleClient.listRoleByProject(projectId).data
                            ?.find { it.roleId == request.roleId }
                    }.getOrNull()
                    val roleInfo = RoleInfo(
                        roleId = request.roleId,
                        name = request.name,
                        type = request.type,
                        projectId = request.projectId,
                        repoName = request.repoName,
                        admin = request.admin,
                        users = request.users,
                        description = request.description
                    )
                    if (existing == null) {
                        localRoleClient.createRoleForFederation(roleInfo)
                    } else {
                        localRoleClient.updateRoleForFederation(roleInfo.copy(id = existing.id))
                    }
                }

                ReplicaAction.DELETE -> {
                    if (request.id == null) {
                        logger.warn("Skipping role DELETE: id is null for roleId=${request.roleId}")
                    } else {
                        localRoleClient.deleteRoleForFederation(request.id!!)
                    }
                }
            }
        } finally {
            FederationReplicaContext.clear()
        }
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaAccountRequest(request: AccountReplicaRequest): Response<Void> {
        FederationReplicaContext.markAsFederationWrite()
        try {
            when (request.action) {
                ReplicaAction.UPSERT -> {
                    val accountInfo = AccountInfo(
                        appId = request.appId,
                        locked = request.locked,
                        authorizationGrantTypes = request.authorizationGrantTypes,
                        homepageUrl = request.homepageUrl,
                        redirectUri = request.redirectUri,
                        avatarUrl = request.avatarUrl,
                        scope = request.scope,
                        description = request.description,
                        credentials = request.credentials
                    )
                    localAccountClient.upsertAccountForFederation(accountInfo)
                }

                ReplicaAction.DELETE -> {
                    localAccountClient.deleteAccountForFederation(request.appId)
                }
            }
        } finally {
            FederationReplicaContext.clear()
        }
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaExternalPermissionRequest(request: ExternalPermissionReplicaRequest): Response<Void> {
        FederationReplicaContext.markAsFederationWrite()
        try {
            when (request.action) {
                ReplicaAction.UPSERT -> {
                    val existing = runCatching {
                        localExternalPermissionClient.listExternalPermission().data
                            ?.find { it.projectId == request.projectId && it.repoName == request.repoName }
                    }.getOrNull()
                    val perm = ExternalPermission(
                        id = existing?.id ?: (request.id ?: ""),
                        url = request.url,
                        headers = request.headers,
                        projectId = request.projectId,
                        repoName = request.repoName,
                        scope = request.scope,
                        platformWhiteList = request.platformWhiteList,
                        enabled = request.enabled,
                        createdDate = LocalDateTime.now(),
                        createdBy = "",
                        lastModifiedDate = LocalDateTime.now(),
                        lastModifiedBy = ""
                    )
                    if (existing == null) {
                        localExternalPermissionClient.createExternalPermission(perm)
                    } else {
                        localExternalPermissionClient.updateExternalPermission(perm.copy(id = existing.id))
                    }
                }

                ReplicaAction.DELETE -> {
                    if (request.id == null) {
                        logger.warn(
                            "Skipping external permission DELETE: " +
                                "id is null for project=${request.projectId} repo=${request.repoName}"
                        )
                    } else {
                        localExternalPermissionClient.deleteExternalPermission(request.id!!)
                    }
                }
            }
        } finally {
            FederationReplicaContext.clear()
        }
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaTemporaryTokenRequest(request: TemporaryTokenReplicaRequest): Response<Void> {
        FederationReplicaContext.markAsFederationWrite()
        try {
            when (request.action) {
                ReplicaAction.UPSERT -> {
                    val existing = runCatching {
                        localTemporaryTokenClient.getTokenInfo(request.token).data
                    }.getOrNull()
                    if (existing != null) return ResponseBuilder.success()
                    val expireSeconds = request.expireDate?.let { dateStr ->
                        runCatching {
                            val expireTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
                            val remaining = java.time.Duration.between(LocalDateTime.now(), expireTime).seconds
                            if (remaining > 0) remaining else return ResponseBuilder.success()
                        }.getOrNull()
                    }
                    localTemporaryTokenClient.createToken(
                        TemporaryTokenCreateRequest(
                            projectId = request.projectId,
                            repoName = request.repoName,
                            fullPathSet = setOf(request.fullPath),
                            authorizedUserSet = request.authorizedUserList,
                            authorizedIpSet = request.authorizedIpList,
                            expireSeconds = expireSeconds ?: java.time.Duration.ofDays(1).seconds,
                            permits = request.permits,
                            type = TokenType.valueOf(request.type),
                            createdBy = request.createdBy
                        )
                    )
                }

                ReplicaAction.DELETE -> {
                    localTemporaryTokenClient.deleteToken(request.token)
                }
            }
        } finally {
            FederationReplicaContext.clear()
        }
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaOauthTokenRequest(request: OauthTokenReplicaRequest): Response<Void> {
        FederationReplicaContext.markAsFederationWrite()
        try {
            when (request.action) {
                ReplicaAction.UPSERT -> {
                    localOauthAuthorizationClient.createOauthTokenForFederation(
                        OauthTokenInfo(
                            accessToken = request.accessToken,
                            refreshToken = request.refreshToken,
                            expireSeconds = request.expireSeconds,
                            type = request.type,
                            accountId = request.accountId,
                            userId = request.userId,
                            scope = request.scope,
                            issuedAt = request.issuedAt
                        )
                    )
                }

                ReplicaAction.DELETE -> {
                    localOauthAuthorizationClient.deleteOauthTokenForFederation(request.accessToken)
                }
            }
        } finally {
            FederationReplicaContext.clear()
        }
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaPersonalPathRequest(request: PersonalPathReplicaRequest): Response<Void> {
        FederationReplicaContext.markAsFederationWrite()
        try {
            when (request.action) {
                ReplicaAction.UPSERT -> {
                    localPermissionClient.createPersonalPath(
                        PersonalPathInfo(
                            userId = request.userId,
                            projectId = request.projectId,
                            repoName = request.repoName,
                            fullPath = request.fullPath
                        )
                    )
                }

                ReplicaAction.DELETE -> {
                    localPermissionClient.deletePersonalPath(
                        projectId = request.projectId,
                        repoName = request.repoName,
                        userId = request.userId
                    )
                }
            }
        } finally {
            FederationReplicaContext.clear()
        }
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaProxyRequest(request: ProxyReplicaRequest): Response<Void> {
        FederationReplicaContext.markAsFederationWrite()
        try {
            when (request.action) {
                ReplicaAction.UPSERT -> {
                    val existing = runCatching {
                        localProxyClient.listProxyByProject(request.projectId).data
                            ?.find { it.name == request.name }
                    }.getOrNull()
                    val proxyInfo = ProxyInfo(
                        name = request.name,
                        displayName = request.displayName,
                        projectId = request.projectId,
                        clusterName = request.clusterName,
                        domain = request.domain,
                        ip = "",
                        status = ProxyStatus.OFFLINE,
                        syncRateLimit = request.syncRateLimit,
                        syncTimeRange = request.syncTimeRange,
                        cacheExpireDays = request.cacheExpireDays
                    )
                    if (existing == null) {
                        localProxyClient.createProxyForFederation(proxyInfo)
                    } else {
                        localProxyClient.updateProxyForFederation(proxyInfo)
                    }
                }

                ReplicaAction.DELETE -> {
                    localProxyClient.deleteProxyForFederation(request.projectId, request.name)
                }
            }
        } finally {
            FederationReplicaContext.clear()
        }
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaKeyRequest(request: KeyReplicaRequest): Response<Void> {
        FederationReplicaContext.markAsFederationWrite()
        try {
            when (request.action) {
                ReplicaAction.UPSERT -> {
                    // 按 fingerprint 幂等：已存在则跳过
                    val existing = runCatching {
                        localKeyClient.listKeyByUserId(request.userId).data
                            ?.find { it.fingerprint == request.fingerprint }
                    }.getOrNull()
                    if (existing == null) {
                        val createAt = runCatching {
                            LocalDateTime.parse(request.createAt, DateTimeFormatter.ISO_DATE_TIME)
                        }.getOrDefault(LocalDateTime.now())
                        localKeyClient.createKeyForFederation(
                            KeyInfo(
                                id = request.id,
                                name = request.name,
                                key = request.key,
                                fingerprint = request.fingerprint,
                                userId = request.userId,
                                createAt = createAt
                            )
                        )
                    }
                }

                ReplicaAction.DELETE -> {
                    val remoteKey = runCatching {
                        localKeyClient.listKeyByUserId(request.userId).data
                            ?.find { it.fingerprint == request.fingerprint }
                    }.getOrNull()
                    if (remoteKey != null) {
                        localKeyClient.deleteKeyForFederation(remoteKey.id)
                    } else {
                        logger.warn(
                            "Key fingerprint=${request.fingerprint} user=${request.userId} not found on remote," +
                                " skipping delete"
                        )
                    }
                }
            }
        } finally {
            FederationReplicaContext.clear()
        }
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPLICATION, PermissionAction.WRITE)
    override fun replicaRepoAuthConfigRequest(request: RepoAuthConfigReplicaRequest): Response<Void> {
        FederationReplicaContext.markAsFederationWrite()
        try {
            when (request.action) {
                ReplicaAction.UPSERT -> {
                    val accessControlMode = runCatching {
                        com.tencent.bkrepo.auth.pojo.enums.AccessControlMode.valueOf(request.accessControlMode)
                    }.getOrDefault(com.tencent.bkrepo.auth.pojo.enums.AccessControlMode.DEFAULT)
                    localRepoModeClient.upsertRepoAuthConfig(
                        projectId = request.projectId,
                        repoName = request.repoName,
                        accessControlMode = accessControlMode,
                        officeDenyGroupSet = request.officeDenyGroupSet,
                        bkiamv3Check = request.bkiamv3Check
                    )
                }

                ReplicaAction.DELETE -> {
                    // RepoAuthConfig 通常不删除，仅 UPSERT；若需要删除可在未来扩展
                    logger.info(
                        "Skipping repo auth config DELETE (not supported):" +
                            " ${request.projectId}/${request.repoName}"
                    )
                }
            }
        } finally {
            FederationReplicaContext.clear()
        }
        return ResponseBuilder.success()
    }

    /** ISO 日期时间字符串解析为 LocalDateTime */
    private fun String.toLocalDateTime(): LocalDateTime =
        LocalDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME)

    /** 构建节点路径 */
    private fun buildNodePath(projectId: String, repoName: String, fullPath: String) =
        "/$projectId/$repoName$fullPath"

    /** 构建包路径 */
    private fun buildPackagePath(projectId: String, repoName: String, packageKey: String) =
        "/$projectId/$repoName/package/$packageKey"

    /** 构建版本路径 */
    private fun buildVersionPath(projectId: String, repoName: String, packageKey: String, versionName: String) =
        "/$projectId/$repoName/package/$packageKey/version/$versionName"

    /** 记录同步操作日志 */
    private fun logReplicaSync(action: FederatedNodeAction, path: String, source: String?, reason: String = "") {
        val reasonSuffix = if (reason.isNotEmpty()) ", reason: $reason" else ""
        logger.info("Replica sync: $action [$path] from [$source]$reasonSuffix")
    }

    /**
     * 联邦仓库节点冲突检查结果
     */
    data class FederatedNodeCheckResult(
        val action: FederatedNodeAction,
        val existNode: NodeDetail? = null,
        val reason: String = ""
    ) {
        companion object {
            /** 非联邦同步，允许操作 */
            fun allowNonFederated() = FederatedNodeCheckResult(
                action = FederatedNodeAction.CREATE,
                reason = "Non-federated sync request"
            )

            /** 目标资源不存在，允许操作 */
            fun allowNotExist(resourceType: String = "node") = FederatedNodeCheckResult(
                action = FederatedNodeAction.CREATE,
                reason = "Target $resourceType does not exist"
            )

            /** 跳过操作 */
            fun skip(existNode: NodeDetail? = null, reason: String) = FederatedNodeCheckResult(
                action = FederatedNodeAction.SKIP,
                existNode = existNode,
                reason = reason
            )

            /** 覆盖目标 */
            fun overwrite(existNode: NodeDetail, reason: String) = FederatedNodeCheckResult(
                action = FederatedNodeAction.OVERWRITE,
                existNode = existNode,
                reason = reason
            )

            /** 合并元数据 */
            fun mergeMetadata(existNode: NodeDetail, reason: String) = FederatedNodeCheckResult(
                action = FederatedNodeAction.MERGE_METADATA,
                existNode = existNode,
                reason = reason
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactReplicaController::class.java)
    }
}
