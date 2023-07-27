package com.tencent.bkrepo.repository.service.repo

/**
 * 仓库清理接口
 */
interface RepositoryCleanService {
    /**
     * 清理仓库
     * @param repoId 仓库id
     */
    fun cleanRepo(repoId: String)

    fun cleanRepoDebug(projectId: String, repoName: String)
}
