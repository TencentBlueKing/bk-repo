/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.controller.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.NodeRestoreResult
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeArchiveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCleanRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCompressedRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeLinkRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRestoreRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUnCompressedRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateAccessDateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodesDeleteRequest
import com.tencent.bkrepo.repository.service.node.NodeSearchService
import com.tencent.bkrepo.repository.service.node.NodeService
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.RestController

/**
 * 节点服务接口实现类
 */
@Primary
@RestController
class NodeController(
    private val nodeService: NodeService,
    private val nodeSearchService: NodeSearchService,
) : NodeClient {

    override fun getNodeDetail(projectId: String, repoName: String, fullPath: String): Response<NodeDetail?> {
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, fullPath)
        return ResponseBuilder.success(nodeService.getNodeDetail(artifactInfo))
    }

    override fun checkExist(projectId: String, repoName: String, fullPath: String): Response<Boolean> {
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, fullPath)
        return ResponseBuilder.success(nodeService.checkExist(artifactInfo))
    }

    override fun listExistFullPath(
        projectId: String,
        repoName: String,
        fullPathList: List<String>,
    ): Response<List<String>> {
        return ResponseBuilder.success(nodeService.listExistFullPath(projectId, repoName, fullPathList))
    }

    override fun listNodePage(
        projectId: String,
        repoName: String,
        path: String,
        option: NodeListOption,
    ): Response<Page<NodeInfo>> {
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, path)
        return ResponseBuilder.success(nodeService.listNodePage(artifactInfo, option))
    }

    override fun createNode(nodeCreateRequest: NodeCreateRequest): Response<NodeDetail> {
        return ResponseBuilder.success(nodeService.createNode(nodeCreateRequest))
    }

    override fun updateNode(nodeUpdateRequest: NodeUpdateRequest): Response<Void> {
        nodeService.updateNode(nodeUpdateRequest)
        return ResponseBuilder.success()
    }

    override fun updateNodeAccessDate(nodeUpdateAccessDateRequest: NodeUpdateAccessDateRequest): Response<Void> {
        nodeService.updateNodeAccessDate(nodeUpdateAccessDateRequest)
        return ResponseBuilder.success()
    }

    override fun renameNode(nodeRenameRequest: NodeRenameRequest): Response<Void> {
        nodeService.renameNode(nodeRenameRequest)
        return ResponseBuilder.success()
    }

    override fun moveNode(nodeMoveRequest: NodeMoveCopyRequest): Response<NodeDetail> {
        return ResponseBuilder.success(nodeService.moveNode(nodeMoveRequest))
    }

    override fun copyNode(nodeCopyRequest: NodeMoveCopyRequest): Response<NodeDetail> {
        return ResponseBuilder.success(nodeService.copyNode(nodeCopyRequest))
    }

    override fun deleteNode(nodeDeleteRequest: NodeDeleteRequest): Response<NodeDeleteResult> {
        return ResponseBuilder.success(nodeService.deleteNode(nodeDeleteRequest))
    }

    override fun deleteNodes(nodesDeleteRequest: NodesDeleteRequest): Response<NodeDeleteResult> {
        return ResponseBuilder.success(nodeService.deleteNodes(nodesDeleteRequest))
    }

    override fun restoreNode(nodeRestoreRequest: NodeRestoreRequest): Response<NodeRestoreResult> {
        return ResponseBuilder.success(
            nodeService.restoreNode(nodeRestoreRequest.artifactInfo, nodeRestoreRequest.nodeRestoreOption),
        )
    }

    override fun computeSize(
        projectId: String,
        repoName: String,
        fullPath: String,
        estimated: Boolean,
    ): Response<NodeSizeInfo> {
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, fullPath)
        return ResponseBuilder.success(nodeService.computeSize(artifactInfo, estimated))
    }

    override fun countFileNode(projectId: String, repoName: String, path: String): Response<Long> {
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, path)
        return ResponseBuilder.success(nodeService.countFileNode(artifactInfo))
    }

    override fun search(queryModel: QueryModel): Response<Page<Map<String, Any?>>> {
        return ResponseBuilder.success(nodeSearchService.search(queryModel))
    }

    override fun queryWithoutCount(queryModel: QueryModel): Response<Page<Map<String, Any?>>> {
        return ResponseBuilder.success(nodeSearchService.searchWithoutCount(queryModel))
    }

    override fun listNode(
        projectId: String,
        repoName: String,
        path: String,
        includeFolder: Boolean,
        deep: Boolean,
        includeMetadata: Boolean,
    ): Response<List<NodeInfo>> {
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, path)
        val nodeListOption = NodeListOption(
            includeFolder = includeFolder,
            includeMetadata = includeMetadata,
            deep = deep,
        )
        return ResponseBuilder.success(nodeService.listNode(artifactInfo, nodeListOption))
    }

    override fun getDeletedNodeDetail(
        projectId: String,
        repoName: String,
        fullPath: String,
    ): Response<List<NodeDetail>> {
        val artifactInfo = DefaultArtifactInfo(projectId, repoName, fullPath)
        return ResponseBuilder.success(nodeService.getDeletedNodeDetail(artifactInfo))
    }

    override fun getDeletedNodeDetailBySha256(
        projectId: String,
        repoName: String,
        sha256: String,
    ): Response<NodeDetail?> {
        return ResponseBuilder.success(nodeService.getDeletedNodeDetailBySha256(projectId, repoName, sha256))
    }

    override fun archiveNode(nodeArchiveRequest: NodeArchiveRequest): Response<Void> {
        nodeService.archiveNode(nodeArchiveRequest)
        return ResponseBuilder.success()
    }

    override fun cleanNodes(nodeCleanRequest: NodeCleanRequest): Response<NodeDeleteResult> {
        return ResponseBuilder.success(
            nodeService.deleteBeforeDate(
                projectId = nodeCleanRequest.projectId,
                repoName = nodeCleanRequest.repoName,
                path = nodeCleanRequest.path,
                date = nodeCleanRequest.date,
                operator = nodeCleanRequest.operator,
            ),
        )
    }

    override fun restoreNode(nodeArchiveRequest: NodeArchiveRequest): Response<Void> {
        nodeService.restoreNode(nodeArchiveRequest)
        return ResponseBuilder.success()
    }

    override fun compressedNode(nodeCompressedRequest: NodeCompressedRequest): Response<Void> {
        nodeService.compressedNode(nodeCompressedRequest)
        return ResponseBuilder.success()
    }

    override fun uncompressedNode(nodeUnCompressedRequest: NodeUnCompressedRequest): Response<Void> {
        nodeService.uncompressedNode(nodeUnCompressedRequest)
        return ResponseBuilder.success()
    }

    override fun link(nodeLinkRequest: NodeLinkRequest): Response<NodeDetail> {
        return ResponseBuilder.success(nodeService.link(nodeLinkRequest))
    }
}
