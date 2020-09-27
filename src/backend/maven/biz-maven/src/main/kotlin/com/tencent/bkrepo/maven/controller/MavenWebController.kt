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

    override fun deletePackage(mavenArtifactInfo: MavenArtifactInfo, packageKey: String): Response<Void> {
        mavenService.delete(mavenArtifactInfo, packageKey, null)
        return ResponseBuilder.success()
    }

    override fun deleteVersion(
        mavenArtifactInfo: MavenArtifactInfo,
        packageKey: String,
        version: String?
    ): Response<Void> {
        mavenService.delete(mavenArtifactInfo, packageKey, version)
        return ResponseBuilder.success()
    }

    override fun artifactDetail(
        mavenArtifactInfo: MavenArtifactInfo,
        packageKey: String,
        version: String?
    ): Response<Any?> {
        return ResponseBuilder.success(mavenService.artifactDetail(mavenArtifactInfo, packageKey, version))
    }
}
