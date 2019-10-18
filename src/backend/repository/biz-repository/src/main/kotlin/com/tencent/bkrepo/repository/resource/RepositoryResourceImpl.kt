package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.common.api.pojo.IdValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.Repository
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
    override fun queryDetail(projectId: String, name: String): Response<Repository?> {
        return Response.success(repositoryService.queryDetail(projectId, name))
    }

    override fun queryDetail(projectId: String, name: String, type: String): Response<Repository?> {
        return Response.success(repositoryService.queryDetail(projectId, name, type))
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

    override fun update(repoUpdateRequest: RepoUpdateRequest): Response<Void> {
        repositoryService.update(repoUpdateRequest)
        return Response.success()
    }

    override fun delete(projectId: String, name: String): Response<Void> {
        repositoryService.delete(projectId, name)
        return Response.success()
    }
}
