package com.tencent.bkrepo.repository.service.repo

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.repository.pojo.repo.RepoListOption
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo

interface SoftwareRepositoryService {

    fun listRepoPage(
        projectId: String? = null,
        pageNumber: Int,
        pageSize: Int,
        option: RepoListOption
    ): Page<RepositoryInfo>

    fun listRepo(
        projectId: String? = null,
        option: RepoListOption,
        includeGeneric: Boolean
    ): List<RepositoryInfo>
}
