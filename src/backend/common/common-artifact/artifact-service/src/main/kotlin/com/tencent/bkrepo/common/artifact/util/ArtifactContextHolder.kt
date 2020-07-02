package com.tencent.bkrepo.common.artifact.util

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.config.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.config.PROJECT_ID
import com.tencent.bkrepo.common.artifact.config.REPO_KEY
import com.tencent.bkrepo.common.artifact.config.REPO_NAME
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

@Component
class ArtifactContextHolder(
    artifactConfiguration: ArtifactConfiguration,
    repositoryResource: RepositoryResource
) {

    init {
        repositoryType = artifactConfiguration.getRepositoryType()
        Companion.repositoryResource = repositoryResource
    }

    companion object {
        private lateinit var repositoryType: RepositoryType
        private lateinit var repositoryResource: RepositoryResource

        fun getRepositoryInfo(): RepositoryInfo? {
            val request = HttpContextHolder.getRequestOrNull() ?: return null
            val repoInfoAttribute = request.getAttribute(REPO_KEY)
            return if (repoInfoAttribute == null) {
                val artifactInfo =
                    getArtifactInfo(
                        request
                    )
                val repositoryInfo =
                    queryRepositoryInfo(
                        artifactInfo
                    )
                request.setAttribute(REPO_KEY, repositoryInfo)
                repositoryInfo
            } else {
                repoInfoAttribute as RepositoryInfo
            }
        }

        private fun getArtifactInfo(request: HttpServletRequest): ArtifactInfo {
            val artifactInfoAttribute = request.getAttribute(ARTIFACT_INFO_KEY)
            return if (artifactInfoAttribute == null) {
                val attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
                val projectId = attributes[PROJECT_ID].toString()
                val repoName = attributes[REPO_NAME].toString()
                DefaultArtifactInfo(projectId, repoName, StringPool.EMPTY)
            } else {
                artifactInfoAttribute as ArtifactInfo
            }
        }

        private fun queryRepositoryInfo(artifactInfo: ArtifactInfo): RepositoryInfo {
            with(artifactInfo) {
                val response = if (repositoryType == RepositoryType.NONE) {
                    repositoryResource.detail(projectId, repoName)
                } else {
                    repositoryResource.detail(projectId, repoName, repositoryType.name)
                }
                return response.data ?: throw ArtifactNotFoundException("Repository[$repoName] not found")
            }
        }
    }
}
