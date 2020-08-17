package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeUpdateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordInfo
import com.tencent.bkrepo.repository.service.NodeService
import com.tencent.bkrepo.repository.service.ShareService
import com.tencent.bkrepo.repository.service.query.NodeQueryService
import org.springframework.web.bind.annotation.RestController

/**
 * 节点服务接口实现类
 */
@RestController
class NodeController(
    private val nodeService: NodeService,
    private val nodeQueryService: NodeQueryService,
    private val shareService: ShareService
) : NodeClient {

    override fun detail(
        projectId: String,
        repoName: String,
        repoType: String,
        fullPath: String
    ): Response<NodeDetail?> {
        return ResponseBuilder.success(nodeService.detail(projectId, repoName, fullPath, repoType))
    }

    override fun detail(projectId: String, repoName: String, fullPath: String): Response<NodeDetail?> {
        return ResponseBuilder.success(nodeService.detail(projectId, repoName, fullPath))
    }

    override fun exist(projectId: String, repoName: String, fullPath: String): Response<Boolean> {
        return ResponseBuilder.success(nodeService.exist(projectId, repoName, fullPath))
    }

    override fun listExistFullPath(
        projectId: String,
        repoName: String,
        fullPathList: List<String>
    ): Response<List<String>> {
        return ResponseBuilder.success(nodeService.listExistFullPath(projectId, repoName, fullPathList))
    }

    override fun list(
        projectId: String,
        repoName: String,
        path: String,
        includeFolder: Boolean,
        deep: Boolean
    ): Response<List<NodeInfo>> {
        return ResponseBuilder.success(nodeService.list(projectId, repoName, path, includeFolder, false, deep))
    }

    override fun page(
        projectId: String,
        repoName: String,
        page: Int,
        size: Int,
        path: String,
        includeFolder: Boolean,
        includeMetadata: Boolean,
        deep: Boolean
    ): Response<Page<NodeInfo>> {
        return ResponseBuilder.success(nodeService.page(projectId, repoName, path, page, size, includeFolder, includeMetadata, deep))
    }

    override fun create(nodeCreateRequest: NodeCreateRequest): Response<NodeDetail> {
        return ResponseBuilder.success(nodeService.create(nodeCreateRequest))
    }

    override fun rename(nodeRenameRequest: NodeRenameRequest): Response<Void> {
        nodeService.rename(nodeRenameRequest)
        return ResponseBuilder.success()
    }

    override fun update(nodeUpdateRequest: NodeUpdateRequest): Response<Void> {
        nodeService.update(nodeUpdateRequest)
        return ResponseBuilder.success()
    }

    override fun move(nodeMoveRequest: NodeMoveRequest): Response<Void> {
        nodeService.move(nodeMoveRequest)
        return ResponseBuilder.success()
    }

    override fun copy(nodeCopyRequest: NodeCopyRequest): Response<Void> {
        nodeService.copy(nodeCopyRequest)
        return ResponseBuilder.success()
    }

    override fun delete(nodeDeleteRequest: NodeDeleteRequest): Response<Void> {
        nodeService.delete(nodeDeleteRequest)
        return ResponseBuilder.success()
    }

    override fun computeSize(projectId: String, repoName: String, fullPath: String): Response<NodeSizeInfo> {
        return ResponseBuilder.success(nodeService.computeSize(projectId, repoName, fullPath))
    }

    override fun countFileNode(projectId: String, repoName: String, path: String): Response<Long> {
        return ResponseBuilder.success(nodeService.countFileNode(projectId, repoName, path))
    }

    override fun listShareRecord(
        projectId: String,
        repoName: String,
        fullPath: String
    ): Response<List<ShareRecordInfo>> {
        return ResponseBuilder.success(shareService.list(projectId, repoName, fullPath))
    }

    override fun query(queryModel: QueryModel): Response<Page<Map<String, Any>>> {
        return ResponseBuilder.success(nodeQueryService.query(queryModel))
    }
}
