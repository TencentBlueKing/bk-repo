package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.ExternalErrorCodeException
import com.tencent.bkrepo.common.storage.core.FileStorage
import com.tencent.bkrepo.common.storage.util.CredentialsUtils
import com.tencent.bkrepo.common.storage.util.FileDigestUtils.fileSha256
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
import com.tencent.bkrepo.repository.pojo.node.NodeCreateRequest
import com.tencent.bkrepo.repository.util.NodeUtils
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

/**
 * 通用文件上传服务类
 *
 * @author: carrypan
 * @date: 2019-10-08
 */
@Service
class UploadService @Autowired constructor(
    private val uploadTransactionRepository: UploadTransactionRepository,
    private val blockRecordRepository: BlockRecordRepository,
    private val repositoryResource: RepositoryResource,
    private val nodeResource: NodeResource,
    private val fileStorage: FileStorage
) {
    @Value("\${upload.transaction.expires:43200}")
    private val uploadTransactionExpires: Long = 3600 * 12

    @Transactional(rollbackFor = [Throwable::class])
    fun simpleUpload(userId: String, projectId: String, repoName: String, fullPath: String, sha256: String?, expires: Long, overwrite: Boolean, file: MultipartFile) {
        val formattedFullPath = NodeUtils.formatFullPath(fullPath)
        val fullUri = "$projectId/$repoName/$formattedFullPath"

        // 参数格式校验
        expires.takeIf { it >= 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "expires")
        file.takeUnless { it.isEmpty } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_NULL, "file")

        // TODO: 校验权限

        // 校验sha256
        val calculatedSha256 = fileSha256(listOf(file.inputStream))
        if (sha256 != null && calculatedSha256 != sha256) {
            logger.warn("user[$userId] simply upload file [$fullUri] failed: file sha256 verification failed")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "sha256")
        }

        // 判断仓库是否存在
        val repository = repositoryResource.queryDetail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId] simply upload file  [$fullUri] failed: $repoName not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        }

        // 保存节点
        val result = nodeResource.create(NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = formattedFullPath,
                expires = expires,
                overwrite = overwrite,
                size = file.size,
                sha256 = calculatedSha256,
                operator = userId
        ))

        if (result.isOk()) {
            val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
            fileStorage.store(calculatedSha256, file.inputStream, storageCredentials)
            logger.info("user[$userId] simply upload file [$fullUri] success")
        } else {
            logger.warn("user[$userId] simply upload file [$fullUri] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun preCheck(userId: String, projectId: String, repoName: String, fullPath: String, sha256: String?, expires: Long, overwrite: Boolean): UploadTransactionInfo {
        val formattedFullPath = NodeUtils.formatFullPath(fullPath)
        val fullUri = "$projectId/$repoName/$formattedFullPath"

        // 参数校验
        expires.takeIf { it >= 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "expires")

        // TODO: 校验权限

        // 判断仓库是否存在
        repositoryResource.queryDetail(projectId, repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId] preCheck [$fullUri] failed: $repoName not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, repoName)
        }

        // 判断文件是否存在
        if (!overwrite && nodeResource.exist(projectId, repoName, fullPath).data == true) {
            logger.warn("user[$userId] preCheck [$fullUri] failed: file already exists")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_EXIST, formattedFullPath)
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
    fun blockUpload(userId: String, uploadId: String, sequence: Int, file: MultipartFile, size: Long?, sha256: String?) {
        // 参数校验
        file.takeUnless { it.isEmpty } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_NULL, "file")
        sequence.takeIf { it > 0 } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "sequence")

        // TODO: 校验权限

        // 判断uploadId是否存在
        val uploadTransaction = uploadTransactionRepository.findByIdOrNull(uploadId) ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, uploadId)
        val fullUri = "$uploadId/$sequence"
        // 判断仓库是否存在
        val repository = repositoryResource.queryDetail(uploadTransaction.projectId, uploadTransaction.repoName, REPO_TYPE).data ?: run {
            logger.warn("user[$userId] upload block [$fullUri] failed: ${uploadTransaction.repoName} not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, uploadTransaction.repoName)
        }
        // 校验size
        if (size != null && file.size != size) {
            logger.warn("user[$userId] upload block [$fullUri] failed: file size verification failed")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "size")
        }
        // 校验sha256
        val calculatedSha256 = fileSha256(listOf(file.inputStream))
        if (sha256 != null && calculatedSha256 != sha256) {
            logger.warn("user[$userId] upload block [$fullUri] failed: file sha256 verification failed")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "sha256")
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
                size = file.size,
                sha256 = calculatedSha256,
                uploadId = uploadId
        ))

        // 保存文件
        val storageCredentials = CredentialsUtils.readString(repository.storageCredentials?.type, repository.storageCredentials?.credentials)
        fileStorage.store(calculatedSha256, file.inputStream, storageCredentials)

        logger.info("user[$userId] upload block [$fullUri] success.")
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun abortUpload(userId: String, uploadId: String) {
        // TODO: 校验权限

        // 判断uploadId是否存在
        val uploadTransaction = uploadTransactionRepository.findByIdOrNull(uploadId) ?: run {
            logger.warn("user[$userId] abort upload [$uploadId] failed: $uploadId not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, uploadId)
        }
        // 查询repository
        val repository = repositoryResource.queryDetail(uploadTransaction.projectId, uploadTransaction.repoName, REPO_TYPE).data
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

        logger.info("user[$userId] abort upload block [${uploadTransaction.projectId}/${uploadTransaction.repoName}${uploadTransaction.fullPath}] success.")
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun completeUpload(userId: String, uploadId: String, blockSha256ListStr: String?) {
        // TODO: 校验权限

        // 判断uploadId是否存在
        val uploadTransaction = uploadTransactionRepository.findByIdOrNull(uploadId) ?: run {
            logger.warn("user[$userId] complete upload [$uploadId] failed: $uploadId not found")
            throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, uploadId)
        }
        // 校验分块sha256
        val sortedUploadedBlockRecordList = blockRecordRepository.findByUploadId(uploadId).sortedBy { it.sequence }
        // 分块记录不能为空
        if (sortedUploadedBlockRecordList.isEmpty()) {
            logger.warn("user[$userId] complete upload [$uploadId] failed: block record is empty")
            throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_NULL, "file block record")
        }
        // 分块记录数量一致性
        logger.debug("blockSha256Str: $blockSha256ListStr")
        val blockSha256List = blockSha256ListStr?.split(",")

        blockSha256List?.run {
            if (this.size != sortedUploadedBlockRecordList.size) {
                logger.warn("user[$userId] complete upload [$uploadId] failed: block count verification failed")
                throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "file block count")
            }
        }
        // 分块记录完整性
        for (i in sortedUploadedBlockRecordList.indices) {
            if (sortedUploadedBlockRecordList[i].sequence != i + 1) {
                logger.warn("user[$userId] complete upload [$uploadId] failed: lock block ${i + 1}")
                throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_NULL, "file block $${i + 1}")
            }

            blockSha256List?.run {
                if (this[i] != sortedUploadedBlockRecordList[i].sha256) {
                    logger.warn("user[$userId] complete upload [$uploadId] failed: block ${i + 1} sha256 verification failed")
                    throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "file block ${i + 1}")
                }
            }
        }
        // 保存节点
        val totalSize = sortedUploadedBlockRecordList.sumBy { it.size.toInt() }.toLong()
        val blockList = sortedUploadedBlockRecordList.map { FileBlock(sequence = it.sequence, size = it.size, sha256 = it.sha256) }
        val result = nodeResource.create(NodeCreateRequest(
                projectId = uploadTransaction.projectId,
                repoName = uploadTransaction.repoName,
                folder = false,
                fullPath = uploadTransaction.fullPath,
                expires = uploadTransaction.expires,
                overwrite = uploadTransaction.overwrite,
                size = totalSize,
                blockList = blockList,
                operator = userId
        ))

        if (result.isOk()) {
            // 删除分块记录
            blockRecordRepository.deleteByUploadId(uploadId)
            // 删除上传事物
            uploadTransactionRepository.deleteById(uploadId)
            logger.info("user[$userId] complete upload [$uploadId] success")
        } else {
            logger.warn("user[$userId] complete upload [$uploadId] failed: [${result.code}, ${result.message}]")
            throw ExternalErrorCodeException(result.code, result.message)
        }
    }

    fun queryBlockInfo(userId: String, uploadId: String): List<BlockInfo> {
        // 判断uploadId是否存在
        uploadTransactionRepository.findByIdOrNull(uploadId) ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, uploadId)
        return blockRecordRepository.findByUploadId(uploadId).map { BlockInfo(size = it.size, sha256 = it.sha256, sequence = it.sequence) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UploadService::class.java)
    }
}
