package com.tencent.bkrepo.maven.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.maven.api.MavenWebResource
import com.tencent.bkrepo.maven.artifact.MavenArtifactInfo
import com.tencent.bkrepo.maven.service.MavenService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MavenWebController(
        private val mavenService: MavenService
) : MavenWebResource {
    @DeleteMapping(MavenArtifactInfo.MAVEN_MAPPING_URI)
    override fun delete(@ArtifactPathVariable mavenArtifactInfo: MavenArtifactInfo): Response<String> {
        return ResponseBuilder.success(mavenService.delete(mavenArtifactInfo))
    }

    @GetMapping(MavenArtifactInfo.MAVEN_LIST_URI)
    override fun listVersion(mavenArtifactInfo: MavenArtifactInfo): Response<List<Any>> {
        return ResponseBuilder.success(mavenService.listVersion(mavenArtifactInfo))
    }

}
