package com.tencent.bkrepo.media.service

import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryId
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.service.util.HeaderUtils
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
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
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
                scheduler = scheduler
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
        logger.info("User[$author] publish stream $streamId")
        return stream
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
    ): String {
        val streamPattern = "$projectId-${repoName}_$resolution"
        // 5 分钟有效
        val expireAt = System.currentTimeMillis() + 300000
        val token: String = generateToken(streamPattern, expireAt)
        return "${mediaProperties.repoHost}/rtc/v1/whep/?app=live&stream=${streamPattern}&token=$token"
    }


    private fun generateToken(streamPattern: String?, expireAt: Long): String {
        val payload = "$streamPattern|$expireAt"
        val signature = HmacUtils(HmacAlgorithms.HMAC_SHA_256, RTC_SECRET).hmacHex(payload)
        return Base64.getUrlEncoder().encodeToString(("$payload.$signature").toByteArray())
    }

    fun verifyToken(token: String?, requestedStream: String): Boolean {
        if (token.isNullOrBlank()) {
            return false
        }
        try {
            val decoded = String(Base64.getUrlDecoder().decode(token))
            val parts = decoded.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size != 2) {
                return false
            }
            val payload = parts[0]
            val signature = parts[1]

            val expected: String = HmacUtils(HmacAlgorithms.HMAC_SHA_256, RTC_SECRET).hmacHex(payload)
            if (expected != signature) {
                return false
            }

            val fields = payload.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val streamPattern = fields[0]
            val expireAt = fields[1].toLong()
            if (System.currentTimeMillis() > expireAt) {
                return false
            }
            // 检查流名是否匹配（token 只允许拉对应项目的流）
            if (requestedStream != streamPattern) {
                return false
            }
            return true
        } catch (e: Exception) {
            logger.error("verifyToken error $token|$requestedStream", e)
            return false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StreamService::class.java)
        private const val DEFAULT = "default"
        const val RTC_SECRET: String = "rtc-stream-pull-secret-2m98cx37yr21"
    }
}
