package com.tencent.bkrepo.dockerapi.service.bkrepo

import com.tencent.bkrepo.dockerapi.client.BkRepoClient
import com.tencent.bkrepo.dockerapi.constant.DEFAULT_DOCKER_REPO_NAME
import com.tencent.bkrepo.dockerapi.pojo.ImageAccount
import com.tencent.bkrepo.dockerapi.pojo.Repository
import com.tencent.bkrepo.dockerapi.service.RepoService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service


@Service
@ConditionalOnProperty(prefix = "dockerapi", name = ["realm"], havingValue = "bkrepo")
class BkRepoRepoServiceImpl(
    private val bkRepoClient: BkRepoClient
) : RepoService {
    override fun createRepo(projectId: String): Repository {
        val result = Repository(projectId, DEFAULT_DOCKER_REPO_NAME, "")
        if (bkRepoClient.repoExist(projectId)) return result
        if (!bkRepoClient.projectExist(projectId)) {
            bkRepoClient.createProject(projectId)
        }
        bkRepoClient.createRepo(projectId)
        return result
    }

    override fun createAccount(projectId: String): ImageAccount {
        return bkRepoClient.createProjectUser(projectId)
    }
}