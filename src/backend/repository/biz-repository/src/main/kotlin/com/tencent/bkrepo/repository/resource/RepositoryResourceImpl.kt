package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.Repository
import org.springframework.web.bind.annotation.RestController

/**
 * 仓库服务接口实现类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@RestController
class RepositoryResourceImpl : RepositoryResource {
    override fun detail(id: String): Response<Repository> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun list(projectId: String): Response<List<Repository>> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun page(page: Long, size: Long, projectId: String): Response<Page<Repository>> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun create(repository: Repository): Response<Repository> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun update(id: String, repository: Repository): Response<Boolean> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun delete(id: String): Response<Boolean> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}
