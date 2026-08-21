package com.tencent.bkrepo.media.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.media.REMOTEDEV_REPO_PREFIX
import com.tencent.bkrepo.media.artifact.CosArchiveArtifactInfo
import com.tencent.bkrepo.media.config.MediaProperties
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * COS 加密归档上传/下载。
 * 存储层按仓库凭证完成上传加密、下载解密。
 */
@Service
class CosArchiveService(
    private val mediaProperties: MediaProperties,
    private val storageManager: StorageManager,
) : ArtifactService() {

    fun upload(
        artifactInfo: CosArchiveArtifactInfo,
        file: ArtifactFile,
        author: String? = null,
        videoStartTime: Long? = null,
        videoEndTime: Long? = null,
    ) {
        val credentialsKey = requireCosCredentialsKey(artifactInfo.projectId)
        val repo = ArtifactContextHolder.getRepoDetail()
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, artifactInfo.repoName)
        val nodeCreateRequest = NodeCreateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            folder = false,
            fullPath = artifactInfo.getArtifactFullPath(),
            size = file.getSize(),
            sha256 = file.getFileSha256(),
            md5 = file.getFileMd5(),
            crc64ecma = file.getFileCrc64ecma(),
            operator = SecurityUtils.getUserId(),
            overwrite = true,
            nodeMetadata = buildMediaMetadata(author, videoStartTime, videoEndTime),
        )
        storageManager.storeArtifactFile(nodeCreateRequest, file, repo.storageCredentials)
        logger.info(
            "Upload cos archive file [$artifactInfo] to credentials [$credentialsKey]"
        )
    }

    fun download(artifactInfo: CosArchiveArtifactInfo) {
        requireCosCredentialsKey(artifactInfo.projectId)
        with(artifactInfo) {
            val repo = ArtifactContextHolder.getRepoDetail()
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            val context = ArtifactDownloadContext(repo, artifactInfo)
            repository.download(context)
        }
    }

    fun requireCosCredentialsKey(projectId: String): String {
        return mediaProperties.getCosStorageCredentialsKey(projectId)
            ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, projectId)
    }

    fun buildArchiveArtifactInfo(
        source: ArtifactInfo,
        author: String?,
        videoStartTime: Long?,
        videoEndTime: Long?,
    ): ArtifactInfo? {
        if (!mediaProperties.isCosArchiveProject(source.projectId)) {
            return null
        }
        val repoName = mediaProperties.cosRepoName
        val date = formatDate(videoStartTime)
        val workspace = source.repoName.removePrefix(REMOTEDEV_REPO_PREFIX)
        val start = formatDateTime(videoStartTime)
        val end = formatDateTime(videoEndTime)
        val authorName = author?.takeIf { it.isNotBlank() } ?: UNKNOWN
        val fullPath = "/$date/$workspace/$start-$end-$authorName.mp4"
        return ArtifactInfo(source.projectId, repoName, fullPath)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CosArchiveService::class.java)
        private const val UNKNOWN = "unknown"
        private const val METADATA_KEY_MEDIA_START_TIME = "media.startTime"
        private const val METADATA_KEY_MEDIA_STOP_TIME = "media.stopTime"
        private const val METADATA_KEY_MEDIA_AUTHOR = "media.author"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        private val BEIJING_ZONE = ZoneId.of("Asia/Shanghai")

        /**
         * 构建录屏归档节点元数据，key 与 MediaArtifactFileConsumer 保持一致
         */
        private fun buildMediaMetadata(
            author: String?,
            videoStartTime: Long?,
            videoEndTime: Long?,
        ): List<MetadataModel>? {
            return listOfNotNull(
                videoStartTime?.let { MetadataModel(METADATA_KEY_MEDIA_START_TIME, it, system = true) },
                videoEndTime?.let { MetadataModel(METADATA_KEY_MEDIA_STOP_TIME, it, system = true) },
                author?.takeIf { it.isNotBlank() }?.let { MetadataModel(METADATA_KEY_MEDIA_AUTHOR, it, system = true) },
            ).ifEmpty { null }
        }

        private fun formatDate(millis: Long?): String {
            if (millis == null) {
                return "O"
            }
            return Instant.ofEpochMilli(millis).atZone(BEIJING_ZONE).format(DATE_FORMATTER)
        }

        private fun formatDateTime(millis: Long?): String {
            if (millis == null) {
                return "O"
            }
            return Instant.ofEpochMilli(millis).atZone(BEIJING_ZONE).format(DATETIME_FORMATTER)
        }
    }
}
