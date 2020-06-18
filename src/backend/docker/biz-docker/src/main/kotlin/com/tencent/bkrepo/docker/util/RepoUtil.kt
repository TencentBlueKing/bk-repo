package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.constant.REPO_TYPE
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.exception.DockerRepoNotFoundException
import org.slf4j.LoggerFactory

class RepoUtil constructor(repo: DockerArtifactRepo) {

    val repo = repo

    companion object {
        private val logger = LoggerFactory.getLogger(RepoUtil::class.java)
    }

    fun loadContext(context: RequestContext) {
        with(context) {
            repo.userId = userId
            isRepoExist(repo, projectId, repoName)
        }
    }

    private fun isRepoExist(repo: DockerArtifactRepo, projectId: String, repoName: String) {
        // check repository
        repo.repositoryResource.detail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.error("get repository detail exception [$projectId] , [$repoName] ")
            throw DockerRepoNotFoundException(repoName)
        }
    }
}
