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
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
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
import com.tencent.bkrepo.replication.pojo.request.PackageVersionExistCheckRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
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
    ): Response<Boolean> {
        return ResponseBuilder.success(nodeService.checkExist(ArtifactInfo(projectId, repoName, fullPath)))
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
        federatedCheck(request.projectId, request.repoName, request.fullPath, request.createdDate!!, request.source)
        return ResponseBuilder.success(nodeService.createNode(request))
    }

    override fun replicaNodeRenameRequest(request: NodeRenameRequest): Response<Void> {
        nodeService.renameNode(request)
        return ResponseBuilder.success()
    }

    override fun replicaNodeUpdateRequest(request: NodeUpdateRequest): Response<Void> {
        nodeService.updateNode(request)
        return ResponseBuilder.success()
    }

    override fun replicaNodeCopyRequest(request: NodeMoveCopyRequest): Response<Void> {
        nodeService.copyNode(request)
        return ResponseBuilder.success()
    }

    override fun replicaNodeMoveRequest(request: NodeMoveCopyRequest): Response<Void> {
        nodeService.moveNode(request)
        return ResponseBuilder.success()
    }

    override fun replicaNodeDeleteRequest(request: NodeDeleteRequest): Response<NodeDeleteResult> {
        federatedCheck(
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

    private fun federatedCheck(
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
                throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, fullPath)
            }
        }
    }
}
