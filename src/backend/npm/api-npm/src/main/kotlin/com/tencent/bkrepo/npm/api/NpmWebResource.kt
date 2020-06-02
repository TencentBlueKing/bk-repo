package com.tencent.bkrepo.npm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.PackageInfoResponse
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Api("npm web 接口")
@RequestMapping("/api/web")
interface NpmWebResource {
    @ApiOperation("查询包的相信信息")
    @GetMapping("/query/{projectId}/{repoName}/*")
    fun getPackageInfo(@ArtifactPathVariable artifactInfo: NpmArtifactInfo): PackageInfoResponse
}
