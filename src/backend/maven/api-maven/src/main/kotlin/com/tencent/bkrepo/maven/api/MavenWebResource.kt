package com.tencent.bkrepo.maven.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.GetMapping

@Api("Maven 产品接口")
@RequestMapping("/ext")
interface MavenWebResource {
    @ApiOperation("maven jar 包删除接口")
    @DeleteMapping(MavenArtifactInfo.MAVEN_EXT_PACKAGE_DELETE)
    fun deletePackage(
        @ArtifactPathVariable mavenArtifactInfo: MavenArtifactInfo,
        @RequestParam packageKey: String
    ): Response<String>

    @ApiOperation("maven jar 包删除接口")
    @DeleteMapping(MavenArtifactInfo.MAVEN_EXT_VERSION_DELETE)
    fun deleteVersion(
            @ArtifactPathVariable mavenArtifactInfo: MavenArtifactInfo,
            @RequestParam packageKey: String,
            @RequestParam version: String?
    ): Response<String>

    @ApiOperation("maven jar 版本详情接口")
    @GetMapping(MavenArtifactInfo.MAVEN_EXT_DETAIL)
    fun artifactDetail(@ArtifactPathVariable mavenArtifactInfo: MavenArtifactInfo): Response<Any?>
}
