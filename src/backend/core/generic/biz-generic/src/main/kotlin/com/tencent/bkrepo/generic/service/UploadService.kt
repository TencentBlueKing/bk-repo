/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.generic.service

import com.tencent.bk.audit.context.ActionAuditContext
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.metadata.constant.FAKE_MD5
import com.tencent.bkrepo.common.metadata.constant.FAKE_SEPARATE
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.model.NodeAttribute
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils.getBooleanHeader
import com.tencent.bkrepo.common.service.util.HeaderUtils.getLongHeader
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.message.StorageErrorException
import com.tencent.bkrepo.common.storage.pojo.FileInfo
import com.tencent.bkrepo.fs.server.constant.FS_ATTR_KEY
import com.tencent.bkrepo.fs.server.constant.UPLOADID_KEY
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.artifact.GenericLocalRepository
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.generic.constant.HEADER_EXPIRES
import com.tencent.bkrepo.generic.constant.HEADER_FILE_SIZE
import com.tencent.bkrepo.generic.constant.HEADER_OVERWRITE
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.SeparateBlockInfo
import com.tencent.bkrepo.generic.pojo.UploadTransactionInfo
import com.tencent.bkrepo.common.metadata.pojo.metadata.MetadataModel
import com.tencent.bkrepo.common.metadata.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 通用文件上传服务类
 */
