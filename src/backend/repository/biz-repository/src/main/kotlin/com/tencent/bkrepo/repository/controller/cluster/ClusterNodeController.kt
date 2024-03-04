/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.controller.cluster

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.cluster.ClusterNodeClient
import com.tencent.bkrepo.common.metadata.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.common.metadata.pojo.node.NodeDetail
import com.tencent.bkrepo.common.metadata.pojo.node.NodeRestoreResult
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeRestoreRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeUpdateAccessDateRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodesDeleteRequest
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import org.springframework.web.bind.annotation.RestController

@RestController
class ClusterNodeController(
    private val permissionManager: PermissionManager,
    private val nodeService: NodeService
) : ClusterNodeClient {

    override fun getNodeDetail(projectId: String, repoName: String, fullPath: String): Response<NodeDetail?> {
        permissionManager.checkNodePermission(PermissionAction.READ, projectId, repoName, fullPath)
        val artifactInfo = ArtifactInfo(projectId, repoName, fullPath)
        return ResponseBuilder.success(nodeService.getNodeDetail(artifactInfo))
    }

    override fun createNode(nodeCreateRequest: NodeCreateRequest): Response<NodeDetail> {
        with(nodeCreateRequest) {
            permissionManager.checkRepoPermission(PermissionAction.WRITE, projectId, repoName)
            return ResponseBuilder.success(nodeService.createNode(this))
        }
    }

    override fun updateNode(nodeUpdateRequest: NodeUpdateRequest): Response<Void> {
        with(nodeUpdateRequest) {
            permissionManager.checkNodePermission(PermissionAction.UPDATE, projectId, repoName, fullPath)
            nodeService.updateNode(this)
            return ResponseBuilder.success()
        }
    }

    override fun updateNodeAccessDate(nodeUpdateAccessDateRequest: NodeUpdateAccessDateRequest): Response<Void> {
        with(nodeUpdateAccessDateRequest) {
            permissionManager.checkNodePermission(PermissionAction.READ, projectId, repoName, fullPath)
            nodeService.updateNodeAccessDate(this)
            return ResponseBuilder.success()
        }
    }

    override fun renameNode(nodeRenameRequest: NodeRenameRequest): Response<Void> {
        with(nodeRenameRequest) {
            permissionManager.checkNodePermission(PermissionAction.UPDATE, projectId, repoName, fullPath)
            permissionManager.checkNodePermission(PermissionAction.UPDATE, projectId, repoName, newFullPath)
            nodeService.renameNode(this)
            return ResponseBuilder.success()
        }
    }

    override fun moveNode(nodeMoveRequest: NodeMoveCopyRequest): Response<Void> {
        checkCrossRepoPermission(nodeMoveRequest)
        nodeService.moveNode(nodeMoveRequest)
        return ResponseBuilder.success()
    }

    override fun copyNode(nodeCopyRequest: NodeMoveCopyRequest): Response<Void> {
        checkCrossRepoPermission(nodeCopyRequest)
        nodeService.copyNode(nodeCopyRequest)
        return ResponseBuilder.success()
    }

    override fun deleteNode(nodeDeleteRequest: NodeDeleteRequest): Response<Void> {
        with(nodeDeleteRequest) {
            permissionManager.checkNodePermission(PermissionAction.DELETE, projectId, repoName, fullPath)
            nodeService.deleteNode(this)
            return ResponseBuilder.success()
        }

    }

    override fun deleteNodes(nodesDeleteRequest: NodesDeleteRequest): Response<NodeDeleteResult> {
        with(nodesDeleteRequest) {
            permissionManager.checkRepoPermission(PermissionAction.DELETE, projectId, repoName)
            return ResponseBuilder.success(nodeService.deleteNodes(this))
        }
    }

    override fun restoreNode(nodeRestoreRequest: NodeRestoreRequest): Response<NodeRestoreResult> {
        with(nodeRestoreRequest) {
            permissionManager.checkNodePermission(
                PermissionAction.WRITE,
                artifactInfo.projectId,
                artifactInfo.repoName,
                artifactInfo.getArtifactFullPath()
            )
            return ResponseBuilder.success(nodeService.restoreNode(artifactInfo, nodeRestoreOption))
        }
    }

    private fun checkCrossRepoPermission(request: NodeMoveCopyRequest) {
        with(request) {
            permissionManager.checkNodePermission(
                PermissionAction.WRITE,
                srcProjectId,
                srcRepoName,
                PathUtils.normalizeFullPath(srcFullPath)
            )
            val toProjectId = request.destProjectId ?: srcProjectId
            val toRepoName = request.destRepoName ?: srcRepoName
            permissionManager.checkNodePermission(
                PermissionAction.WRITE,
                toProjectId,
                toRepoName,
                PathUtils.normalizeFullPath(destFullPath)
            )
        }
    }
}
