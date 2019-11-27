package com.tencent.bkrepo.generic.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable.Companion.ARTIFACT_COORDINATE_URI
import com.tencent.bkrepo.generic.pojo.BlockInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestParam

/**
 * 下载接口
 *
 * @author: carrypan
 * @date: 2019-09-28
 */
@Api("下载接口")
interface DownloadResource {

    @ApiOperation("简单下载")
    @GetMapping(ARTIFACT_COORDINATE_URI)
    fun simpleDownload(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo
    )

    @ApiOperation("分块下载")
    @GetMapping("/block/$ARTIFACT_COORDINATE_URI")
    fun blockDownload(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo,
        @ApiParam("分块序号", required = true)
        @RequestParam("sequence")
        sequence: Int
    )

    @ApiOperation("查询分块信息")
    @GetMapping("/block/list/$ARTIFACT_COORDINATE_URI")
    fun getBlockList(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable
        artifactInfo: ArtifactInfo
    ): Response<List<BlockInfo>>

}
