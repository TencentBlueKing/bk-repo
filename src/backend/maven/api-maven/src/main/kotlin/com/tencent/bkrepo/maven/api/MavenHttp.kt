package com.tencent.bkrepo.maven.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo.Companion.MAVEN_MAPPING_URI
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Api("maven http协议接口")
@RequestMapping
interface MavenHttp {

    @ApiOperation("deploy and checksum")
    @PutMapping(MAVEN_MAPPING_URI)
    fun deploy(@ArtifactPathVariable
               mavenArtifactInfo: MavenArtifactInfo,
               file: ArtifactFile
    )

    @ApiOperation("dependency")
    @GetMapping(MAVEN_MAPPING_URI)
    fun dependency(@ArtifactPathVariable
                   mavenArtifactInfo: MavenArtifactInfo
    )

}