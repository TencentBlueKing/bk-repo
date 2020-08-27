package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.model.TRepository
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.transaction.annotation.Transactional

/**
 * 仓库服务
 */
interface RepositoryService {
    fun detail(projectId: String, name: String, type: String? = null): RepositoryInfo?
    fun queryRepository(projectId: String, name: String, type: String? = null): TRepository?
    fun list(projectId: String): List<RepositoryInfo>
    fun page(projectId: String, page: Int, size: Int): Page<RepositoryInfo>
    fun exist(projectId: String, name: String, type: String? = null): Boolean

    /**
     * 创建仓库
     */
    fun create(repoCreateRequest: RepoCreateRequest): RepositoryInfo

    /**
     * 更新仓库
     */
    fun update(repoUpdateRequest: RepoUpdateRequest)

    /**
     * 删除仓库，需要保证文件已经被删除
     */
    @Transactional(rollbackFor = [Throwable::class])
    fun delete(repoDeleteRequest: RepoDeleteRequest)

    /**
     * 检查仓库是否存在，不存在则抛异常
     */
    fun checkRepository(projectId: String, repoName: String, repoType: String? = null): TRepository
}
