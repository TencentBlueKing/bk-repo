/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.replication.controller

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.replication.api.ClusterReplicaClient
import com.tencent.bkrepo.replication.config.DEFAULT_VERSION
import com.tencent.bkrepo.replication.pojo.request.NodeExistCheckRequest
import com.tencent.bkrepo.replication.pojo.request.NodeReplicaRequest
import com.tencent.bkrepo.replication.pojo.request.PackageVersionExistCheckRequest
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.ProjectClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
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
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 集群间数据同步接口
 */
@Principal(type = PrincipalType.ADMIN)
@RestController
class ClusterReplicaController(
    private val projectClient: ProjectClient,
    private val repositoryClient: RepositoryClient,
    private val nodeClient: NodeClient,
    private val packageClient: PackageClient,
    private val metadataClient: MetadataClient,
    private val storageService: StorageService
) : ClusterReplicaClient {

    @Value("\${spring.application.version}")
    private var version: String = DEFAULT_VERSION

    override fun ping() = ResponseBuilder.success()

    override fun version() = ResponseBuilder.success(version)

    override fun checkNodeExist(
        projectId: String,
        repoName: String,
        fullPath: String
    ): Response<Boolean> {
        return nodeClient.checkExist(projectId, repoName, fullPath)
    }

    override fun checkNodeExistList(
        request: NodeExistCheckRequest
    ): Response<List<String>> {
        return nodeClient.listExistFullPath(
            request.projectId,
            request.repoName,
            request.fullPathList
        )
    }

    @PostMapping(FILE_MAPPING_URI, consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun replicaFile(
        @ArtifactPathVariable
        artifactInfo: DefaultArtifactInfo,
        fileMap: ArtifactFileMap,
        nodeReplicaRequest: NodeReplicaRequest
    ): Response<NodeDetail> {
        with(nodeReplicaRequest) {
            val file = fileMap["file"]!!
            // 校验
            if (file.getSize() != size) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "size")
            }
            if (sha256.isBlank()) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "sha256")
            }
            if (md5.isBlank()) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "md5")
            }
            // 保存文件
            val projectId = artifactInfo.projectId
            val repoName = artifactInfo.repoName
            val fullPath = artifactInfo.getArtifactFullPath()
            val repoInfo = repositoryClient.getRepoDetail(projectId, repoName).data!!
            storageService.store(sha256, file, repoInfo.storageCredentials)
            // 保存节点
            val request = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                folder = false,
                expires = expires,
                overwrite = true,
                size = size,
                sha256 = sha256,
                md5 = md5,
                metadata = metadata,
                operator = userId
            )
            return nodeClient.createNode(request)
        }
    }

    override fun replicaNodeCreateRequest(request: NodeCreateRequest): Response<NodeDetail> {
        return nodeClient.createNode(request)
    }

    override fun replicaNodeRenameRequest(request: NodeRenameRequest): Response<Void> {
        return nodeClient.renameNode(request)
    }

    override fun replicaNodeUpdateRequest(request: NodeUpdateRequest): Response<Void> {
        return nodeClient.updateNode(request)
    }

    override fun replicaNodeCopyRequest(request: NodeMoveCopyRequest): Response<Void> {
        return nodeClient.copyNode(request)
    }

    override fun replicaNodeMoveRequest(request: NodeMoveCopyRequest): Response<Void> {
        return nodeClient.moveNode(request)
    }

    override fun replicaNodeDeleteRequest(request: NodeDeleteRequest): Response<Void> {
        return nodeClient.deleteNode(request)
    }

    override fun replicaRepoCreateRequest(request: RepoCreateRequest): Response<RepositoryDetail> {
        return repositoryClient.getRepoDetail(request.projectId, request.name).data?.let { ResponseBuilder.success(it) }
            ?: repositoryClient.createRepo(request)
    }

    override fun replicaRepoUpdateRequest(request: RepoUpdateRequest): Response<Void> {
        return repositoryClient.updateRepo(request)
    }

    override fun replicaRepoDeleteRequest(request: RepoDeleteRequest): Response<Void> {
        return repositoryClient.deleteRepo(request)
    }

    override fun replicaProjectCreateRequest(request: ProjectCreateRequest): Response<ProjectInfo> {
        return projectClient.getProjectInfo(request.name).data?.let { ResponseBuilder.success(it) }
            ?: projectClient.createProject(request)
    }

    override fun replicaMetadataSaveRequest(request: MetadataSaveRequest): Response<Void> {
        return metadataClient.saveMetadata(request)
    }

    override fun replicaMetadataDeleteRequest(request: MetadataDeleteRequest): Response<Void> {
        return metadataClient.deleteMetadata(request)
    }

    override fun checkPackageVersionExistList(
        request: PackageVersionExistCheckRequest
    ): Response<List<String>> {
        return packageClient.listExistPackageVersion(
            request.projectId,
            request.repoName,
            request.packageKey,
            request.packageVersionList
        )
    }

    override fun replicaPackageVersionCreatedRequest(
        request: PackageVersionCreateRequest
    ): Response<Void> {
        return packageClient.createVersion(request)
    }

    companion object {
        private const val FILE_MAPPING_URI = "/file/{projectId}/{repoName}/**"
    }
}
