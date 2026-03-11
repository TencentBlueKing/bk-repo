package com.tencent.bkrepo.media.controller

import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.media.artifact.MediaArtifactInfo
import com.tencent.bkrepo.media.artifact.MediaArtifactInfo.Companion.DEFAULT_STREAM_MAPPING_URI
import com.tencent.bkrepo.media.service.TokenService
import com.tencent.bkrepo.media.service.TranscodeService
import com.tencent.bkrepo.media.stream.TranscodeConfig
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 转码资源控制器
 * 用于临时下载文件，和上传转码后的文件
 * */
@RestController
@RequestMapping("/user/transcode")
class TranscodeController(
    private val tokenService: TokenService,
    private val transcodeService: TranscodeService,
) {
    /**
     * 下载源文件
     * @param artifactInfo 要下载的源文件
     * @param token 下载token
     * */
    @GetMapping("/download/$DEFAULT_STREAM_MAPPING_URI")
    fun download(
        artifactInfo: MediaArtifactInfo,
        @RequestParam token: String,
    ) {
        val tokenInfo = tokenService.validateToken(token, artifactInfo, TokenType.DOWNLOAD)
        transcodeService.download(artifactInfo)
        tokenService.decrementPermits(tokenInfo)
    }

    /**
     * 回传转码后文件
     * @param artifactInfo 转码后的文件信息
     * @param file 转码后的文件
     * @param token 上传token
     * @param origin 转码源文件完整路径
     * */
    @PutMapping("/upload/$DEFAULT_STREAM_MAPPING_URI")
    fun callback(
        artifactInfo: MediaArtifactInfo,
        file: ArtifactFile,
        @RequestParam token: String,
        @RequestParam origin: String,
        @RequestParam originToken: String,
    ) {
        val tokenInfo = tokenService.validateToken(token, artifactInfo, TokenType.UPLOAD)
        val originArtifactInfo = MediaArtifactInfo(artifactInfo.projectId, artifactInfo.repoName, origin)
        val originTokenInfo = tokenService.validateToken(originToken, originArtifactInfo, TokenType.UPLOAD)
        transcodeService.transcodeCallback(artifactInfo, file, originArtifactInfo)
        tokenService.decrementPermits(tokenInfo)
        tokenService.decrementPermits(originTokenInfo)
    }

    /**
     * 视频转码
     * @param artifactInfo 待转码文件
     * @param transcodeConfig 转码配置
     * */
    @PutMapping(DEFAULT_STREAM_MAPPING_URI)
    fun transcode(
        artifactInfo: MediaArtifactInfo,
        @RequestAttribute userId: String,
        @RequestBody transcodeConfig: TranscodeConfig,
    ) {
        transcodeService.transcode(artifactInfo, transcodeConfig, userId, null, null, null, null)
    }
}
