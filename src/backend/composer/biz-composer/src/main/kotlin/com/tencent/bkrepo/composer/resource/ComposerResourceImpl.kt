package com.tencent.bkrepo.composer.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.composer.api.ComposerResource
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import com.tencent.bkrepo.composer.service.ComposerService
import org.apache.http.HttpStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ComposerResourceImpl(
        @Autowired
        private val composerService: ComposerService
): ComposerResource{
    override fun installRequire(composerArtifactInfo: ComposerArtifactInfo) {
        composerService.installRequire(composerArtifactInfo)
    }

    override fun packages(composerArtifactInfo: ComposerArtifactInfo) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=UTF-8"
        response.writer.print(composerService.packages(composerArtifactInfo))
    }

    override fun getJson(composerArtifactInfo: ComposerArtifactInfo) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=UTF-8"
        if (composerService.getJson(composerArtifactInfo) != null) {
            response.writer.print (composerService.getJson(composerArtifactInfo))
        } else {
            response.status = HttpStatus.SC_NOT_FOUND
        }
    }

    override fun deploy(composerArtifactInfo: ComposerArtifactInfo, file: ArtifactFile) {
        composerService.deploy(composerArtifactInfo, file)
    }
}