package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.project.RepoRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.service.RepositoryService
import org.springframework.web.bind.annotation.RestController

/**
 * 仓库服务接口实现类
 */
@RestController
class RepositoryController(
    private val repositoryService: RepositoryService
) : RepositoryClient {
    override fun getRepoInfo(projectId: String, repoName: String): Response<RepositoryInfo?> {
        return ResponseBuilder.success(repositoryService.getRepoInfo(projectId, repoName, null))
    }

    override fun getRepoDetail(projectId: String, repoName: String): Response<RepositoryDetail?> {
        return ResponseBuilder.success(repositoryService.getRepoDetail(projectId, repoName, null))
    }

    override fun getRepoDetail(projectId: String, repoName: String, type: String?): Response<RepositoryDetail?> {
        return ResponseBuilder.success(repositoryService.getRepoDetail(projectId, repoName, type))
    }

    override fun list(projectId: String): Response<List<RepositoryInfo>> {
        return ResponseBuilder.success(repositoryService.list(projectId))
    }

    override fun page(pageNumber: Int, pageSize: Int, projectId: String): Response<Page<RepositoryInfo>> {
        return ResponseBuilder.success(repositoryService.page(projectId, pageNumber, pageSize))
    }

    override fun rangeQuery(request: RepoRangeQueryRequest): Response<Page<RepositoryInfo?>> {
        return ResponseBuilder.success(repositoryService.rangeQuery(request))
    }

    override fun create(repoCreateRequest: RepoCreateRequest): Response<RepositoryDetail> {
        return ResponseBuilder.success(repositoryService.create(repoCreateRequest))
    }

    override fun update(repoUpdateRequest: RepoUpdateRequest): Response<Void> {
        repositoryService.update(repoUpdateRequest)
        return ResponseBuilder.success()
    }

    override fun delete(repoDeleteRequest: RepoDeleteRequest): Response<Void> {
        repositoryService.delete(repoDeleteRequest)
        return ResponseBuilder.success()
    }
}
