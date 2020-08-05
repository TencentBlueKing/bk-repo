package com.tencent.bkrepo.npm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.fixtool.DateTimeFormatResponse
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping

@Api("npm修复工具")
interface NpmFixToolResource {

    @ApiOperation("修复时间格式工具")
    @GetMapping("/{projectId}/{repoName}/fixDateFormat")
    fun fixDateFormat(
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        pkgName: String
    ): DateTimeFormatResponse
}