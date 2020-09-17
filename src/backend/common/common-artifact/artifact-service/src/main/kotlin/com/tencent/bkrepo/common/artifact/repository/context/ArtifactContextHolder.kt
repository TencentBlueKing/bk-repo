package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.api.constant.StringPool
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
import com.tencent.bkrepo.common.artifact.repository.composite.CompositeRepository
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactRepository
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

@Component
class ArtifactContextHolder(
    artifactConfiguration: ArtifactConfiguration,
    repositoryClient: RepositoryClient,
    localRepository: ObjectProvider<LocalRepository>,
    remoteRepository: ObjectProvider<RemoteRepository>,
    virtualRepository: ObjectProvider<VirtualRepository>,
    compositeRepository: ObjectProvider<CompositeRepository>
) {

    init {
        Companion.artifactConfiguration = artifactConfiguration
        Companion.repositoryClient = repositoryClient
        Companion.localRepository = localRepository
        Companion.remoteRepository = remoteRepository
        Companion.virtualRepository = virtualRepository
        Companion.compositeRepository = compositeRepository
    }

    companion object {
        lateinit var artifactConfiguration: ArtifactConfiguration
        private lateinit var repositoryClient: RepositoryClient
        private lateinit var localRepository: ObjectProvider<LocalRepository>
        private lateinit var remoteRepository: ObjectProvider<RemoteRepository>
        private lateinit var virtualRepository: ObjectProvider<VirtualRepository>
        private lateinit var compositeRepository: ObjectProvider<CompositeRepository>

        fun getRepository(repositoryCategory: RepositoryCategory? = null): ArtifactRepository {
            return when (repositoryCategory ?: getRepoDetail()!!.category) {
                RepositoryCategory.LOCAL -> localRepository.`object`
                RepositoryCategory.REMOTE -> remoteRepository.`object`
                RepositoryCategory.VIRTUAL -> virtualRepository.`object`
                RepositoryCategory.COMPOSITE -> compositeRepository.`object`
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
