package com.tencent.bkrepo.media.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.media.config.MediaProperties
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 媒体仓库创建与存储凭证解析
 */
@Service
class MediaRepoService(
    private val repositoryService: RepositoryService,
    private val mediaProperties: MediaProperties,
) {

    fun resolveStorageCredentialsKey(projectId: String): String? {
        return mediaProperties.getStorageCredentialsKey(projectId)
    }

    fun resolveCosStorageCredentialsKey(projectId: String): String? {
        return mediaProperties.getCosStorageCredentialsKey(projectId)
    }

    /**
     * 仓库不存在时按指定存储凭证自动创建。
     * @return 仓库详情
     */
    fun ensureRepo(
        projectId: String,
        repoName: String,
        storageCredentialsKey: String? = resolveStorageCredentialsKey(projectId),
        display: Boolean = true,
    ): RepositoryDetail {
        repositoryService.getRepoDetail(projectId, repoName)?.let { return it }
        return try {
            val created = repositoryService.createRepo(
                RepoCreateRequest(
                    projectId = projectId,
                    name = repoName,
                    type = RepositoryType.MEDIA,
                    category = RepositoryCategory.LOCAL,
                    public = false,
                    display = display,
                    storageCredentialsKey = storageCredentialsKey,
                )
            )
            logger.info(
                "Create media repository [$projectId/$repoName] with credentials [$storageCredentialsKey]"
            )
            created
        } catch (e: ErrorCodeException) {
            if (e.messageCode == ArtifactMessageCode.REPOSITORY_EXISTED) {
                logger.info("Media repository [$projectId/$repoName] already exists")
                repositoryService.getRepoDetail(projectId, repoName)
                    ?: throw e
            } else {
                throw e
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MediaRepoService::class.java)
    }
}
