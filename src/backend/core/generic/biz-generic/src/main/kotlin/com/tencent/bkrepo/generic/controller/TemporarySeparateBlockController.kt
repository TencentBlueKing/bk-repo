package com.tencent.bkrepo.generic.controller

import com.tencent.bkrepo.auth.pojo.token.TokenType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.SEPARATE_MAPPING_URI
import com.tencent.bkrepo.generic.constant.HEADER_UPLOAD_ID
import com.tencent.bkrepo.generic.constant.HEADER_UPLOAD_TYPE
import com.tencent.bkrepo.generic.constant.SEPARATE_UPLOAD
import com.tencent.bkrepo.generic.pojo.SeparateBlockInfo
import com.tencent.bkrepo.generic.pojo.UploadTransactionInfo
import com.tencent.bkrepo.generic.service.TemporaryAccessService
import com.tencent.bkrepo.generic.service.UploadService
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/temporary")
class TemporarySeparateBlockController(
    private val temporaryAccessService: TemporaryAccessService,
    private val uploadService: UploadService,
) {

    @PostMapping(SEPARATE_MAPPING_URI)
    fun start(
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
        @RequestParam token: String
    ): Response<UploadTransactionInfo> {
        temporaryAccessService.validateToken(token, artifactInfo, TokenType.UPLOAD)
        return ResponseBuilder.success(uploadService.startSeparateBlockUpload(SecurityUtils.getUserId(), artifactInfo))
    }

    @CrossOrigin
    @PutMapping("/upload/$SEPARATE_MAPPING_URI", headers = ["$HEADER_UPLOAD_TYPE=$SEPARATE_UPLOAD"])
    fun upload(
        artifactInfo: GenericArtifactInfo,
        file: ArtifactFile,
        @RequestParam token: String
    ) {
        temporaryAccessService.validateToken(token, artifactInfo, TokenType.UPLOAD)
        temporaryAccessService.upload(artifactInfo, file)
    }


    @DeleteMapping(SEPARATE_MAPPING_URI)
    fun abortBlockUploadWithToken(
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
        @RequestParam token: String
    ): Response<Void> {
        temporaryAccessService.validateToken(token, artifactInfo, TokenType.UPLOAD)
        uploadService.abortSeparateBlockUpload(SecurityUtils.getUserId(), uploadId, artifactInfo)
        return ResponseBuilder.success()
    }

    @PutMapping(SEPARATE_MAPPING_URI)
    fun completeBlockUploadWithToken(
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
        @RequestParam token: String
    ): Response<Void> {
        val tokenInfo = temporaryAccessService.validateToken(token, artifactInfo, TokenType.UPLOAD)
        uploadService.completeSeparateBlockUpload(SecurityUtils.getUserId(), uploadId, artifactInfo)
        temporaryAccessService.decrementPermits(tokenInfo)
        return ResponseBuilder.success()
    }

    @GetMapping(SEPARATE_MAPPING_URI)
    fun listBlockWithToken(
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo,
        @RequestParam token: String
    ): Response<List<SeparateBlockInfo>> {
        temporaryAccessService.validateToken(token, artifactInfo, TokenType.UPLOAD)
        return ResponseBuilder.success(
            uploadService.separateListBlock(SecurityUtils.getUserId(), uploadId, artifactInfo)
        )
    }
}
