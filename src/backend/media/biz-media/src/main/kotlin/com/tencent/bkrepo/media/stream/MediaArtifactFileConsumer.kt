package com.tencent.bkrepo.media.stream

import com.tencent.bk.audit.context.ActionAuditContext
import com.tencent.bkrepo.common.api.exception.SystemErrorException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.CRC64
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.toArtifactFile
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.metadata.constant.FAKE_MD5
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.model.NodeAttribute
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.fs.server.constant.FS_ATTR_KEY
import com.tencent.bkrepo.fs.server.constant.UPLOADID_KEY
import com.tencent.bkrepo.media.service.TranscodeService
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime

/**
 * 将文件保存为制品构件
 * */
class MediaArtifactFileConsumer(
    private val storageManager: StorageManager,
    private val transcodeService: TranscodeService,
    private val repo: RepositoryDetail,
    private val userId: String,
    private val author: String,
    private val path: String,
    private val transcodeConfig: TranscodeConfig? = null,
    private val storageService: StorageService,
    private val blockNodeService: BlockNodeService,
    private val nodeService: NodeService
) : FileConsumer {

    private val startTime = System.currentTimeMillis()
    override fun accept(t: File) {
        accept(t.name, t.toArtifactFile(), null, System.currentTimeMillis())
    }

    override fun accept(file: File, name: String, endTime: Long) {
        accept(name, file.toArtifactFile(), null, endTime)
    }

    override fun accept(name: String, file: ArtifactFile, extraFiles: Map<String, ArtifactFile>?, endTime: Long) {
        val artifactInfo = genAndStoreArtifactInfos(name, file, endTime)
        val extraArtifactFiles = extraFiles?.map { (name, file) ->
            genAndStoreArtifactInfos(name, file, endTime)
        }
        if (transcodeConfig != null) {
            transcodeService.transcode(
                artifactInfo = artifactInfo,
                transcodeConfig = transcodeConfig,
                userId = userId,
                extraFiles = extraArtifactFiles,
                author = author,
                videoStartTime = startTime,
                videoEndTime = endTime
            )
        }
    }

    fun genAndStoreArtifactInfos(name: String, file: ArtifactFile, endTime: Long): ArtifactInfo {
        val filePath = "$path/$name"
        val artifactInfo = ArtifactInfo(repo.projectId, repo.name, filePath)
        val nodeCreateRequest = buildNodeCreateRequest(artifactInfo, file, userId, author, endTime)
        storageManager.storeArtifactFile(nodeCreateRequest, file, repo.storageCredentials)
        return artifactInfo
    }


    /**
     * 分块存储文件（用于重连场景拼接视频）
     */
    override fun acceptBlock(
        name: String,
        file: ArtifactFile,
        uploadId: String,
        isComplete: Boolean,
        endTime: Long,
        extraFiles: Map<String, ArtifactFile>?
    ) {
        val filePath = "$path/$name"
        val artifactInfo = ArtifactInfo(repo.projectId, repo.name, filePath)
        // 每次都存储当前视频分块
        storeBlockNode(artifactInfo, file, uploadId)
        // 额外文件（鼠标JSON、音频AAC）也走分块存储，使用同一uploadId前缀关联
        val extraArtifactInfoMap = mutableMapOf<String, ArtifactInfo>()
        extraFiles?.forEach { (extraName, extraFile) ->
            if (extraFile.getSize() > 0) {
                val extraFilePath = "$path/$extraName"
                val extraArtifactInfo = ArtifactInfo(repo.projectId, repo.name, extraFilePath)
                // 使用 uploadId + 文件类型前缀 作为额外文件的分块uploadId，保证与视频分块关联但不冲突
                val extraUploadId = "${uploadId}_${extraName.substringBefore(".")}"
                storeBlockNode(extraArtifactInfo, extraFile, extraUploadId)
                extraArtifactInfoMap[extraName] = extraArtifactInfo
            }
        }
        if (isComplete) {
            // 正常结束：合并所有分块，创建完整node
            completeBlockNode(artifactInfo, uploadId)
            // 额外文件也合并分块
            val extraArtifactInfos = extraArtifactInfoMap.map { (extraName, extraArtifactInfo) ->
                val extraUploadId = "${uploadId}_${extraName.substringBefore(".")}"
                completeBlockNode(extraArtifactInfo, extraUploadId)
                extraArtifactInfo
            }.ifEmpty { null }
            // 转码仅在completeBlockNode后触发
            if (transcodeConfig != null) {
                transcodeService.transcode(
                    artifactInfo = artifactInfo,
                    transcodeConfig = transcodeConfig,
                    userId = userId,
                    extraFiles = extraArtifactInfos,
                    author = author,
                    videoStartTime = startTime,
                    videoEndTime = endTime
                )
            }
        }
        logger.info(
            "AcceptBlock [$filePath], uploadId=$uploadId, isComplete=$isComplete, size=${file.getSize()}"
        )
    }

    /**
     * 视频流异常中断时，存储分块节点
     * @param uploadId 上传id，用于关联分块节点
     */
    fun storeBlockNode(artifactInfo: ArtifactInfo, file: ArtifactFile, uploadId: String) {
        with(artifactInfo) {
            val oldBlockNodes = blockNodeService.listBlocksInUploadId(
                projectId,
                repoName,
                getArtifactFullPath(),
                uploadId = uploadId
            )
            val startPos = if (oldBlockNodes.isEmpty()) {
                0L
            } else {
                oldBlockNodes.maxBy { it.endPos }.endPos + 1
            }
            val blockNode = TBlockNode(
                projectId = artifactInfo.projectId,
                repoName = artifactInfo.repoName,
                nodeFullPath = artifactInfo.getArtifactFullPath(),
                size = file.getSize(),
                sha256 = file.getFileSha256(),
                crc64ecma = file.getFileCrc64ecma(),
                startPos = startPos,
                uploadId = uploadId,
                createdBy = author,
                createdDate = LocalDateTime.now(),
                expireDate = LocalDateTime.now().plusDays(1), // 1天过期时间
            )
            val digest = file.getFileSha256()
            val stored = storageService.store(digest, file, repo.storageCredentials)
            try {
                val createdBlock = blockNodeService.createBlock(blockNode, repo.storageCredentials)
            } catch (e: Exception) {
                if (stored > 1) {
                    storageService.delete(digest, repo.storageCredentials)
                }
                throw e
            }
        }
    }

    /**
     * 视频流正常结束时，完成分块存储，创建对应node
     */
    fun completeBlockNode(artifactInfo: ArtifactInfo, uploadId: String) {
        with(artifactInfo) {
            val blockNodes = blockNodeService.listBlocksInUploadId(
                projectId,
                repoName,
                getArtifactFullPath(),
                uploadId = uploadId
            )
            if (blockNodes.isEmpty()) {
                return
            }
            val totalSize = blockNodes.sumOf { it.size }
            val crc64ecma = crc64ecma(blockNodes)
            val beforeCreateNodeTime = LocalDateTime.now()
            blockBaseNodeCreate(userId, artifactInfo, uploadId, totalSize, crc64ecma)
            val afterCreateNodeTime = LocalDateTime.now()
            val beforeUpdateTime = LocalDateTime.now()
            blockNodeService.updateBlockUploadId(projectId, repoName, getArtifactFullPath(), uploadId)
            val afterUpdateTime = LocalDateTime.now()
        }
    }

    fun blockBaseNodeCreate(
        userId: String,
        artifactInfo: ArtifactInfo,
        uploadId: String,
        fileSize: Long,
        crc64ecma: String
    ) {
        val attributes = NodeAttribute(
            uid = NodeAttribute.NOBODY,
            gid = NodeAttribute.NOBODY,
            mode = NodeAttribute.DEFAULT_MODE
        )
        val metadata = mutableListOf<MetadataModel>()
        metadata.add(MetadataModel(UPLOADID_KEY, uploadId, system = true))
        metadata.add(MetadataModel(key = FS_ATTR_KEY, value = attributes))

        val request = NodeCreateRequest(
            projectId = artifactInfo.projectId,
            repoName = artifactInfo.repoName,
            folder = false,
            fullPath = artifactInfo.getArtifactFullPath(),
            sha256 = FAKE_SHA256,
            md5 = FAKE_MD5,
            crc64ecma = crc64ecma,
            operator = userId,
            size = fileSize,
            overwrite = true,
            nodeMetadata = metadata,
            separate = true,
        )
        ActionAuditContext.current().setInstance(request)
        nodeService.createNode(request)
    }

    private fun crc64ecma(blocks: List<TBlockNode>): String {
        var crc64ecma = 0L
        blocks.sortedBy { it.startPos }.forEachIndexed { index, block ->
            val blockCrc64ecma = block.crc64ecma?.let { CRC64.fromUnsignedString(it).value }
            if (blockCrc64ecma == null) {
                logger.error("crc64ecma not found for block node[$block]")
                throw SystemErrorException(CommonMessageCode.SYSTEM_ERROR)
            }
            crc64ecma = if (index == 0) {
                blockCrc64ecma
            } else {
                CRC64.combine(crc64ecma, blockCrc64ecma, block.size)
            }
        }

        return CRC64(crc64ecma).unsignedStringValue()
    }

    private fun buildNodeCreateRequest(
        artifactInfo: ArtifactInfo,
        file: ArtifactFile,
        userId: String,
        author: String,
        endTime: Long,
    ): NodeCreateRequest {
        with(artifactInfo) {
            return NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = artifactInfo.getArtifactFullPath(),
                size = file.getSize(),
                sha256 = file.getFileSha256(),
                md5 = file.getFileMd5(),
                crc64ecma = file.getFileCrc64ecma(),
                operator = userId,
                nodeMetadata = listOf(
                    MetadataModel(key = METADATA_KEY_MEDIA_START_TIME, value = startTime, system = true),
                    MetadataModel(key = METADATA_KEY_MEDIA_STOP_TIME, value = endTime, system = true),
                    MetadataModel(key = METADATA_KEY_MEDIA_AUTHOR, value = author, system = true),
                ),
            )
        }
    }

    companion object {
        private const val METADATA_KEY_MEDIA_START_TIME = "media.startTime"
        private const val METADATA_KEY_MEDIA_STOP_TIME = "media.stopTime"
        private const val METADATA_KEY_MEDIA_AUTHOR = "media.author"
        private val logger = LoggerFactory.getLogger(MediaArtifactFileConsumer::class.java)
    }
}
