package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

/**
 * 仓库服务接口
 */
interface RepositoryService {
    /**
     * 查询仓库详情，不存在返回null
     *
     * @param projectId 项目id
     * @param name 仓库名称
     * @param type 仓库类型
     */
    fun getRepoDetail(projectId: String, name: String, type: String? = null): RepositoryDetail?

    /**
     * 查询仓库基本信息，不存在返回null
     *
     * @param projectId 项目id
     * @param name 仓库名称
     * @param type 仓库类型
     */
    fun getRepoInfo(projectId: String, name: String, type: String? = null): RepositoryInfo?

    /**
     * 查询项目[projectId]下的所有仓库
     */
    fun list(projectId: String, name: String? = null, type: String? = null): List<RepositoryInfo>

    /**
     * 分页查询仓库列表
     *
     * @param projectId 项目id
     * @param pageNumber 当前页
     * @param pageSize 分页数量
     * @param name 仓库名称
     * @param type 仓库类型
     */
    fun page(projectId: String, pageNumber: Int, pageSize: Int, name: String? = null, type: String? = null): Page<RepositoryInfo>

    /**
     * 判断仓库是否存在
     *
     * @param projectId 项目id
     * @param name 仓库名称
     * @param type 仓库类型
     */
    fun exist(projectId: String, name: String, type: String? = null): Boolean

    /**
     * 根据请求[repoCreateRequest]创建仓库
     */
    fun create(repoCreateRequest: RepoCreateRequest): RepositoryDetail

    /**
     * 根据请求[repoUpdateRequest]更新仓库
     */
    fun update(repoUpdateRequest: RepoUpdateRequest)

    /**
     * 更新storageCredentialsKey
     */
    fun updateStorageCredentialsKey(projectId: String, repoName: String, storageCredentialsKey: String)

    /**
     * 根据请求[repoDeleteRequest]删除仓库
     *
     * 删除仓库前，需要保证仓库下的文件已经被删除
     */
    fun delete(repoDeleteRequest: RepoDeleteRequest)

    /**
     * 检查仓库是否存在，不存在则抛异常
     */
    fun checkRepository(projectId: String, repoName: String, repoType: String? = null): TRepository
}
