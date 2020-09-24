package com.tencent.bkrepo.maven.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping

interface MavenWebResource {
    @DeleteMapping(MavenArtifactInfo.MAVEN_MAPPING_URI)
    fun delete(@ArtifactPathVariable mavenArtifactInfo: MavenArtifactInfo): Response<String>

    @GetMapping(MavenArtifactInfo.MAVEN_MAPPING_URI)
    fun listVersion(@ArtifactPathVariable mavenArtifactInfo: MavenArtifactInfo): Response<List<Any>>

}