@Service
class UploadService(
    private val nodeService: NodeService,
    private val storageService: StorageService,
    private val repositoryService: RepositoryService,
    private val blockNodeService: BlockNodeService,
) : ArtifactService() {

    fun upload(artifactInfo: GenericArtifactInfo, file: ArtifactFile) {
        val context = ArtifactUploadContext(file)
        repository.upload(context)
    }

    fun delete(userId: String, artifactInfo: GenericArtifactInfo) {
        val context = ArtifactRemoveContext()
        repository.remove(context)
        logger.info("User[${SecurityUtils.getPrincipal()}] delete artifact[$artifactInfo] success.")
    }

    fun startBlockUpload(userId: String, artifactInfo: GenericArtifactInfo): UploadTransactionInfo {
        with(artifactInfo) {
            val expires = getLongHeader(HEADER_EXPIRES)
            val overwrite = getBooleanHeader(HEADER_OVERWRITE)
            Preconditions.checkArgument(expires >= 0, "expires")
            // 判断文件是否存在
            if (!overwrite && nodeService.checkExist(this)) {
                logger.warn(
                    "User[${SecurityUtils.getPrincipal()}] start block upload [$artifactInfo] failed: " +
                            "artifact already exists."
                )
                throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, getArtifactName())
            }

            val uploadId = try {
                storageService.createBlockId(getStorageCredentials(artifactInfo))
            } catch (ignore: StorageErrorException) {
                throw BadRequestException(CommonMessageCode.SYSTEM_ERROR)
            }
            val uploadTransaction = UploadTransactionInfo(
                uploadId = uploadId,
                expireSeconds = expires
            )

            logger.info("User[${SecurityUtils.getPrincipal()}] start block upload [$artifactInfo] success: $uploadId.")
            return uploadTransaction
        }
    }

    fun startSeparateBlockUpload(userId: String, artifactInfo: GenericArtifactInfo): UploadTransactionInfo {
        with(artifactInfo) {
            // 获取请求头中是否允许覆盖的参数
            val overwrite = getBooleanHeader(HEADER_OVERWRITE)

            val node = nodeService.getNodeDetail(this)

            val oldNodeId = node?.nodeInfo?.id ?: FAKE_SEPARATE
            // 如果不允许覆盖且节点已经存在，抛出异常
            if (node != null && !overwrite) {
                throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, getArtifactName())
            }

            // 生成唯一的 uploadId，作为上传会话的标识
            val uploadId = "${StringPool.uniqueId()}/$oldNodeId"

            // 创建上传事务信息，设置过期时间
            val uploadTransaction = UploadTransactionInfo(
                uploadId = uploadId,
                expireSeconds = TRANSACTION_EXPIRES
            )
            // 记录上传启动的日志
            logger.info(
                "User[${SecurityUtils.getPrincipal()}] start block upload [$artifactInfo] success, "
                        + "version: $uploadId."
            )
            return uploadTransaction
        }
    }

    fun blockBaseNodeCreate(userId: String, artifactInfo: GenericArtifactInfo, uploadId: String) {
        val attributes = NodeAttribute(
            uid = NodeAttribute.NOBODY,
            gid = NodeAttribute.NOBODY,
            mode = NodeAttribute.DEFAULT_MODE
        )
        val fsAttr = MetadataModel(
            key = FS_ATTR_KEY,
            value = attributes,
        )
        val versionMetadata = MetadataModel(
            key = UPLOADID_KEY,
            value = uploadId
        )
        val fileSize = getLongHeader(HEADER_FILE_SIZE).takeIf { it > 0L }
            ?: throw ErrorCodeException(GenericMessageCode.BLOCK_HEAD_NOT_FOUND)
        val request = NodeCreateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            folder = false,
            fullPath = artifactInfo.getArtifactFullPath(),
            sha256 = FAKE_SHA256,
            md5 = FAKE_MD5,
            operator = userId,
            size = fileSize,
            overwrite = getBooleanHeader(HEADER_OVERWRITE),
            expires = getLongHeader(HEADER_EXPIRES),
            nodeMetadata = listOf(fsAttr, versionMetadata),
            separate = true,
            metadata = mapOf(UPLOADID_KEY to uploadId)
        )
        ActionAuditContext.current().setInstance(request)
        nodeService.createNode(request)
    }

    fun abortBlockUpload(userId: String, uploadId: String, artifactInfo: GenericArtifactInfo) {
        val storageCredentials = getStorageCredentials(artifactInfo)
        checkUploadId(uploadId, storageCredentials)

        storageService.deleteBlockId(uploadId, storageCredentials)
        logger.info("User[${SecurityUtils.getPrincipal()}] abort upload block [$artifactInfo] success.")
    }

    fun abortSeparateBlockUpload(userId: String, uploadId: String, artifactInfo: GenericArtifactInfo) {

        checkUploadIdIsEmpty(uploadId)

        blockNodeService.deleteBlocks(
            artifactInfo.projectId,
            artifactInfo.repoName,
            artifactInfo.getArtifactFullPath(),
            uploadId
        )
    }

    fun completeBlockUpload(
        userId: String,
        uploadId: String,
        artifactInfo: GenericArtifactInfo,
        sha256: String? = null,
        md5: String? = null,
        size: Long? = null,
        mergeFileFlag: Boolean = true
    ) {
        val storageCredentials = getStorageCredentials(artifactInfo)
        checkUploadId(uploadId, storageCredentials)
        val fileInfo = if (!sha256.isNullOrEmpty() && !md5.isNullOrEmpty() && size != null) {
            logger.info(
                "sha256 $sha256, md5 $md5, size $size for " +
                        "fullPath ${artifactInfo.getArtifactFullPath()} with uploadId $uploadId"
            )
            FileInfo(sha256, md5, size)
        } else {
            null
        }
        val mergedFileInfo = try {
            storageService.mergeBlock(uploadId, storageCredentials, fileInfo, mergeFileFlag)
        } catch (ignore: StorageErrorException) {
            throw BadRequestException(GenericMessageCode.CHUNKED_ARTIFACT_BROKEN, sha256.orEmpty())
        }
        // 保存节点
        val repository = ArtifactContextHolder.getRepository(RepositoryCategory.LOCAL) as GenericLocalRepository
        val request = NodeCreateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            folder = false,
            fullPath = artifactInfo.getArtifactFullPath(),
            sha256 = mergedFileInfo.sha256,
            md5 = mergedFileInfo.md5,
            size = mergedFileInfo.size,
            overwrite = getBooleanHeader(HEADER_OVERWRITE),
            operator = userId,
            expires = getLongHeader(HEADER_EXPIRES),
            nodeMetadata = repository.resolveMetadata(HttpContextHolder.getRequest()),
        )
        ActionAuditContext.current().setInstance(request)
        nodeService.createNode(request)
        logger.info("User[${SecurityUtils.getPrincipal()}] complete upload [$artifactInfo] success.")
    }

    fun completeSeparateBlockUpload(userId: String, uploadId: String, artifactInfo: GenericArtifactInfo) {

        checkUploadIdIsEmpty(uploadId)

        val projectId = artifactInfo.projectId
        val repoName = artifactInfo.repoName
        val artifactFullPath = artifactInfo.getArtifactFullPath()

        // 获取并按起始位置排序块信息列表
        val blockInfoList = blockNodeService.listBlocksInUploadId(
            projectId,
            repoName,
            artifactFullPath,
            uploadId = uploadId
        )

        blockInfoList.ifEmpty {
            logger.warn("No block information found for uploadId: $uploadId")
            throw ErrorCodeException(GenericMessageCode.BLOCK_UPDATE_LIST_IS_NULL, artifactInfo)
        }

        // 计算所有块的总大小
        val totalSize = blockInfoList.sumOf { it.size }

        // 验证节点大小是否与块总大小一致
        if (getLongHeader(HEADER_FILE_SIZE) != totalSize) {
            throw ErrorCodeException(GenericMessageCode.NODE_DATA_ERROR, artifactInfo)
        }

        // 创建新的基础节点（Base Node）
        try {
            blockBaseNodeCreate(userId, artifactInfo, uploadId)
        } catch (e: Exception) {
            logger.error(
                "Create block base node failed, file path [${artifactInfo.getArtifactFullPath()}], " +
                        "version : $uploadId"
            )
            abortSeparateBlockUpload(userId, uploadId, artifactInfo)
            throw e
        }

        // 删除旧Block
        blockNodeService.deleteBlocks(
            projectId,
            repoName,
            artifactFullPath
        )

        // 更新节点版本信息为null
        blockNodeService.updateBlockUploadId(
            projectId,
            repoName,
            artifactFullPath,
            uploadId
        )

        // 上传完成，记录日志
        logger.info(
            "User [$userId] successfully completed block upload [uploadId: $uploadId], " +
                    "file path [${artifactFullPath}]."
        )
    }

    fun listBlock(userId: String, uploadId: String, artifactInfo: GenericArtifactInfo): List<BlockInfo> {
        val storageCredentials = getStorageCredentials(artifactInfo)
        checkUploadId(uploadId, storageCredentials)

        val blockInfoList = storageService.listBlock(uploadId, storageCredentials)
        return blockInfoList.map {
            BlockInfo(size = it.first, sha256 = it.second, sequence = it.third)
        }
    }

    fun separateListBlock(
        userId: String,
        uploadId: String,
        artifactInfo: GenericArtifactInfo
    ): List<SeparateBlockInfo> {

        checkUploadIdIsEmpty(uploadId)

        val blockInfoList = blockNodeService.listBlocksInUploadId(
            artifactInfo.projectId,
            artifactInfo.repoName,
            artifactInfo.getArtifactFullPath(),
            uploadId = uploadId
        )

        return blockInfoList.map { blockInfo ->
            SeparateBlockInfo(blockInfo.size, blockInfo.sha256, blockInfo.startPos, blockInfo.uploadId)
        }
    }

    private fun checkUploadId(uploadId: String, storageCredentials: StorageCredentials?) {
        if (!storageService.checkBlockId(uploadId, storageCredentials)) {
            throw ErrorCodeException(GenericMessageCode.UPLOAD_ID_NOT_FOUND, uploadId)
        }
    }

    private fun checkUploadIdIsEmpty(uploadId: String) {
        // 检查uploadId是否为空""
        if (uploadId.isEmpty()) {
            throw ErrorCodeException(GenericMessageCode.BLOCK_UPLOADID_ERROR, uploadId)
        }
    }

    private fun getStorageCredentials(artifactInfo: GenericArtifactInfo): StorageCredentials? {
        with(artifactInfo) {
            val repoDetail = repositoryService.getRepoDetail(projectId, repoName)
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            return repoDetail.storageCredentials
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UploadService::class.java)
        private const val TRANSACTION_EXPIRES: Long = 3600 * 12L
    }
}
