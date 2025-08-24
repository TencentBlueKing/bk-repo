package com.tencent.bkrepo.media.service

import com.tencent.bkrepo.auth.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.metadata.service.metadata.MetadataService
import com.tencent.bkrepo.media.artifact.MediaArtifactInfo
import com.tencent.bkrepo.media.config.MediaProperties
import com.tencent.bkrepo.media.dao.MediaTranscodeJobDao
import com.tencent.bkrepo.media.model.TMediaTranscodeJob
import com.tencent.bkrepo.media.pojo.transcode.MediaTranscodeJobStatus
import com.tencent.bkrepo.media.pojo.transcode.TranscodeReportData
import com.tencent.bkrepo.media.stream.TranscodeConfig
import com.tencent.bkrepo.media.stream.TranscodeParam
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime

/**
 * 视频转码服务
 * */
@Service
class TranscodeService(
    private val tokenService: TokenService,
    private val mediaProperties: MediaProperties,
    private val metadataService: MetadataService,
    private val mediaTranscodeJobDao: MediaTranscodeJobDao
) :
    ArtifactService() {

    /**
     * 视频转码
     * */
    fun transcode(
        artifactInfo: ArtifactInfo,
        transcodeConfig: TranscodeConfig,
        userId: String,
        extraFiles: List<ArtifactInfo>?,
    ) {
        val transcodeParam = generateTranscodeParam(artifactInfo, transcodeConfig, userId)
        transcodeParam.extraFiles = extraFiles?.map {
            val p = generateTranscodeParam(it, transcodeConfig, userId)
            mapOf(
                "inputFileName" to p.inputFileName,
                "inputUrl" to p.inputUrl
            )
        }
        logger.info(
            "add transcode task for artifact[$artifactInfo][$extraFiles],param[${transcodeParam.toJsonString()}]"
        )
        mediaTranscodeJobDao.insert(
            TMediaTranscodeJob(
                id = null,
                projectId = artifactInfo.projectId,
                repoName = artifactInfo.repoName,
                fileName = artifactInfo.getResponseName(),
                status = MediaTranscodeJobStatus.QUEUE,
                param = transcodeParam.toJsonString(),
                createdTime = LocalDateTime.now(),
                updateTime = LocalDateTime.now()
            )
        )
    }

    /**
     * 下载视频
     * */
    fun download(artifactInfo: MediaArtifactInfo) {
        with(artifactInfo) {
            val repo = ArtifactContextHolder.getRepoDetail()
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            val context = ArtifactDownloadContext(repo, artifactInfo)
            repository.download(context)
        }
    }

    /**
     * 转码成功后回调
     * 删除原视频，保留转码后的视频
     * */
    fun transcodeCallback(
        newArtifactInfo: MediaArtifactInfo,
        newArtifactFile: ArtifactFile,
        originArtifactInfo: MediaArtifactInfo,
    ) {
        with(newArtifactInfo) {
            val repo = ArtifactContextHolder.getRepoDetail()
                ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
            logger.info("File[$originArtifactInfo] transcode successful")
            val context = ArtifactUploadContext(repo, newArtifactFile, newArtifactInfo)
            repository.upload(context)
            logger.info("Upload new file[$newArtifactInfo]")
            // 复制原有视频的metadata
            val originMetadata =
                metadataService.listMetadata(projectId, repoName, originArtifactInfo.getArtifactFullPath())
            val copyRequest = MetadataSaveRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = newArtifactInfo.getArtifactFullPath(),
                metadata = originMetadata,
            )
            metadataService.saveMetadata(copyRequest)
            val removeContext = ArtifactRemoveContext(repo, originArtifactInfo)
            repository.remove(removeContext)
            logger.info("Delete origin file[$originArtifactInfo]")
        }
    }

    /**
     * 上报转码任务状态和监控信息
     */
    fun jobReport(artifactInfo: ArtifactInfo, data: TranscodeReportData) {
        mediaTranscodeJobDao.updateStatus(
            projectId = artifactInfo.repoName,
            repoName = artifactInfo.projectId,
            fileName = artifactInfo.getArtifactName(),
            status = data.status,
        )
    }

    private fun generateTranscodeParam(
        artifactInfo: ArtifactInfo,
        transcodeConfig: TranscodeConfig,
        userId: String,
    ): TranscodeParam {
        with(transcodeConfig) {
            val outputArtifactInfo = covertOutputArtifactInfo(artifactInfo, scale)
            val (inputUrl, callbackUrl) = generateUrl(
                artifactInfo,
                outputArtifactInfo,
                mediaProperties.repoHost,
                userId,
            )
            return TranscodeParam(
                inputUrl = inputUrl,
                callbackUrl = callbackUrl,
                scale = scale,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                inputFileName = artifactInfo.getResponseName(),
                outputFileName = outputArtifactInfo.getResponseName(),
                extraParams = transcodeConfig.extraParams.orEmpty(),
                extraFiles = null
            )
        }
    }

    private fun covertOutputArtifactInfo(input: ArtifactInfo, scale: String): ArtifactInfo {
        with(input) {
            val name = getResponseName()
            val (fileName, fileType) = name.split(".")
            val outFileName = "${fileName}_$scale.$fileType"
            val outputFilePath = getArtifactFullPath().replace(name, outFileName)
            return ArtifactInfo(projectId, repoName, outputFilePath)
        }
    }

    private fun generateUrl(
        input: ArtifactInfo,
        output: ArtifactInfo,
        host: String,
        userId: String,
    ): Pair<String, String> {
        val inputToken = createAccessToken(input, userId)
        val outputToken = createAccessToken(output, userId)
        val downloadUrl = "$host/media/user/transcode/download$input?token=$inputToken"
        val callbackUrl = "$host/media/user/transcode/upload$output?token=$outputToken" +
                "&origin=${input.getArtifactFullPath()}&originToken=$inputToken"
        return Pair(downloadUrl, callbackUrl)
    }

    private fun createAccessToken(artifactInfo: ArtifactInfo, userId: String): String {
        with(artifactInfo) {
            val tokenRequest = TemporaryTokenCreateRequest(
                projectId = projectId,
                repoName = repoName,
                fullPathSet = setOf(getArtifactFullPath()),
                type = TokenType.ALL,
                createdBy = userId,
                expireSeconds = Duration.ofDays(7).seconds,
            )
            return tokenService.createToken(tokenRequest).firstOrNull().orEmpty()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TranscodeService::class.java)
    }
}
