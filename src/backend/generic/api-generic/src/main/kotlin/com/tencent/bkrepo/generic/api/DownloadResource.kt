package com.tencent.bkrepo.generic.api

import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactCoordinate
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo.Companion.ARTIFACT_COORDINATE_URI
import com.tencent.bkrepo.generic.pojo.BlockInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 下载接口
 *
 * @author: carrypan
 * @date: 2019-09-28
 */
@Api("下载接口")
@RequestMapping("/download")
interface DownloadResource {

    @ApiOperation("简单下载")
    @GetMapping("/simple/$ARTIFACT_COORDINATE_URI")
    fun simpleDownload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ArtifactInfo
        artifactCoordinate: ArtifactCoordinate,
        request: HttpServletRequest,
        response: HttpServletResponse
    )

    @ApiOperation("分块下载")
    @GetMapping("/block/$ARTIFACT_COORDINATE_URI")
    fun blockDownload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ArtifactInfo
        artifactCoordinate: ArtifactCoordinate,
        @ApiParam("分块序号", required = true)
        @RequestParam("sequence")
        sequence: Int,
        request: HttpServletRequest,
        response: HttpServletResponse
    )

    @ApiOperation("查询分块信息")
    @GetMapping("/info/$ARTIFACT_COORDINATE_URI")
    fun queryBlockInfo(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ArtifactInfo
        artifactCoordinate: ArtifactCoordinate
    ): Response<List<BlockInfo>>
}
