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

import com.tencent.bkrepo.auth.api.ServiceUserClient
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.packages.PackageService
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.exception.PermissionException
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.api.ArtifactReplicaClient
import com.tencent.bkrepo.replication.constant.DEFAULT_VERSION
import com.tencent.bkrepo.replication.pojo.request.CheckPermissionRequest
import com.tencent.bkrepo.replication.pojo.request.NodeExistCheckRequest
import com.tencent.bkrepo.replication.pojo.request.PackageDeleteRequest
import com.tencent.bkrepo.replication.pojo.request.PackageVersionDeleteRequest
import com.tencent.bkrepo.replication.pojo.request.PackageVersionExistCheckRequest
import com.tencent.bkrepo.repository.pojo.blocknode.BlockNodeDetail
import com.tencent.bkrepo.repository.pojo.blocknode.service.BlockNodeCreateRequest
import com.tencent.bkrepo.repository.pojo.metadata.DeletedNodeMetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 集群间数据同步接口
 */
@Principal(type = PrincipalType.ADMIN)
@RestController
class ArtifactReplicaController(
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
    private val nodeService: NodeService,
    private val packageService: PackageService,
    private val metadataService: MetadataService,
    private val userResource: ServiceUserClient,
    private val permissionManager: PermissionManager,
    private val blockNodeService: BlockNodeService,
) : ArtifactReplicaClient {

    @Value("\${spring.application.version:$DEFAULT_VERSION}")
    private var version: String = DEFAULT_VERSION

    @Principal(type = PrincipalType.GENERAL)
    override fun ping(token: String) = ResponseBuilder.success()

    override fun version() = ResponseBuilder.success(version)

    override fun checkNodeExist(
        projectId: String,
        repoName: String,
        fullPath: String,
        deleted: String?
    ): Response<Boolean> {
        val result = if (deleted == null) {
            nodeService.checkExist(ArtifactInfo(projectId, repoName, fullPath))
        } else {
            val deletedDate = LocalDateTime.parse(deleted, DateTimeFormatter.ISO_DATE_TIME)
            val nodeDetail = nodeService.getDeletedNodeDetail(projectId, repoName, fullPath, deletedDate)
            nodeDetail != null
        }
        return ResponseBuilder.success(result)
    }

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

    override fun replicaNodeCreateRequest(request: NodeCreateRequest): Response<NodeDetail> {
        federatedNodeDeletedCheck(
            projectId = request.projectId,
            repoName = request.repoName,
            fullPath = request.fullPath,
            compareDate = request.createdDate!!,
            source = request.source
        )
        return ResponseBuilder.success(nodeService.createNode(request))
    }

    override fun replicaDeletedNodeReplicationRequest(request: DeletedNodeReplicationRequest): Response<NodeDetail> {
        val existingNode = checkAndHandleExistingNodes(request)
        return if (existingNode != null) {
            ResponseBuilder.success(existingNode)
        } else {
            ResponseBuilder.success(nodeService.replicaDeletedNode(request))
        }
    }

    override fun replicaNodeRenameRequest(request: NodeRenameRequest): Response<Void> {
        nodeService.renameNode(request)
        return ResponseBuilder.success()
    }

    override fun replicaNodeUpdateRequest(request: NodeUpdateRequest): Response<Void> {
        nodeService.updateNode(request)
        return ResponseBuilder.success()
    }

    override fun replicaNodeCopyRequest(request: NodeMoveCopyRequest): Response<NodeDetail> {
        return ResponseBuilder.success(nodeService.copyNode(request))
    }

    override fun replicaNodeMoveRequest(request: NodeMoveCopyRequest): Response<NodeDetail> {
        return ResponseBuilder.success(nodeService.moveNode(request))
    }

    override fun replicaNodeDeleteRequest(request: NodeDeleteRequest): Response<NodeDeleteResult> {
        federatedNodeDeletedCheck(
            request.projectId,
            request.repoName,
            request.fullPath,
            LocalDateTime.parse(request.deletedDate!!, DateTimeFormatter.ISO_DATE_TIME),
            request.source
        )
        return ResponseBuilder.success(nodeService.deleteNode(request))
    }

    override fun replicaRepoCreateRequest(request: RepoCreateRequest): Response<RepositoryDetail> {
        return repositoryService.getRepoDetail(request.projectId, request.name)?.let { ResponseBuilder.success(it) }
            ?: ResponseBuilder.success(repositoryService.createRepo(request))
    }

    override fun replicaRepoUpdateRequest(request: RepoUpdateRequest): Response<Void> {
        repositoryService.updateRepo(request)
        return ResponseBuilder.success()
    }

    override fun replicaRepoDeleteRequest(request: RepoDeleteRequest): Response<Void> {
        repositoryService.deleteRepo(request)
        return ResponseBuilder.success()
    }

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

    override fun replicaProjectCreateRequest(request: ProjectCreateRequest): Response<ProjectInfo> {
        return projectService.getProjectInfo(request.name)?.let { ResponseBuilder.success(it) }
            ?: ResponseBuilder.success(projectService.createProject(request))
    }

    override fun replicaMetadataSaveRequest(request: MetadataSaveRequest): Response<Void> {
        metadataService.saveMetadata(request)
        return ResponseBuilder.success()
    }

    override fun replicaMetadataSaveRequestForDeletedNode(request: DeletedNodeMetadataSaveRequest): Response<Void> {
        metadataService.saveMetadataForDeletedNode(request)
        return ResponseBuilder.success()
    }

    override fun replicaMetadataDeleteRequest(request: MetadataDeleteRequest): Response<Void> {
        metadataService.deleteMetadata(request)
        return ResponseBuilder.success()
    }

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

    override fun replicaPackageVersionCreatedRequest(
        request: PackageVersionCreateRequest,
    ): Response<Void> {
        packageService.createPackageVersion(request)
        return ResponseBuilder.success()
    }

    override fun replicaPackageDeleteRequest(request: PackageDeleteRequest): Response<Void> {
        with(request) {
            federatedPackageDeletedCheck(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                compareDate = LocalDateTime.parse(deletedDate, DateTimeFormatter.ISO_DATE_TIME)
            )
            packageService.deletePackage(projectId, repoName, packageKey)
        }
        return ResponseBuilder.success()
    }

    override fun replicaPackageVersionDeleteRequest(request: PackageVersionDeleteRequest): Response<Void> {
        with(request) {
            federatedPackageDeletedCheck(
                projectId = projectId,
                repoName = repoName,
                packageKey = packageKey,
                compareDate = LocalDateTime.parse(deletedDate, DateTimeFormatter.ISO_DATE_TIME),
                versionName = versionName
            )
            packageService.deleteVersion(projectId, repoName, packageKey, versionName)
        }
        return ResponseBuilder.success()
    }

    override fun replicaBlockNodeCreateRequest(request: BlockNodeCreateRequest): Response<BlockNodeDetail> {
        val blockNode = buildTBlockNode(request)
        val repo = repositoryService.getRepoDetail(request.projectId, request.repoName)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, request.repoName)
        val result = blockNodeService.createBlock(blockNode, repo.storageCredentials)
        return ResponseBuilder.success(toBlockNodeDetail(result))
    }

    private fun buildTBlockNode(request: BlockNodeCreateRequest): TBlockNode {
        return TBlockNode(
            projectId = request.projectId,
            repoName = request.repoName,
            nodeFullPath = request.fullPath,
            size = request.size,
            createdDate = request.createdDate,
            createdBy = request.createdBy,
            startPos = request.startPos,
            endPos = request.endPos,
            sha256 = request.sha256,
            crc64ecma = request.crc64ecma,
            uploadId = request.uploadId,
            expireDate = request.expireDate
        )
    }

    private fun toBlockNodeDetail(blockNode: TBlockNode): BlockNodeDetail {
        return BlockNodeDetail(
            id = blockNode.id,
            projectId = blockNode.projectId,
            repoName = blockNode.repoName,
            nodeFullPath = blockNode.nodeFullPath,
            size = blockNode.size,
            createdDate = blockNode.createdDate,
            createdBy = blockNode.createdBy,
            startPos = blockNode.startPos,
            endPos = blockNode.endPos,
            sha256 = blockNode.sha256,
            crc64ecma = blockNode.crc64ecma,
            uploadId = blockNode.uploadId,
            expireDate = blockNode.expireDate,
            deleted = blockNode.deleted,
        )
    }


    private fun federatedNodeDeletedCheck(
        projectId: String,
        repoName: String,
        fullPath: String,
        compareDate: LocalDateTime,
        source: String?,
    ) {
        if (source.isNullOrEmpty()) return
        val existNode = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
        if (existNode != null) {
            val existCreatedDate = LocalDateTime.parse(existNode.createdDate, DateTimeFormatter.ISO_DATE_TIME)
            if (existCreatedDate.isAfter(compareDate)) {
                return
            }
        }
    }

    private fun federatedPackageDeletedCheck(
        projectId: String,
        repoName: String,
        packageKey: String,
        compareDate: LocalDateTime,
        versionName: String? = null,
    ) {
        val existPackage = packageService.findPackageByKey(projectId, repoName, packageKey) ?: return

        // 检查包的创建日期是否晚于比较日期
        if (existPackage.createdDate.isAfter(compareDate)) {
            return
        }

        // 如果未指定版本名称，则无需检查版本
        if (versionName.isNullOrEmpty()) return

        // 检查指定版本是否存在
        val existVersion = packageService.findVersionByName(projectId, repoName, packageKey, versionName) ?: return

        // 检查版本的修改日期是否晚于比较日期
        if (existVersion.lastModifiedDate.isAfter(compareDate)) {
            return
        }
    }

    private fun checkAndHandleExistingNodes(request: DeletedNodeReplicationRequest): NodeDetail? {
        with(request.nodeCreateRequest) {
            // 检查是否存在已删除的节点
            val deletedNode = nodeService.getDeletedNodeDetail(projectId, repoName, fullPath, request.deleted)
            if (deletedNode != null) {
                return deletedNode
            }

            // 检查是否存在活跃节点
            val existNode = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath))
            if (existNode != null) {
                val existCreatedDate = LocalDateTime.parse(existNode.createdDate, DateTimeFormatter.ISO_DATE_TIME)
                if (existCreatedDate.isEqual(createdDate) && existNode.createdBy == createdBy) {
                    // 如果活跃节点符合条件，则删除并返回
                    nodeService.deleteNodeById(
                        projectId, repoName, fullPath, operator, existNode.nodeInfo.id!!, request.deleted
                    )
                    return nodeService.getDeletedNodeDetail(projectId, repoName, fullPath, request.deleted)
                }
            }
            return null
        }
    }
}