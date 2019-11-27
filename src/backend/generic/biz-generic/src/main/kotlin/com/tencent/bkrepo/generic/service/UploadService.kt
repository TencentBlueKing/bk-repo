package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.auth.PermissionService
import com.tencent.bkrepo.common.service.util.HeaderUtils.getBooleanHeader
import com.tencent.bkrepo.common.service.util.HeaderUtils.getHeader
import com.tencent.bkrepo.common.service.util.HeaderUtils.getLongHeader
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.util.CredentialsUtils
import com.tencent.bkrepo.common.storage.util.FileDigestUtils.fileSha256
import com.tencent.bkrepo.generic.constant.BKREPO_META_PREFIX
import com.tencent.bkrepo.generic.constant.HEADER_EXPIRES
import com.tencent.bkrepo.generic.constant.HEADER_OVERWRITE
import com.tencent.bkrepo.generic.constant.HEADER_SHA256
import com.tencent.bkrepo.generic.constant.REPO_TYPE
import com.tencent.bkrepo.generic.constant.UploadStatusEnum
import com.tencent.bkrepo.generic.model.TBlockRecord
import com.tencent.bkrepo.generic.model.TUploadTransaction
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.upload.UploadTransactionInfo
import com.tencent.bkrepo.generic.repository.BlockRecordRepository
import com.tencent.bkrepo.generic.repository.UploadTransactionRepository
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.api.RepositoryResource
import com.tencent.bkrepo.repository.pojo.node.FileBlock
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.util.NodeUtils
import java.time.LocalDateTime
import javax.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional



/**
 * 通用文件上传服务类
 *
 * @author: carrypan
 * @date: 2019-10-08
 */
