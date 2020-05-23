package com.tencent.bkrepo.generic.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.BLOCK_MAPPING_URI
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo.Companion.GENERIC_MAPPING_URI
import com.tencent.bkrepo.generic.constant.HEADER_UPLOAD_ID
import com.tencent.bkrepo.generic.pojo.BlockInfo
import com.tencent.bkrepo.generic.pojo.UploadTransactionInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestHeader

/**
 * 上传接口
 *
 * @author: carrypan
 * @date: 2019-09-27
 */
@Api("上传接口")
interface UploadResource {

    @ApiOperation("上传")
    @PutMapping(GENERIC_MAPPING_URI)
    fun upload(@ArtifactPathVariable artifactInfo: GenericArtifactInfo, file: ArtifactFile)

    @ApiOperation("开启分块上传")
    @PostMapping(BLOCK_MAPPING_URI)
    fun startBlockUpload(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo
    ): Response<UploadTransactionInfo>

    @ApiOperation("取消分块上传")
    @DeleteMapping(BLOCK_MAPPING_URI)
    fun abortBlockUpload(
        @RequestAttribute userId: String,
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo
    ): Response<Void>

    @ApiOperation("完成分块上传")
    @PutMapping(BLOCK_MAPPING_URI)
    fun completeBlockUpload(
        @RequestAttribute userId: String,
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo
    ): Response<Void>

    @ApiOperation("查询上传分块")
    @GetMapping(BLOCK_MAPPING_URI)
    fun listBlock(
        @RequestAttribute userId: String,
        @RequestHeader(HEADER_UPLOAD_ID) uploadId: String,
        @ArtifactPathVariable artifactInfo: GenericArtifactInfo
    ): Response<List<BlockInfo>>

    @ApiOperation("手动补偿文件落地")
    @GetMapping("/retry/{sha256}")
    fun retry(
        @RequestAttribute userId: String,
        @PathVariable sha256: String
    ): Response<Void>
}
