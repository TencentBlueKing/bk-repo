package com.tencent.bkrepo.generic.api

import com.tencent.bkrepo.common.api.annotation.WildcardParam
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.pojo.artifactory.JfrogFilesData
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Api("蓝盾适配接口")
@RequestMapping("/artifactory")
interface ArtifactoryResource {
    @ApiOperation("上传文件")
    @PutMapping("/{projectId}/{repoName}/**")
    fun upload(
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
