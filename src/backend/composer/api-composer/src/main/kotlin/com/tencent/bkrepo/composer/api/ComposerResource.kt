package com.tencent.bkrepo.composer.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo.Companion.COMPOSER_DEPLOY
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo.Companion.COMPOSER_INSTALL
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo.Companion.COMPOSER_JSON
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo.Companion.COMPOSER_PACKAGES
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping

@Api("composer http协议接口")
interface ComposerResource {

    @ApiOperation("install")
    @GetMapping(COMPOSER_INSTALL)
    fun installRequire(
        @ArtifactPathVariable composerArtifactInfo: ComposerArtifactInfo
    )

    @ApiOperation("packages.json")
    @GetMapping(COMPOSER_PACKAGES, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun packages(@ArtifactPathVariable composerArtifactInfo: ComposerArtifactInfo)

    @ApiOperation("%package%.json")
    @GetMapping(COMPOSER_JSON, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getJson(@ArtifactPathVariable composerArtifactInfo: ComposerArtifactInfo)

    @ApiOperation("deploy")
    @PutMapping(COMPOSER_DEPLOY, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun deploy(@ArtifactPathVariable composerArtifactInfo: ComposerArtifactInfo, file: ArtifactFile)
}
