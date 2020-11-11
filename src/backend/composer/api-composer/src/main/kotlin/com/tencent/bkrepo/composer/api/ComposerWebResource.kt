package com.tencent.bkrepo.composer.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Api("Composer 产品-web接口")
@RequestMapping("/ext")
interface ComposerWebResource {

    @ApiOperation("composer 包删除接口")
    @DeleteMapping(ComposerArtifactInfo.COMPOSER_EXT_PACKAGE_DELETE)
    fun deletePackage(
        @ArtifactPathVariable composerArtifactInfo: ComposerArtifactInfo,
        @RequestParam packageKey: String
    ): Response<Void>

    @ApiOperation("composer 版本删除接口")
    @DeleteMapping(ComposerArtifactInfo.COMPOSER_EXT_VERSION_DELETE)
    fun deleteVersion(
        @ArtifactPathVariable composerArtifactInfo: ComposerArtifactInfo,
        @RequestParam packageKey: String,
        @RequestParam version: String?
    ): Response<Void>

    @ApiOperation("composer 版本详情接口")
    @GetMapping(ComposerArtifactInfo.COMPOSER_EXT_DETAIL)
    fun artifactDetail(
        @ArtifactPathVariable composerArtifactInfo: ComposerArtifactInfo,
        @RequestParam packageKey: String,
        @RequestParam version: String?
    ): Response<Any?>
}
