package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.Node
import org.springframework.web.bind.annotation.RestController

/**
 * 资源节点服务接口 实现类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@RestController
class NodeResourceImpl : NodeResource {
    override fun detail(id: String): Response<Node> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun list(repositoryId: String, path: String): Response<Page<Node>> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun page(page: Long, size: Long, repositoryId: String, path: String): Response<Page<Node>> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun create(repository: Node): Response<Node> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun update(id: String, repository: Node): Response<Boolean> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteById(id: String): Response<Boolean> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteByPath(path: String): Response<Boolean> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}
