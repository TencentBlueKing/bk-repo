package com.tencent.bkrepo.media.service

import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryId
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.media.STREAM_PATH
import com.tencent.bkrepo.media.artifact.MediaArtifactInfo
import com.tencent.bkrepo.media.config.MediaProperties
import com.tencent.bkrepo.media.stream.ArtifactFileRecordingListener
import com.tencent.bkrepo.media.stream.ClientStream
import com.tencent.bkrepo.media.stream.MediaArtifactFileConsumer
import com.tencent.bkrepo.media.stream.MediaType
import com.tencent.bkrepo.media.stream.RemuxRecordingListener
import com.tencent.bkrepo.media.stream.StreamManger
import com.tencent.bkrepo.media.stream.StreamMode
import com.tencent.bkrepo.media.stream.TranscodeConfig
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service

@Service
class StreamService(
    private val mediaProperties: MediaProperties,
    private val repositoryService: RepositoryService,
    private val nodeService: NodeService,
    private val tokenService: TokenService,
    private val scheduler: ThreadPoolTaskScheduler,
    private val storageManager: StorageManager,
    private val storageProperties: StorageProperties,
    private val streamManger: StreamManger,
    private val transcodeService: TranscodeService,
    private val storageService: StorageService,
    private val blockNodeService: BlockNodeService,
) : ArtifactService() {

    /**
     * 创建推流地址
     * */
    fun createStream(projectId: String, repoName: String, display: Boolean): String {
        /*
        * 1. 创建媒体库
        * 2. 创建streams目录
        * 3. 创建streams目录写权限url =》 推流地址/{projectId}/{pushId}/streams
        * */
        repositoryService.getRepoDetail(projectId, repoName) ?: let {
            val createRepoRequest = RepoCreateRequest(
                projectId = projectId,
                name = repoName,
                type = RepositoryType.MEDIA,
                category = RepositoryCategory.LOCAL,
                public = false,
                display = display,
                storageCredentialsKey = mediaProperties.storageCredentialsKey
            )
            repositoryService.createRepo(createRepoRequest)
            val nodeCreateRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = STREAM_PATH,
                folder = true,
                overwrite = false,
            )
            nodeService.createNode(nodeCreateRequest)
            logger.info("Create media repository for [$repoName].")
        }
        val temporaryTokenRequest = TemporaryTokenCreateRequest(
            projectId = projectId,
            repoName = repoName,
            fullPathSet = setOf(STREAM_PATH),
            type = TokenType.UPLOAD,
        )
        val token = tokenService.createToken(temporaryTokenRequest).firstOrNull()
        val gray = HeaderUtils.getHeader("X-GATEWAY-TAG").toString().equals("gray", true)
                && mediaProperties.grayServerAddress.isNotEmpty()
        val serverAddress = if (gray) {
            mediaProperties.grayServerAddress
        } else {
            mediaProperties.serverAddress
        }
        return "$serverAddress/$projectId/$repoName$STREAM_PATH?token=$token"
    }

    /**
     * 发布流
     * @param projectId 项目Id
     * @param repoName 仓库名
     * @param name 流名称
     * @param mode 流模式
     * @param userId 用户id
     * @param remux 是否封装
     * @param saveType 流保存格式
     * */
    fun publish(
        projectId: String,
        repoName: String,
        name: String,
        mode: StreamMode,
        userId: String,
        author: String,
        remux: Boolean = false,
        saveType: MediaType = MediaType.MP4,
        transcodeExtraParams: String? = null,
        uploadId: String? = null
    ): ClientStream {
        val repoId = RepositoryId(projectId, repoName)
        val repo = ArtifactContextHolder.getRepoDetail(repoId)
        val credentials = repo.storageCredentials ?: storageProperties.defaultStorageCredentials()
        // 只有视频流参与转码
        val transcodeConfig = if (saveType == MediaType.MP4) {
            getTranscodeConfig(projectId)
        } else {
            null
        }
        val streamTranscodeConfig = transcodeConfig?.copy(extraParams = transcodeExtraParams)
        transcodeConfig?.let { it.extraParams = transcodeExtraParams }
        val fileConsumer = MediaArtifactFileConsumer(
            storageManager,
            transcodeService,
            repo,
            userId,
            author,
            STREAM_PATH,
            streamTranscodeConfig,
            storageService,
            blockNodeService,
            nodeService
        )
        val recordingListener = if (remux) {
            RemuxRecordingListener(credentials.upload.location, scheduler, saveType, fileConsumer)
        } else {
            val artifactFile = ArtifactFileFactory.buildChunked(credentials)
            val clientMouseArtifactFile = ArtifactFileFactory.buildChunked(credentials)
            val hostAudioArtifactFile = ArtifactFileFactory.buildChunked(credentials)
            ArtifactFileRecordingListener(
                artifactFile = artifactFile,
                clientMouseArtifactFile = clientMouseArtifactFile,
                hostAudioArtifactFile = hostAudioArtifactFile,
                fileConsumer = fileConsumer,
                scheduler = scheduler,
                uploadId = uploadId,
            )
        }
        val streamId = "$projectId:$repoName:$name"
        val stream = ClientStream(name, streamId, mediaProperties.maxRecordFileSize.toBytes(), recordingListener)
        stream.listeners.add(streamManger)
        stream.start()
        if (mode == StreamMode.RECORD) {
            stream.saveAs()
        }
        stream.startPublish()
        logger.info("User[$author] publish stream $streamId with uploadId=$name")
        return stream
    }

    /**
     * 合并超时未完成的分块（供外部定时任务调用）
     * 当视频流异常退出且没有重连时，分块不会被自动合并
     * @param transcodeExtraParams 转码额外参数（sktoken），为空时转码参数中 extraParams 也为空
     */
    fun mergeExpiredBlocks(
        projectId: String,
        repoName: String,
        uploadId: String,
        transcodeExtraParams: String? = null,
    ) {
        val repo = repositoryService.getRepoDetail(projectId, repoName)
            ?: run {
                logger.warn("MergeExpiredBlocks: repo not found for $projectId/$repoName, skip.")
                return
            }

        // 查询并校验视频主文件分块
        val videoArtifactInfo = buildArtifactInfo(projectId, repoName, uploadId, MediaType.MP4)
        val videoBlocks = blockNodeService.listBlocksInUploadId(
            projectId, repoName, videoArtifactInfo.getArtifactFullPath(), uploadId
        )
        if (videoBlocks.isEmpty()) {
            logger.warn("MergeExpiredBlocks: no video blocks found for uploadId=$uploadId, skip.")
            return
        }

        // 从分块元信息推算 author 和视频起止时间
        // storeBlockNode 中 createdBy 存的是 author（真实录制者）
        val author = videoBlocks.first().createdBy
        val sortedBlocks = videoBlocks.sortedBy { it.createdDate }
        val videoStartTime = sortedBlocks.first().createdDate
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val videoEndTime = sortedBlocks.last().createdDate
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 获取转码配置，并将 transcodeExtraParams (sktoken) 设置到配置中
        val transcodeConfig = getTranscodeConfig(projectId)?.copy(extraParams = transcodeExtraParams)
        val fileConsumer = buildFileConsumer(repo, author, transcodeConfig)

        // 合并视频主文件分块
        fileConsumer.completeBlockNode(videoArtifactInfo, uploadId)
        logger.info("MergeExpiredBlocks: video merged for uploadId=$uploadId")

        // 合并额外文件（鼠标轨迹、音频）的分块
        val extraFileSpecs = listOf("CM" to MediaType.JSON, "AU" to MediaType.AAC)
        val extraArtifactInfos = mergeExtraFileBlocks(
            projectId, repoName, uploadId, extraFileSpecs, fileConsumer
        )

        // 合并完成后触发转码
        if (transcodeConfig != null) {
            transcodeService.transcode(
                artifactInfo = videoArtifactInfo,
                transcodeConfig = transcodeConfig,
                userId = author,
                extraFiles = extraArtifactInfos.ifEmpty { null },
                author = author,
                videoStartTime = videoStartTime,
                videoEndTime = videoEndTime
            )
            logger.info("MergeExpiredBlocks: transcode triggered for uploadId=$uploadId")
        }

        logger.info(
            "MergeExpiredBlocks completed: projectId=$projectId, repoName=$repoName, " +
                "uploadId=$uploadId, author=$author"
        )
    }

    /**
     * 构建 ArtifactInfo，根据 uploadId 和文件类型推算文件路径
     * @param prefix 文件名前缀，如 "CM"、"AU"；为空时表示视频主文件
     */
    private fun buildArtifactInfo(
        projectId: String,
        repoName: String,
        uploadId: String,
        mediaType: MediaType,
        prefix: String? = null
    ): ArtifactInfo {
        val fileName = if (prefix != null) "${prefix}_$uploadId" else uploadId
        val name = "$fileName.${mediaType.name.lowercase(java.util.Locale.getDefault())}"
        return ArtifactInfo(projectId, repoName, "$STREAM_PATH/$name")
    }

    /**
     * 构建 MediaArtifactFileConsumer 实例
     * @param author 真实录制者（从分块 createdBy 获取）
     */
    private fun buildFileConsumer(
        repo: com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail,
        author: String,
        transcodeConfig: TranscodeConfig?
    ): MediaArtifactFileConsumer {
        return MediaArtifactFileConsumer(
            storageManager, transcodeService, repo, author, author,
            STREAM_PATH, transcodeConfig, storageService, blockNodeService, nodeService
        )
    }

    /**
     * 批量合并额外文件（鼠标轨迹、音频等）的分块
     * @param extraFileSpecs 额外文件规格列表，每项为 (前缀, 媒体类型)
     * @return 成功合并的额外文件 ArtifactInfo 列表
     */
    private fun mergeExtraFileBlocks(
        projectId: String,
        repoName: String,
        uploadId: String,
        extraFileSpecs: List<Pair<String, MediaType>>,
        fileConsumer: MediaArtifactFileConsumer
    ): List<ArtifactInfo> {
        return extraFileSpecs.mapNotNull { (prefix, mediaType) ->
            val artifactInfo = buildArtifactInfo(projectId, repoName, uploadId, mediaType, prefix)
            val extraUploadId = "${uploadId}_${prefix}_$uploadId"
            val blocks = blockNodeService.listBlocksInUploadId(
                projectId, repoName, artifactInfo.getArtifactFullPath(), extraUploadId
            )
            if (blocks.isNotEmpty()) {
                fileConsumer.completeBlockNode(artifactInfo, extraUploadId)
                logger.info("MergeExpiredBlocks: $prefix file merged for uploadId=$uploadId")
                artifactInfo
            } else {
                null
            }
        }
    }

    fun download(artifactInfo: MediaArtifactInfo) {
        val context = ArtifactDownloadContext()
        repository.download(context)
    }

    private fun getTranscodeConfig(projectId: String): TranscodeConfig? {
        val transcodeConfig = mediaProperties.transcodeConfig
        return transcodeConfig[projectId] ?: transcodeConfig[DEFAULT]
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StreamService::class.java)
        private const val DEFAULT = "default"
    }
}
