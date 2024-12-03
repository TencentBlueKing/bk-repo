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
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.constant.FAKE_MD5
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.model.TBlockNode
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
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.artifact.GenericLocalRepository
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.generic.constant.HEADER_EXPIRES
import com.tencent.bkrepo.generic.constant.HEADER_FILE_SIZE
import com.tencent.bkrepo.generic.constant.HEADER_OVERWRITE
import com.tencent.bkrepo.generic.model.NodeAttribute
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.NewBlockInfo
import com.tencent.bkrepo.generic.pojo.UploadTransactionInfo
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
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

    fun newBlockUpload(artifactInfo: GenericArtifactInfo, file: ArtifactFile) {
        val context = ArtifactUploadContext(file)
        repository.newUpload(context)
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
                expireSeconds = TRANSACTION_EXPIRES
            )

            logger.info("User[${SecurityUtils.getPrincipal()}] start block upload [$artifactInfo] success: $uploadId.")
            return uploadTransaction
        }
    }

    fun newStartBlockUpload(userId: String, artifactInfo: GenericArtifactInfo){
        val attributes = NodeAttribute(
            uid = NodeAttribute.NOBODY,
            gid = NodeAttribute.NOBODY,
            mode = NodeAttribute.DEFAULT_MODE
        )
        val fsAttr = MetadataModel(
            key = FS_ATTR_KEY,
            value = attributes
        )
        val request = NodeCreateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            folder = false,
            fullPath = artifactInfo.getArtifactFullPath(),
            sha256 = FAKE_SHA256,
            md5 = FAKE_MD5,
            operator = userId,
            size = getLongHeader(HEADER_FILE_SIZE),
            overwrite = getBooleanHeader(HEADER_OVERWRITE),
            expires = getLongHeader(HEADER_EXPIRES),
            nodeMetadata = listOf(fsAttr)
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

    fun abortNewBlockUpload(userId: String, artifactInfo: GenericArtifactInfo) {
        delete(userId, artifactInfo)
        with(artifactInfo){
            blockNodeService.deleteBlocks(projectId, repoName, getArtifactFullPath())
        }
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
            nodeMetadata = repository.resolveMetadata(HttpContextHolder.getRequest())
        )
        ActionAuditContext.current().setInstance(request)
        nodeService.createNode(request)
        logger.info("User[${SecurityUtils.getPrincipal()}] complete upload [$artifactInfo] success.")
    }

    fun completeNewBlockUpload(
        userId: String,
        artifactInfo: GenericArtifactInfo
    ) {
        // 获取并按起始位置排序块信息列表
        val blockInfoList = listBlocks(artifactInfo).sortedBy { it.startPos }

        // 获取节点详情，如果节点不存在则抛出异常
        val node = ArtifactContextHolder.getNodeDetail(artifactInfo)
            ?: throw ErrorCodeException(GenericMessageCode.BLOCK_FILE_NODE_NOT_CREATE, artifactInfo)

        // 如果节点是文件夹，抛出异常
        if (node.folder) {
            throw ErrorCodeException(GenericMessageCode.BLOCK_LIST_PATH_IS_FOLDER, artifactInfo)
        }

        var offset = 0L  // 用于记录当前偏移量

        // 遍历块信息列表，创建对应的块节点
        blockInfoList.forEach { blockInfo ->
            val blockSize = blockInfo.size
            val blockNode = TBlockNode(
                id = blockInfo.id,
                createdBy = blockInfo.createdBy,
                createdDate = blockInfo.createdDate,
                nodeFullPath = blockInfo.nodeFullPath,
                startPos = offset,
                endPos = offset + blockSize - 1,
                sha256 = blockInfo.sha256,
                projectId = blockInfo.projectId,
                repoName = blockInfo.repoName,
                size = blockSize
            )

            blockNodeService.updateBlock(blockNode)
            offset += blockInfo.size  // 更新偏移量
        }

        // 验证节点大小是否与块总大小一致
        if (node.size != offset) {
            abortNewBlockUpload(userId, artifactInfo)
            throw ErrorCodeException(GenericMessageCode.NODE_DATA_HAS_CHANGED, artifactInfo)
        }
    }

    fun listBlock(userId: String, uploadId: String, artifactInfo: GenericArtifactInfo): List<BlockInfo> {
        val storageCredentials = getStorageCredentials(artifactInfo)
        checkUploadId(uploadId, storageCredentials)

        val blockInfoList = storageService.listBlock(uploadId, storageCredentials)
        return blockInfoList.map {
            BlockInfo(size = it.first, sha256 = it.second, sequence = it.third)
        }
    }

    fun newListBlock(userId: String, artifactInfo: GenericArtifactInfo): List<NewBlockInfo> {

        val blockInfoList = listBlocks(artifactInfo)

        return blockInfoList.map { blockInfo ->
            NewBlockInfo(blockInfo.size, blockInfo.sha256, blockInfo.startPos)
        }
    }

    private fun listBlocks(artifactInfo: GenericArtifactInfo): List<TBlockNode>
    {
        val node = ArtifactContextHolder.getNodeDetail(artifactInfo)
        val context = ArtifactDownloadContext()

        if (node == null) {
            // 安全调用 repositoryDetail，防止空指针异常
            if (context.repositoryDetail.category == RepositoryCategory.LOCAL) {
                throw NodeNotFoundException(artifactInfo.getArtifactFullPath())
            } else {
                // 当 node 为 null 且不是 LOCAL 仓库时，返回空列表
                return emptyList()
            }
        }

        if (node.folder) {
            throw ErrorCodeException(
                GenericMessageCode.BLOCK_LIST_PATH_IS_FOLDER,
                artifactInfo
            )
        }

        val blockInfoList = blockNodeService.listBlocks(
            Range.full(node.size),
            node.projectId,
            node.repoName,
            node.fullPath,
            node.createdDate
        )
        return blockInfoList
    }

    private fun checkUploadId(uploadId: String, storageCredentials: StorageCredentials?) {
        if (!storageService.checkBlockId(uploadId, storageCredentials)) {
            throw ErrorCodeException(GenericMessageCode.UPLOAD_ID_NOT_FOUND, uploadId)
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
