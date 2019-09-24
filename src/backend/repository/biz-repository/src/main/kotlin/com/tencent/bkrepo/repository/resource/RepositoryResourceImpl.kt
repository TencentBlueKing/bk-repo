package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.Repository
import com.tencent.bkrepo.repository.service.RepositoryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 仓库服务接口实现类
 *
 * @author: carrypan
 * @date: 2019-09-10
 */
@RestController
class RepositoryResourceImpl @Autowired constructor(
    private val repositoryService: RepositoryService
) : RepositoryResource {

    override fun detail(id: String): Response<Repository> {
        return Response.success(repositoryService.getDetailById(id))
    }

    override fun list(projectId: String): Response<List<Repository>> {
        return Response.success(repositoryService.list(projectId))
    }

    override fun page(page: Int, size: Int, projectId: String): Response<Page<Repository>> {
        return Response.success(repositoryService.page(projectId, page, size))
    }

    override fun create(repoCreateRequest: RepoCreateRequest): Response<IdValue> {
        return Response.success(repositoryService.create(repoCreateRequest))
    }

    override fun update(id: String, repoUpdateRequest: RepoUpdateRequest): Response<Void> {
        repositoryService.updateById(id, repoUpdateRequest)
        return Response.success()
    }

    override fun delete(id: String): Response<Void> {
        repositoryService.deleteById(id)
        return Response.success()
    }
}
