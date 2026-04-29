package com.tencent.bkrepo.media.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
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
import com.tencent.bkrepo.media.common.dao.MediaActiveStreamDao
import com.tencent.bkrepo.media.common.pojo.stream.MediaStreamRouteInfo
import com.tencent.bkrepo.media.config.MediaProperties
import com.tencent.bkrepo.media.stream.ArtifactFileRecordingListener
import com.tencent.bkrepo.media.stream.ClientStream
import com.tencent.bkrepo.media.stream.MediaArtifactFileConsumer
import com.tencent.bkrepo.media.stream.MediaMod
import com.tencent.bkrepo.media.stream.MediaType
import com.tencent.bkrepo.media.stream.RemuxRecordingListener
import com.tencent.bkrepo.media.stream.StreamManger
import com.tencent.bkrepo.media.stream.StreamMode
import com.tencent.bkrepo.media.stream.TranscodeConfig
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.devops.utils.jackson.readJsonString
import io.swagger.v3.oas.annotations.media.Schema
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.InetAddress
import java.util.Base64

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
    private val mediaActiveStreamDao: MediaActiveStreamDao,
) : ArtifactService() {

    /**
     * 创建推流地址
     * */
    fun createStream(projectId: String, repoName: String, display: Boolean, mediaMod: String?): String {
        val gray = HeaderUtils.getHeader("X-GATEWAY-TAG").toString().equals("gray", true)
                && mediaProperties.grayServerAddress.isNotEmpty()
        val serverAddress = if (gray) {
            mediaProperties.grayServerAddress
        } else {
            mediaProperties.serverAddress
        }
        // 如果是纯直播则不创建节点
        if (mediaMod == MediaMod.LIVE.name) {
            val expireAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000
            val token: String = generateToken("$projectId-${repoName}", expireAt)
            return "$serverAddress/$projectId/$repoName$STREAM_PATH?token=$token"
        }
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
        uploadId: String? = null,
        videoStartTime: Long? = null
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
            nodeService,
            videoStartTime ?: System.currentTimeMillis()
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
        persistedStartTime: Long? = null,
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

        // 从分块元信息推算 author 和视频结束时间
        // storeBlockNode 中 createdBy 存的是 author（真实录制者）
        val author = videoBlocks.first().createdBy
        val sortedBlocks = videoBlocks.sortedBy { it.createdDate }
        // 开始时间优先使用插件侧持久化的精确时间
        val videoStartTime = persistedStartTime
            ?: sortedBlocks.first().createdDate
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val videoEndTime = sortedBlocks.last().createdDate
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 获取转码配置，并将 transcodeExtraParams (sktoken) 设置到配置中
        val transcodeConfig = getTranscodeConfig(projectId)?.copy(extraParams = transcodeExtraParams)
        val fileConsumer = buildFileConsumer(repo, author, transcodeConfig, persistedStartTime)

        // 合并视频主文件分块
        fileConsumer.completeBlockNode(videoArtifactInfo, uploadId, videoEndTime)
        logger.info("MergeExpiredBlocks: video merged for uploadId=$uploadId")

        // 合并额外文件（鼠标轨迹、音频）的分块
        val extraFileSpecs = listOf("CM" to MediaType.JSON, "AU" to MediaType.AAC)
        val extraArtifactInfos = mergeExtraFileBlocks(
            projectId, repoName, uploadId, extraFileSpecs, fileConsumer, videoEndTime
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
     * @param persistedStartTime 插件侧持久化的录屏开始时间（用于重连场景恢复首次连接时间）
     */
    private fun buildFileConsumer(
        repo: com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail,
        author: String,
        transcodeConfig: TranscodeConfig?,
        persistedStartTime: Long? = null
    ): MediaArtifactFileConsumer {
        return MediaArtifactFileConsumer(
            storageManager, transcodeService, repo, author, author,
            STREAM_PATH, transcodeConfig, storageService, blockNodeService, nodeService,
            persistedStartTime ?: System.currentTimeMillis()
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
        fileConsumer: MediaArtifactFileConsumer,
        endTime: Long
    ): List<ArtifactInfo> {
        return extraFileSpecs.mapNotNull { (prefix, mediaType) ->
            val artifactInfo = buildArtifactInfo(projectId, repoName, uploadId, mediaType, prefix)
            val extraUploadId = "${uploadId}_${prefix}_$uploadId"
            val blocks = blockNodeService.listBlocksInUploadId(
                projectId, repoName, artifactInfo.getArtifactFullPath(), extraUploadId
            )
            if (blocks.isNotEmpty()) {
                fileConsumer.completeBlockNode(artifactInfo, extraUploadId, endTime)
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

    fun fetchRtc(
        projectId: String,
        repoName: String,
        resolution: String
    ): String? {
        val streamId = "$projectId-${repoName}_$resolution"
        if (getActiveStreamRoute(streamId) == null) {
            logger.warn("rtc stream not found, streamId=$streamId")
            return null
        }
        // 5 分钟有效
        val expireAt = System.currentTimeMillis() + 300000
        val token: String = generateToken(streamId, expireAt)
        return "${mediaProperties.repoHost}/rtc/v1/whep/?app=live&stream=${streamId}&token=$token"
    }

    /**
     * 生成 token，格式: {expireAt}.{base64url(hmacBytes)}，~57 字符
     * streamPattern 不嵌入 token（由 URL 路径携带），HMAC 中已绑定
     */
    fun generateToken(streamPattern: String?, expireAt: Long): String {
        val payload = "$streamPattern|$expireAt"
        val hmacBytes = HmacUtils(HmacAlgorithms.HMAC_SHA_256, mediaProperties.rtcSecret).hmac(payload)
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes)
        return "$expireAt.$signature"
    }

    /**
     * 校验 token，兼容新旧两种格式：
     * - 新格式: {expireAt}.{base64url(hmacBytes)}
     * - 旧格式: base64( streamPattern|expireAt.hmacHex )
     */
    fun verifyToken(token: String?, requestedStream: String): Boolean {
        if (token.isNullOrBlank()) {
            return false
        }
        val newResult = try {
            verifyNewToken(token, requestedStream)
        } catch (_: Exception) {
            false
        }
        if (newResult) return true
        return try {
            verifyLegacyToken(token, requestedStream)
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyNewToken(token: String, requestedStream: String): Boolean {
        val dotIndex = token.indexOf('.')
        if (dotIndex <= 0 || dotIndex >= token.length - 1) return false
        val expireStr = token.substring(0, dotIndex)
        if (!expireStr.all { it.isDigit() }) return false
        val expireAt = expireStr.toLong()
        val signature = token.substring(dotIndex + 1)
        val payload = "$requestedStream|$expireAt"
        val hmacBytes = HmacUtils(HmacAlgorithms.HMAC_SHA_256, mediaProperties.rtcSecret).hmac(payload)
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes)
        return expected == signature && System.currentTimeMillis() <= expireAt
    }

    private fun verifyLegacyToken(token: String, requestedStream: String): Boolean {
        val decoded = String(Base64.getUrlDecoder().decode(token))
        val parts = decoded.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val payload = parts.getOrNull(0) ?: return false
        val signature = parts.getOrNull(1) ?: return false
        if (parts.size != 2) return false
        val expected = HmacUtils(HmacAlgorithms.HMAC_SHA_256, mediaProperties.rtcSecret).hmacHex(payload)
        val fields = payload.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val streamPattern = fields[0]
        val expireAt = fields[1].toLong()
        return expected == signature
                && System.currentTimeMillis() <= expireAt
                && requestedStream == streamPattern
    }

    fun saveActiveStream(
        streamId: String,
        machine: String? = null,
        serverId: String? = null,
        app: String? = null,
        vhost: String? = null,
        clientIp: String? = null,
    ) {
        val normalizedStreamId = streamId.trim()
        require(normalizedStreamId.isNotBlank()) { "streamId must not be blank" }
        val resolvedMachine = machine?.trim()?.takeIf { it.isNotBlank() } ?: resolveLocalMachine()
        mediaActiveStreamDao.saveOrUpdate(
            streamId = normalizedStreamId,
            machine = resolvedMachine,
            serverId = serverId,
            app = app,
            vhost = vhost,
            clientIp = clientIp,
        )
        logger.info(
            "Saved active stream: streamId=$normalizedStreamId, machine=$resolvedMachine, " +
                    "serverId=$serverId, app=$app, vhost=$vhost"
        )
    }

    fun deleteActiveStream(streamId: String): Boolean {
        val normalizedStreamId = streamId.trim()
        require(normalizedStreamId.isNotBlank()) { "streamId must not be blank" }
        val result = mediaActiveStreamDao.deleteByStreamId(normalizedStreamId)
        if (result.deletedCount > 0) {
            logger.info("Deleted active stream: streamId=$normalizedStreamId")
            return true
        }
        logger.info("Active stream already absent: streamId=$normalizedStreamId")
        return false
    }

    fun getActiveStreamRoute(streamId: String): MediaStreamRouteInfo? {
        val normalizedStreamId = streamId.trim()
        require(normalizedStreamId.isNotBlank()) { "streamId must not be blank" }
        return mediaActiveStreamDao.findByStreamId(normalizedStreamId)?.let {
            MediaStreamRouteInfo(
                streamId = it.streamId,
                machine = it.machine,
                serverId = it.serverId,
            )
        }
    }

    private fun resolveLocalMachine(): String {
        return runCatching { InetAddress.getLocalHost().hostAddress }
            .onFailure { logger.warn("Resolve local machine failed.", it) }
            .getOrDefault("unknown")
    }

    fun checkUserWorkspaceLivePerm(projectId: String, workspaceName: String, userId: String): Boolean {
        val devops = mediaProperties.plugin.devx.devops
        val requestUrl = "${mediaProperties.remoteDevHost}/apigw-app/remotedev/check_view_live" +
                "?userId=$userId&projectId=$projectId&workspaceName=$workspaceName"
        val request = Request.Builder()
            .url(requestUrl)
            .header(
                BK_API_AUTH_HEADER,
                """{"bk_app_code": "${devops.appCode}", "bk_app_secret": "${devops.appSecret}"}"""
            )
            .header(X_DEVOPS_UID, userId)
            .build()
        val response = executeWithRetry(request, "checkUserWorkspaceLivePerm") ?: return false
        return response.use {
            if (it.code != 200) {
                logger.error(
                    "checkUserWorkspaceLivePerm request[$requestUrl]resp[${it.code}]: ${it.body?.string()}"
                )
                false
            } else {
                val resp = it.body!!.string().readJsonString<DevopsResult<Boolean>>().data
                logger.debug("checkUserWorkspaceLivePerm response[$resp]")
                resp == true
            }
        }
    }

    private fun executeWithRetry(
        request: Request,
        operationName: String,
        maxRetries: Int = MAX_RETRIES,
    ): Response? {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val response = httpClient.newCall(request).execute()
                if (response.code == 200 || attempt == maxRetries) {
                    return response
                }
                val body = response.body?.string()
                response.close()
                logger.warn(
                    "$operationName attempt $attempt/$maxRetries failed: " +
                            "HTTP ${response.code}, body=$body"
                )
            } catch (e: IOException) {
                lastException = e
                logger.warn("$operationName attempt $attempt/$maxRetries IOException: ${e.message}")
            }
            if (attempt < maxRetries) {
                val backoffMs = RETRY_BASE_DELAY_MS * (1L shl (attempt - 1))
                Thread.sleep(backoffMs)
            }
        }
        logger.error("$operationName failed after $maxRetries attempts", lastException)
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StreamService::class.java)
        private const val DEFAULT = "default"
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 1000L

        private val httpClient = HttpClientBuilderFactory.create().build()
        private const val X_DEVOPS_UID = "X-DEVOPS-UID"
        private const val BK_API_AUTH_HEADER = "X-Bkapi-Authorization"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class DevopsResult<out T>(
    @get:Schema(title = "返回码")
    val status: Int,
    @get:Schema(title = "错误信息")
    val message: String? = null,
    @get:Schema(title = "数据")
    val data: T? = null
)
