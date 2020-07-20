package com.tencent.bkrepo.generic.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.BLOCK_MAPPING_URI
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.GENERIC_MAPPING_URI
import com.tencent.bkrepo.generic.constant.HEADER_UPLOAD_ID
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.UploadTransactionInfo
import com.tencent.bkrepo.generic.service.DownloadService
import com.tencent.bkrepo.generic.service.UploadService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class GenericController(
    private val uploadService: UploadService,
    private val downloadService: DownloadService
) {

    @PutMapping(GENERIC_MAPPING_URI)
    fun upload(@ArtifactPathVariable artifactInfo: GenericArtifactInfo, file: ArtifactFile) {
        uploadService.upload(artifactInfo, file)
    }

    @DeleteMapping(GENERIC_MAPPING_URI)
    fun delete(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo) {
        uploadService.delete(userId, artifactInfo)
    }

    @GetMapping(GENERIC_MAPPING_URI)
    fun download(
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo) {
        downloadService.download(artifactInfo)
    }

    @PostMapping(BLOCK_MAPPING_URI)
    fun startBlockUpload(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo
    ): Response<UploadTransactionInfo> {
        return ResponseBuilder.success(uploadService.startBlockUpload(userId, artifactInfo))
    }

    @DeleteMapping(BLOCK_MAPPING_URI)
    fun abortBlockUpload(
        @RequestAttribute userId: String,
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo
    ): Response<Void> {
        uploadService.abortBlockUpload(userId, uploadId, artifactInfo)
        return ResponseBuilder.success()
    }

    @PutMapping(BLOCK_MAPPING_URI)
    fun completeBlockUpload(
        @RequestAttribute userId: String,
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo
    ): Response<Void> {
        uploadService.completeBlockUpload(userId, uploadId, artifactInfo)
        return ResponseBuilder.success()
    }

    @GetMapping(BLOCK_MAPPING_URI)
    fun listBlock(
        @RequestAttribute userId: String,
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo
    ): Response<List<BlockInfo>> {
        return ResponseBuilder.success(uploadService.listBlock(userId, uploadId, artifactInfo))
    }
}