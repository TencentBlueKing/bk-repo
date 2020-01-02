package com.tencent.bkrepo.maven.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.maven.api.MavenResource
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.service.MavenService
import org.springframework.web.bind.annotation.RestController

@RestController
class MavenResourceImpl(
    private val mavenService: MavenService
) : MavenResource {

    override fun deploy(
        mavenArtifactInfo: MavenArtifactInfo,
        file: ArtifactFile
    ) {
        return mavenService.deploy(mavenArtifactInfo, file)
    }

    override fun dependency(mavenArtifactInfo: MavenArtifactInfo) {
        mavenService.dependency(mavenArtifactInfo)
    }
}
