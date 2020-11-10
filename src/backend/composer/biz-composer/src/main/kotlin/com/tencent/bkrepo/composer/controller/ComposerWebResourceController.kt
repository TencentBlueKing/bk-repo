package com.tencent.bkrepo.composer.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.composer.api.ComposerWebResource
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import com.tencent.bkrepo.composer.service.ComposerWebService
import org.springframework.web.bind.annotation.RestController

@RestController
class ComposerWebResourceController(
        private val composerWebService: ComposerWebService
) : ComposerWebResource {
    override fun deletePackage(composerArtifactInfo: ComposerArtifactInfo, packageKey: String): Response<Void> {
        composerWebService.deletePackage(composerArtifactInfo, packageKey)
        return ResponseBuilder.success()
    }

    override fun deleteVersion(composerArtifactInfo: ComposerArtifactInfo, packageKey: String, version: String?): Response<Void> {
        composerWebService.delete(composerArtifactInfo, packageKey, version)
        return ResponseBuilder.success()
    }

    override fun artifactDetail(composerArtifactInfo: ComposerArtifactInfo, packageKey: String, version: String?): Response<Any?> {
        return ResponseBuilder.success(composerWebService.artifactDetail(composerArtifactInfo, packageKey, version))
    }
}