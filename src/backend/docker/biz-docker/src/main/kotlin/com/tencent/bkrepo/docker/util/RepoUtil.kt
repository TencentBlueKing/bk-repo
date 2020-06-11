package com.tencent.bkrepo.docker.util

import com.tencent.bkrepo.docker.artifact.DockerArtifactService
import com.tencent.bkrepo.docker.constant.REPO_TYPE
import com.tencent.bkrepo.docker.exception.DockerRepoNotFoundException
import org.slf4j.LoggerFactory

class RepoUtil {

    companion object {

        private val logger = LoggerFactory.getLogger(RepoUtil::class.java)

        fun loadRepo(repo: DockerArtifactService, userId: String, projectId: String, repoName: String) {
            repo.userId = userId
            isRepoExist(repo, projectId, repoName)
        }

        private fun isRepoExist(repo: DockerArtifactService, projectId: String, repoName: String) {
            // check repository
            repo.repositoryResource.detail(projectId, repoName, REPO_TYPE).data ?: run {
                logger.error("get repository detail exception [$projectId] , [$repoName] ")
                throw DockerRepoNotFoundException(repoName)
            }
        }
    }
}
