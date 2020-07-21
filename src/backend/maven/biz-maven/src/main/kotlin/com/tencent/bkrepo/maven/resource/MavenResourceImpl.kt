package com.tencent.bkrepo.maven.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.service.MavenService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MavenResourceImpl(
    private val mavenService: MavenService
) {

    @PutMapping(MavenArtifactInfo.MAVEN_MAPPING_URI)
    fun deploy(
        @ArtifactPathVariable
        mavenArtifactInfo: MavenArtifactInfo,
        file: ArtifactFile
    ) {
        return mavenService.deploy(mavenArtifactInfo, file)
    }

    @GetMapping(MavenArtifactInfo.MAVEN_MAPPING_URI)
    fun dependency(@ArtifactPathVariable mavenArtifactInfo: MavenArtifactInfo) {
        mavenService.dependency(mavenArtifactInfo)
    }
}
