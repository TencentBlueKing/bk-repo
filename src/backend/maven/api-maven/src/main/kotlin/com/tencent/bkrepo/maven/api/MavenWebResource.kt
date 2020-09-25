package com.tencent.bkrepo.maven.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Api("Maven 产品接口")
@RequestMapping("/api")
interface MavenWebResource {
    @ApiOperation("maven jar 包删除接口")
    @DeleteMapping(MavenArtifactInfo.MAVEN_MAPPING_URI)
    fun delete(@ArtifactPathVariable mavenArtifactInfo: MavenArtifactInfo): Response<String>

    @Deprecated("")
    @ApiOperation("")
    @GetMapping(MavenArtifactInfo.MAVEN_MAPPING_URI)
    fun listVersion(@ArtifactPathVariable mavenArtifactInfo: MavenArtifactInfo): Response<List<Any>>

    @ApiOperation("maven jar 版本详情接口")
    @GetMapping(MavenArtifactInfo.MAVEN_ARTIFACT_DETAIL)
    fun artifactDetail(@ArtifactPathVariable mavenArtifactInfo: MavenArtifactInfo): Response<Any?>
}
