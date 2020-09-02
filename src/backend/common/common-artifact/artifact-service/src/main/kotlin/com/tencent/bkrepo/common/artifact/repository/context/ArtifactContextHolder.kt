package com.tencent.bkrepo.common.artifact.repository.context

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.constant.PROJECT_ID
import com.tencent.bkrepo.common.artifact.constant.REPO_KEY
import com.tencent.bkrepo.common.artifact.constant.REPO_NAME
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

@Component
class ArtifactContextHolder(
    artifactConfiguration: ArtifactConfiguration,
    repositoryClient: RepositoryClient
) {

    init {
        Companion.artifactConfiguration = artifactConfiguration
        Companion.repositoryClient = repositoryClient
    }

    companion object {
        private lateinit var artifactConfiguration: ArtifactConfiguration
        private lateinit var repositoryClient: RepositoryClient

        inline fun <reified T> String.readJsonString(): T = JsonUtils.objectMapper.readValue(this, jacksonTypeRef<T>())

        fun <reified T> getRepository(repositoryCategory: RepositoryCategory? = null): ArtifactRepository {
            return when (repositoryCategory?: getRepoDetail()!!.category) {
                RepositoryCategory.LOCAL -> artifactConfiguration.getLocalRepository()
                RepositoryCategory.REMOTE -> artifactConfiguration.getRemoteRepository()
                RepositoryCategory.VIRTUAL -> artifactConfiguration.getVirtualRepository()
                RepositoryCategory.COMPOSITE -> artifactConfiguration.getCompositeRepository()
            }
        }

        fun getRepoDetail(): RepositoryDetail? {
            val request = HttpContextHolder.getRequestOrNull() ?: return null
            val repositoryAttribute = request.getAttribute(REPO_KEY)
            return if (repositoryAttribute == null) {
                val artifactInfo = getArtifactInfo(request)
                queryRepositoryDetail(artifactInfo).apply { request.setAttribute(REPO_KEY, this) }
            } else {
                repositoryAttribute as RepositoryDetail
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

        private fun queryRepositoryDetail(artifactInfo: ArtifactInfo): RepositoryDetail {
            with(artifactInfo) {
                val repositoryType = artifactConfiguration.getRepositoryType()
                val response = if (repositoryType == RepositoryType.NONE) {
                    repositoryClient.getRepoDetail(projectId, repoName)
                } else {
                    repositoryClient.getRepoDetail(projectId, repoName, repositoryType.name)
                }
                return response.data ?: throw ArtifactNotFoundException("Repository[${artifactInfo.getRepoIdentify()}] not found")
            }
        }
    }
}
