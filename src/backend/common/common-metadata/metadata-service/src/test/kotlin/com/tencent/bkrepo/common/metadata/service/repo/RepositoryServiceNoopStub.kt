package com.tencent.bkrepo.common.metadata.service.repo

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.project.RepoRangeQueryRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoListOption
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

/** 集成测试占位：仅满足 bean 装配，降冷日期查询不会调用。 */
object RepositoryServiceNoopStub : RepositoryService {

    private fun emptyPageInfo() = Page(1, 20, 0, emptyList<RepositoryInfo>())
    private fun emptyPageDetail() = Page(1, 20, 0, emptyList<RepositoryDetail>())

    override fun getRepoInfo(projectId: String, name: String, type: String?) = null
    override fun getRepoDetail(projectId: String, name: String, type: String?) = null
    override fun listRepo(projectId: String, name: String?, type: String?, display: Boolean?) =
        emptyList<RepositoryInfo>()
    override fun listRepoPage(projectId: String, pageNumber: Int, pageSize: Int, name: String?, type: String?) =
        emptyPageInfo()

    override fun listPermissionRepo(userId: String, projectId: String, option: RepoListOption) =
        emptyList<RepositoryInfo>()
    override fun listPermissionRepoPage(
        userId: String,
        projectId: String,
        pageNumber: Int,
        pageSize: Int,
        option: RepoListOption,
    ) = emptyPageInfo()

    override fun listRepoPageByType(type: String, pageNumber: Int, pageSize: Int) = emptyPageDetail()
    override fun rangeQuery(request: RepoRangeQueryRequest) = Page(1, 20, 0, emptyList<RepositoryInfo?>())
    override fun checkExist(projectId: String, name: String, type: String?) = false
    override fun createRepo(repoCreateRequest: RepoCreateRequest) =
        throw UnsupportedOperationException("RepositoryServiceNoopStub")

    override fun updateRepo(repoUpdateRequest: RepoUpdateRequest) {}
    override fun updateStorageCredentialsKey(projectId: String, repoName: String, storageCredentialsKey: String?) {}
    override fun unsetOldStorageCredentialsKey(projectId: String, repoName: String) {}
    override fun deleteRepo(repoDeleteRequest: RepoDeleteRequest) {}
    override fun statRepo(projectId: String, repoName: String) = NodeSizeInfo(size = 0L)
}
