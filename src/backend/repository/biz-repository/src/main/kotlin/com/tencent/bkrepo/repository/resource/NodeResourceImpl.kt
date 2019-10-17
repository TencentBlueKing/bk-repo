package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.NodeCopyRequest
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeSearchRequest
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeUpdateRequest
import com.tencent.bkrepo.repository.service.NodeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 资源节点服务接口 实现类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@RestController
class NodeResourceImpl @Autowired constructor(
    private val nodeService: NodeService
) : NodeResource {
    override fun queryDetail(projectId: String, repoName: String, repoType: String, fullPath: String): Response<NodeDetail?> {
        return Response.success(nodeService.queryDetail(projectId, repoName, fullPath, repoType))
    }

    override fun queryDetail(projectId: String, repoName: String, fullPath: String): Response<NodeDetail?> {
        return Response.success(nodeService.queryDetail(projectId, repoName, fullPath))
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

    override fun search(nodeSearchRequest: NodeSearchRequest): Response<List<NodeInfo>> {
        return Response.success(nodeService.search(nodeSearchRequest))
    }

    override fun create(nodeCreateRequest: NodeCreateRequest): Response<IdValue> {
        return Response.success(nodeService.create(nodeCreateRequest))
    }

    override fun update(nodeUpdateRequest: NodeUpdateRequest): Response<Void> {
        nodeService.update(nodeUpdateRequest)
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

    override fun getSize(projectId: String, repoName: String, fullPath: String): Response<NodeSizeInfo> {
        return Response.success(nodeService.getSize(projectId, repoName, fullPath))
    }
}
