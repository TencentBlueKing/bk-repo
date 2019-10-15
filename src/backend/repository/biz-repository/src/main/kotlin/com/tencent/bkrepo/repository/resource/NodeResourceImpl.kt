package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
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

    override fun queryDetail(repositoryId: String, fullPath: String): Response<NodeDetail?> {
        return Response.success(nodeService.queryNodeDetail(repositoryId, fullPath))
    }

    override fun exist(repositoryId: String, fullPath: String): Response<Boolean> {
        return Response.success(nodeService.exist(repositoryId, fullPath))
    }

    override fun list(repositoryId: String, path: String, includeFolder: Boolean, deep: Boolean): Response<List<NodeInfo>> {
        return Response.success(nodeService.list(repositoryId, path, includeFolder, deep))
    }

    override fun page(page: Int, size: Int, repositoryId: String, path: String, includeFolder: Boolean, deep: Boolean): Response<Page<NodeInfo>> {
        return Response.success(nodeService.page(repositoryId, path, page, size, includeFolder, deep))
    }

    override fun search(repositoryId: String, nodeSearchRequest: NodeSearchRequest): Response<List<NodeInfo>> {
        return Response.success(nodeService.search(repositoryId, nodeSearchRequest))
    }

    override fun create(nodeCreateRequest: NodeCreateRequest): Response<IdValue> {
        return Response.success(nodeService.create(nodeCreateRequest))
    }

    override fun update(id: String, nodeUpdateRequest: NodeUpdateRequest): Response<Void> {
        nodeService.updateById(id, nodeUpdateRequest)
        return Response.success()
    }

    override fun delete(id: String, modifiedBy: String): Response<Void> {
        nodeService.deleteById(id, modifiedBy)
        return Response.success()
    }

    override fun getNodeSize(repositoryId: String, fullPath: String): Response<NodeSizeInfo> {
        return Response.success(nodeService.getNodeSize(repositoryId, fullPath))
    }
}
