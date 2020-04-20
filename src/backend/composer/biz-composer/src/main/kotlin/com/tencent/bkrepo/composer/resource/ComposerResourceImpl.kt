package com.tencent.bkrepo.composer.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.composer.api.ComposerResource
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import com.tencent.bkrepo.composer.service.ComposerService
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
        composerService.installRequire(composerArtifactInfo)
    }

    override fun query(composerArtifactInfo: ComposerArtifactInfo) {
        TODO("Not yet implemented")
    }

    override fun deploy(composerArtifactInfo: ComposerArtifactInfo, file: ArtifactFile) {
        composerService.deploy(composerArtifactInfo, file)
    }


}