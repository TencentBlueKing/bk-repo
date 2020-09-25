package com.tencent.bkrepo.maven.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.maven.api.MavenWebResource
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.service.MavenService
import org.springframework.web.bind.annotation.RestController

@RestController
class MavenWebController(
    private val mavenService: MavenService
) : MavenWebResource {
    override fun delete(
        mavenArtifactInfo: MavenArtifactInfo,
        packageKey: String,
        version: String
    ): Response<String> {
        return ResponseBuilder.success(mavenService.delete(mavenArtifactInfo, packageKey, version))
    }

    override fun artifactDetail(mavenArtifactInfo: MavenArtifactInfo): Response<Any?> {
        return ResponseBuilder.success(mavenService.artifactDetail(mavenArtifactInfo))
    }
}
