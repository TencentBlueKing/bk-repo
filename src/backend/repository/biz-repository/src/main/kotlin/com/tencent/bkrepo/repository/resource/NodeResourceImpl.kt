package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeSearchRequest
import com.tencent.bkrepo.repository.service.NodeService
import com.tencent.bkrepo.repository.service.query.NodeQueryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import java.lang.RuntimeException

/**
 * 资源节点服务接口 实现类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@RestController
class NodeResourceImpl @Autowired constructor(
    private val nodeService: NodeService,
    private val nodeQueryService: NodeQueryService
) : NodeResource {

    override fun detail(projectId: String, repoName: String, repoType: String, fullPath: String): Response<NodeDetail?> {
        return Response.success(nodeService.detail(projectId, repoName, fullPath, repoType))
    }

    override fun detail(projectId: String, repoName: String, fullPath: String): Response<NodeDetail?> {
        return Response.success(nodeService.detail(projectId, repoName, fullPath))
    }

    override fun exist(projectId: String, repoName: String, fullPath: String): Response<Boolean> {
        return Response.success(nodeService.exist(projectId, repoName, fullPath))
    }

    override fun list(projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): Response<List<NodeInfo>> {
        return Response.success(nodeService.list(projectId, repoName, path, includeFolder, deep))
    }

    override fun page(projectId: String, repoName: String, page: Int, size: Int, path: String, includeFolder: Boolean, deep: Boolean): Response<Page<NodeInfo>> {
        return Response.success(nodeService.page(projectId, repoName, path, page, size, includeFolder, deep))
    }

    override fun search(nodeSearchRequest: NodeSearchRequest): Response<Page<NodeInfo>> {
        return Response.success(nodeService.search(nodeSearchRequest))
    }

    override fun create(nodeCreateRequest: NodeCreateRequest): Response<IdValue> {
        return Response.success(nodeService.create(nodeCreateRequest))
    }

    override fun rename(nodeRenameRequest: NodeRenameRequest): Response<Void> {
        nodeService.rename(nodeRenameRequest)
        return Response.success()
    }

    override fun move(nodeMoveRequest: NodeMoveRequest): Response<Void> {
        nodeService.move(nodeMoveRequest)
        return Response.success()
    }

    override fun copy(nodeCopyRequest: NodeCopyRequest): Response<Void> {
        nodeService.copy(nodeCopyRequest)
        return Response.success()
    }

    override fun delete(nodeDeleteRequest: NodeDeleteRequest): Response<Void> {
        nodeService.delete(nodeDeleteRequest)
        return Response.success()
    }

    override fun computeSize(projectId: String, repoName: String, fullPath: String): Response<NodeSizeInfo> {
        return Response.success(nodeService.computeSize(projectId, repoName, fullPath))
    }

    override fun query(queryModel: QueryModel): Response<Page<Map<String, Any>>> {
        return Response.success(nodeQueryService.query(queryModel))
    }

    override fun error(): Response<String> {
        throw ErrorCodeException(CommonMessageCode.SYSTEM_ERROR)
    }

    override fun error1(): Response<String> {
        throw RuntimeException("")
    }

    override fun success(): Response<String> {
        return Response.success("success")
    }
}
