package com.tencent.bkrepo.maven.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.maven.api.MavenHttp
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.service.MavenHttpService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class MavenHttpImpl(
        private val mavenHttpService: MavenHttpService
): MavenHttp{

    companion object {
        val logger: Logger = LoggerFactory.getLogger(MavenHttpImpl::class.java)
    }

    override fun deploy(mavenArtifactInfo: MavenArtifactInfo,
                        file: ArtifactFile)
    {
        return mavenHttpService.deploy(mavenArtifactInfo, file)
    }

    override fun dependency(mavenArtifactInfo: MavenArtifactInfo)
    {
        mavenHttpService.dependency(mavenArtifactInfo)
    }
}