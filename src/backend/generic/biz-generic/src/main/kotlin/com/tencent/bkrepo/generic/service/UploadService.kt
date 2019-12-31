package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.config.REPO_KEY
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.service.util.HeaderUtils.getBooleanHeader
import com.tencent.bkrepo.common.service.util.HeaderUtils.getLongHeader
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.generic.constant.HEADER_EXPIRES
import com.tencent.bkrepo.generic.constant.HEADER_OVERWRITE
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.upload.UploadTransactionInfo
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 通用文件上传服务类
 *
 * @author: carrypan
 * @date: 2019-10-08
 */
@Service
class UploadService @Autowired constructor(
    private val nodeResource: NodeResource,
    private val storageService: StorageService
) {
    @Value("\${upload.transaction.expires:43200}")
    private val uploadTransactionExpires: Long = 3600 * 12

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun upload(artifactInfo: GenericArtifactInfo, file: ArtifactFile) {
        val context = ArtifactUploadContext(file)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.upload(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun startBlockUpload(userId: String, artifactInfo: GenericArtifactInfo): UploadTransactionInfo {
        with(artifactInfo) {
            val expires = getLongHeader(HEADER_EXPIRES)
            val overwrite = getBooleanHeader(HEADER_OVERWRITE)

            expires.takeIf { it >= 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "expires")
            // 判断文件是否存在
            if (!overwrite && nodeResource.exist(projectId, repoName, fullPath).data == true) {
                logger.warn("User[$userId] start block upload [${artifactInfo.getFullUri()}] failed: artifact already exists.")
                throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, fullPath)
            }
            val uploadId = storageService.createBlockId()
            val uploadTransaction = UploadTransactionInfo(uploadId = uploadId, expires = uploadTransactionExpires)

            logger.info("User[$userId] start block upload [${artifactInfo.getFullUri()}] success: $uploadTransaction.")
            return uploadTransaction
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun abortBlockUpload(userId: String, uploadId: String, artifactInfo: GenericArtifactInfo) {
        storageService.deleteBlockId(uploadId)
        logger.info("User[$userId] abort upload block [${artifactInfo.getFullUri()}] success.")
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun completeBlockUpload(userId: String, uploadId: String, artifactInfo: GenericArtifactInfo) {
        val storageCredentials = getStorageCredentials()
        // 判断uploadId是否存在
        if (!storageService.checkBlockId(uploadId)) {
            logger.warn("User[$userId] abort block upload [${artifactInfo.getFullUri()}] failed: uploadId not found.")
            throw ErrorCodeException(GenericMessageCode.UPLOAD_ID_NOT_FOUND, uploadId)
        }

        val combinedFileInfo = storageService.mergeBlock(uploadId, storageCredentials)
        // 保存节点
        nodeResource.create(
            NodeCreateRequest(
                projectId = artifactInfo.projectId,
                repoName = artifactInfo.repoName,
                folder = false,
                fullPath = artifactInfo.fullPath,
                sha256 = combinedFileInfo.digest,
                overwrite = true,
                size = combinedFileInfo.size,
                operator = userId
            )
        )
        logger.info("User[$userId] complete upload [${artifactInfo.getFullUri()}] success")
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    fun listBlock(userId: String, uploadId: String, artifactInfo: GenericArtifactInfo): List<BlockInfo> {
        if (!storageService.checkBlockId(uploadId)) throw ErrorCodeException(GenericMessageCode.UPLOAD_ID_NOT_FOUND, uploadId)
        val blockInfoList = storageService.listBlock(uploadId)
        return blockInfoList.mapIndexed { index, it -> BlockInfo(size = it.first, sequence = index + 1, sha256 = it.second) }
    }

    private fun getStorageCredentials(): StorageCredentials? {
        val repoInfo = HttpContextHolder.getRequest().getAttribute(REPO_KEY) as RepositoryInfo
        return repoInfo.storageCredentials
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UploadService::class.java)
    }
}
