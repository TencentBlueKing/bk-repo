package com.tencent.bkrepo.opdata.model

import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RepoModel @Autowired constructor(
    private val repositoryResource: RepositoryResource
) {

    fun getRepoListByProjectId(projectId: String): List<RepositoryInfo> {
        val result = repositoryResource.list(projectId).data ?: return emptyList()
        return result
    }
}
