package com.tencent.bkrepo.generic.api

import com.tencent.bkrepo.common.api.annotation.WildcardParam
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.pojo.artifactory.JfrogFilesData
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Api("蓝盾适配接口")
@RequestMapping("/artifactory")
interface ArtifactoryResource {
    @ApiOperation("上传文件")
    @PutMapping("/{projectId}/{repoName}/**")
    fun upload(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目id", required = true)
        @PathVariable
        projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam(hidden = true)
        @WildcardParam
        fullPath: String,
        request: HttpServletRequest
    ): Response<Void>

    @ApiOperation("下载文件")
    @GetMapping("/{projectId}/{repoName}/**")
    fun download(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目id", required = true)
        @PathVariable
        projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam(hidden = true)
        @WildcardParam
        fullPath: String,
        response: HttpServletResponse
    )

    @ApiOperation("listFile")
    @GetMapping("/api/storage/{projectId}/{repoName}/**")
    fun listFile(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目id", required = true)
        @PathVariable
        projectId: String,
        @ApiParam("仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam(hidden = true)
        @WildcardParam
        fullPath: String,
        response: HttpServletResponse
    ): JfrogFilesData
}
