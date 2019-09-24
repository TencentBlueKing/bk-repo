package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.Node
import com.tencent.bkrepo.repository.pojo.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.NodeUpdateRequest
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
    override fun detail(id: String): Response<Node> {
        return Response.success(nodeService.getDetailById(id))
    }

    override fun list(repositoryId: String, path: String): Response<List<Node>> {
        return Response.success(nodeService.list(repositoryId, path))
    }

    override fun page(page: Int, size: Int, repositoryId: String, path: String): Response<Page<Node>> {
        return Response.success(nodeService.page(repositoryId, path, page, size))
    }

    override fun create(nodeCreateRequest: NodeCreateRequest): Response<IdValue> {
        return Response.success(nodeService.create(nodeCreateRequest))
    }

    override fun update(id: String, nodeUpdateRequest: NodeUpdateRequest): Response<Void> {
        nodeService.updateById(id, nodeUpdateRequest)
        return Response.success()
    }

    override fun deleteById(id: String, modifiedBy: String): Response<Void> {
        nodeService.softDeleteById(id, modifiedBy)
        return Response.success()
    }
}
