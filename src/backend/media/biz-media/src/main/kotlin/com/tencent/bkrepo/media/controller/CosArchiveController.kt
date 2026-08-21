package com.tencent.bkrepo.media.controller

import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.media.artifact.CosArchiveArtifactInfo
import com.tencent.bkrepo.media.artifact.CosArchiveArtifactInfo.Companion.COS_ARCHIVE_MAPPING_URI
import com.tencent.bkrepo.media.service.CosArchiveService
import com.tencent.bkrepo.media.service.TokenService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * COS 加密归档上传/下载。
 * 仓库不存在时上传接口会按项目映射的 storageCredentialsKey 自动创建。
 */
@Tag(name = "媒体 COS 归档")
@RestController
@RequestMapping("/user/cos")
class CosArchiveController(
    private val tokenService: TokenService,
    private val cosArchiveService: CosArchiveService,
) {

    /**
     * 上传转码后的录屏到 COS 归档仓库，需 token。
     * 仓库不存在时自动创建。
     * author/startTime/endTime 为录屏元数据，由 cosUploadUrl 携带，写入归档节点。
     */
    @Operation(summary = "COS 归档上传")
    @PutMapping("/upload/$COS_ARCHIVE_MAPPING_URI")
    fun upload(
        @ArtifactPathVariable artifactInfo: CosArchiveArtifactInfo,
        file: ArtifactFile,
        @RequestParam token: String,
        @RequestParam(required = false) author: String?,
        @RequestParam(required = false) startTime: Long?,
        @RequestParam(required = false) endTime: Long?,
    ) {
        val tokenInfo = tokenService.validateToken(
            token = token,
            artifactInfo = artifactInfo,
            type = TokenType.UPLOAD,
            checkNodePermission = false,
        )
        cosArchiveService.upload(artifactInfo, file, author, startTime, endTime)
        tokenService.decrementPermits(tokenInfo)
    }

    /**
     * 从 COS 归档仓库下载录屏，需 token。存储层按凭证自动解密。
     */
    @Operation(summary = "COS 归档下载")
    @GetMapping("/download/$COS_ARCHIVE_MAPPING_URI")
    fun download(
        @ArtifactPathVariable artifactInfo: CosArchiveArtifactInfo,
        @RequestParam token: String,
    ) {
        val tokenInfo = tokenService.validateToken(
            token = token,
            artifactInfo = artifactInfo,
            type = TokenType.DOWNLOAD,
            checkNodePermission = false,
        )
        cosArchiveService.download(artifactInfo)
        tokenService.decrementPermits(tokenInfo)
    }
}