@Service
class UploadService @Autowired constructor(
    private val permissionService: PermissionService,
    private val uploadTransactionRepository: UploadTransactionRepository,
    private val blockRecordRepository: BlockRecordRepository,
    private val repositoryResource: RepositoryResource,
    private val nodeResource: NodeResource,
    private val fileStorage: FileStorage
) {
    @Value("\${upload.transaction.expires:43200}")
    private val uploadTransactionExpires: Long = 3600 * 12

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun simpleUpload(userId: String, artifactInfo: ArtifactInfo, file: ArtifactFile) {
        val request = HttpContextHolder.getRequest()

        val projectId = artifactInfo.projectId
        val repoName = artifactInfo.repoName
        val fullPath = artifactInfo.coordinate.fullPath
        val formattedFullPath = NodeUtils.formatFullPath(fullPath)

        // 解析参数
        val sha256 = getHeader(HEADER_SHA256)
        val expires = getLongHeader(HEADER_EXPIRES)
        val overwrite = getBooleanHeader(HEADER_OVERWRITE)
        val metadata = parseMetadata(request)
        val contentLength = request.contentLengthLong
        val size = file.getSize()

        // 参数格式校验
        expires.takeIf { it >= 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "expires")
        contentLength.takeIf { it > 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "content length")
        size.takeIf { it > 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_NULL, "file content")

        // 校验sha256
        val calculatedSha256 = fileSha256(listOf(file.getInputStream()))
        if (sha256 != null && calculatedSha256 != sha256) {
            logger.warn("User[$userId] simply upload file [${artifactInfo.getUri()}] failed: file sha256 verification failed")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "sha256")
        }

        // 判断仓库是否存在
        val repository = repositoryResource.detail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.warn("User[$userId] simply upload file  [${artifactInfo.getUri()}] failed: $repoName not found")
            throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
        }

        // 保存节点
        val result = nodeResource.create(
            NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = formattedFullPath,
                expires = expires,
                overwrite = overwrite,
                size = size,
                sha256 = calculatedSha256,
                metadata = metadata,
                operator = userId
            )
        )

        if (result.isOk()) {
            val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
            fileStorage.store(calculatedSha256, file.getInputStream(), storageCredentials)
            logger.info("User[$userId] simply upload file [${artifactInfo.getUri()}] success")
        } else {
            logger.warn("User[$userId] simply upload file [${artifactInfo.getUri()}] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun preCheck(userId: String, artifactInfo: ArtifactInfo): UploadTransactionInfo {
        // 解析参数
        val projectId = artifactInfo.projectId
        val repoName = artifactInfo.repoName
        val fullPath = artifactInfo.coordinate.fullPath
        val formattedFullPath = NodeUtils.formatFullPath(fullPath)
        val expires = getLongHeader(HEADER_EXPIRES)
        val overwrite = getBooleanHeader(HEADER_OVERWRITE)

        // 参数校验
        expires.takeIf { it >= 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "expires")

        // 判断仓库是否存在
        repositoryResource.detail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.warn("User[$userId] preCheck [${artifactInfo.getUri()}] failed: $repoName not found")
            throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
        }

        // 判断文件是否存在
        if (!overwrite && nodeResource.exist(projectId, repoName, fullPath).data == true) {
            logger.warn("User[$userId] preCheck [${artifactInfo.getUri()}] failed: file already exists")
            throw ErrorCodeException(ArtifactMessageCode.NODE_IS_EXIST, formattedFullPath)
        }

        // 创建上传事物
        val uploadTransaction = TUploadTransaction(
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now(),
                projectId = projectId,
                repoName = repoName,
                fullPath = formattedFullPath,
                status = UploadStatusEnum.UPLOADING,
                expires = expires,
                overwrite = overwrite
        )
        uploadTransactionRepository.save(uploadTransaction)
        // 返回结果
        return UploadTransactionInfo(uploadId = uploadTransaction.id!!, expires = uploadTransactionExpires)
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun blockUpload(userId: String, uploadId: String, sequence: Int, file: ArtifactFile) {
        val request = HttpContextHolder.getRequest()
        // 解析参数
        val sha256 = getHeader(HEADER_SHA256)
        val contentLength = request.contentLengthLong
        val size = file.getSize()

        // 参数校验
        sequence.takeIf { it > 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "sequence")
        contentLength.takeIf { it > 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "content length")
        size.takeIf { it > 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_NULL, "file content")

        // 判断uploadId是否存在
        val uploadTransaction = uploadTransactionRepository.findByIdOrNull(uploadId) ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, uploadId)
        val fullUri = "$uploadId/$sequence"

        // 鉴权
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, uploadTransaction.projectId, uploadTransaction.repoName))

        // 判断仓库是否存在
        val repository = repositoryResource.detail(uploadTransaction.projectId, uploadTransaction.repoName, REPO_TYPE).data ?: run {
            logger.warn("User[$userId] upload block [$fullUri] failed: ${uploadTransaction.repoName} not found")
            throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, uploadTransaction.repoName)
        }

        // 校验sha256
        val calculatedSha256 = fileSha256(listOf(file.getInputStream()))
        if (sha256 != null && calculatedSha256 != sha256) {
            logger.warn("User[$userId] upload block [$fullUri] failed: file sha256 verification failed")
            throw ErrorCodeException(ArtifactMessageCode.SHA256_CHECK_FAILED)
        }
        // 删除旧的分块记录
        blockRecordRepository.deleteByUploadIdAndSequence(uploadId, sequence)

        // 保存新的分块记录
        blockRecordRepository.save(TBlockRecord(
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now(),
                sequence = sequence,
                size = size,
                sha256 = calculatedSha256,
                uploadId = uploadId
        ))

        // 保存文件
        val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
        fileStorage.store(calculatedSha256, file.getInputStream(), storageCredentials)

        logger.info("User[$userId] upload block [$fullUri] success.")
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun abortUpload(userId: String, uploadId: String) {
        // 判断uploadId是否存在
        val uploadTransaction = uploadTransactionRepository.findByIdOrNull(uploadId) ?: run {
            logger.warn("User[$userId] abort upload [$uploadId] failed: $uploadId not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, uploadId)
        }
        // 鉴权
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, uploadTransaction.projectId, uploadTransaction.repoName))
        // 查询repository
        val repository = repositoryResource.detail(uploadTransaction.projectId, uploadTransaction.repoName, REPO_TYPE).data
        // 查询分块记录
        val blockRecordList = blockRecordRepository.findByUploadId(uploadId)
        // 删除文件
        repository?.run {
            val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
            blockRecordList.forEach { fileStorage.delete(it.sha256, storageCredentials) }
        }
        // 删除分块记录
        blockRecordList.forEach { blockRecordRepository.deleteById(it.id!!) }
        // 删除上传事物
        uploadTransactionRepository.deleteById(uploadTransaction.id!!)

        logger.info("User[$userId] abort upload block [${uploadTransaction.projectId}/${uploadTransaction.repoName}${uploadTransaction.fullPath}] success.")
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun completeUpload(userId: String, uploadId: String, blockSha256ListStr: String?) {
        // 判断uploadId是否存在
        val uploadTransaction = uploadTransactionRepository.findByIdOrNull(uploadId) ?: run {
            logger.warn("User[$userId] complete upload [$uploadId] failed: $uploadId not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, uploadId)
        }
        // 鉴权
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.WRITE, uploadTransaction.projectId, uploadTransaction.repoName))
        // 校验分块sha256
        val sortedUploadedBlockRecordList = blockRecordRepository.findByUploadId(uploadId).sortedBy { it.sequence }
        // 分块记录不能为空
        if (sortedUploadedBlockRecordList.isEmpty()) {
            logger.warn("User[$userId] complete upload [$uploadId] failed: block record is empty")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_NULL, "file block record")
        }
        // 分块记录数量一致性
        val blockSha256List = blockSha256ListStr?.split(",")

        blockSha256List?.run {
            if (this.size != sortedUploadedBlockRecordList.size) {
                logger.warn("User[$userId] complete upload [$uploadId] failed: block count verification failed")
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "file block count")
            }
        }
        // 分块记录完整性
        for (i in sortedUploadedBlockRecordList.indices) {
            if (sortedUploadedBlockRecordList[i].sequence != i + 1) {
                logger.warn("User[$userId] complete upload [$uploadId] failed: lock block ${i + 1}")
                throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_NULL, "file block $${i + 1}")
            }

            blockSha256List?.run {
                if (this[i] != sortedUploadedBlockRecordList[i].sha256) {
                    logger.warn("User[$userId] complete upload [$uploadId] failed: block ${i + 1} sha256 verification failed")
                    throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "file block ${i + 1}")
                }
            }
        }
        // 保存节点
        val totalSize = sortedUploadedBlockRecordList.sumBy { it.size.toInt() }.toLong()
        val blockList = sortedUploadedBlockRecordList.map { FileBlock(sequence = it.sequence, size = it.size, sha256 = it.sha256) }
        val result = nodeResource.create(
            NodeCreateRequest(
                projectId = uploadTransaction.projectId,
                repoName = uploadTransaction.repoName,
                folder = false,
                fullPath = uploadTransaction.fullPath,
                expires = uploadTransaction.expires,
                overwrite = uploadTransaction.overwrite,
                size = totalSize,
                blockList = blockList,
                operator = userId
            )
        )

        if (result.isOk()) {
            // 删除分块记录
            blockRecordRepository.deleteByUploadId(uploadId)
            // 删除上传事物
            uploadTransactionRepository.deleteById(uploadId)
            logger.info("User[$userId] complete upload [$uploadId] success")
        } else {
            logger.warn("User[$userId] complete upload [$uploadId] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }
    }

    fun getUploadedBlockList(userId: String, uploadId: String): List<BlockInfo> {
        // 判断uploadId是否存在
        val uploadTransaction = uploadTransactionRepository.findByIdOrNull(uploadId) ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, uploadId)
        // 鉴权
        permissionService.checkPermission(CheckPermissionRequest(userId, ResourceType.REPO, PermissionAction.READ, uploadTransaction.projectId, uploadTransaction.repoName))
        return blockRecordRepository.findByUploadId(uploadId).map { BlockInfo(size = it.size, sha256 = it.sha256, sequence = it.sequence) }
    }


    private fun parseMetadata(request: HttpServletRequest): Map<String, String> {
        val metadata = HashMap<String, String>()
        val headerNames = request.headerNames
        while (headerNames.hasMoreElements()) {
            val headerName = headerNames.nextElement()
            if (headerName.startsWith(BKREPO_META_PREFIX)) {
                val key = headerName.replace(BKREPO_META_PREFIX, "")
                if (key.trim().isNotEmpty()) {
                    val value = request.getHeader(headerName)
                    metadata[key] = value
                }
            }
        }

        return metadata
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UploadService::class.java)
    }
}
