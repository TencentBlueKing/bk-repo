package com.tencent.bkrepo.npm.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PACKAGE_INFO_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PACKAGE_SCOPE_TGZ_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PACKAGE_TGZ_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PUBLISH_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_UNPUBLISH_MAPPING_URI
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody

@Api("npm接口定义")
interface NpmResource {
    @ApiOperation("publish package")
    @PutMapping(NPM_PUBLISH_MAPPING_URI)
    fun publish(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @RequestBody body: String
    ): Response<Void>

    @ApiOperation("search package.json info")
    @GetMapping(NPM_PACKAGE_INFO_MAPPING_URI)
    fun searchPackageInfo(@ArtifactPathVariable artifactInfo: NpmArtifactInfo): Map<String, Any>

    @ApiOperation("install tgz file")
    @GetMapping(value = [NPM_PACKAGE_TGZ_MAPPING_URI, NPM_PACKAGE_SCOPE_TGZ_MAPPING_URI])
    fun download(@ArtifactPathVariable artifactInfo: NpmArtifactInfo)

    @ApiOperation("unpublish package")
    @DeleteMapping(NPM_UNPUBLISH_MAPPING_URI)
    fun unpublish(@RequestAttribute userId: String, @ArtifactPathVariable artifactInfo: NpmArtifactInfo): Response<Void>
}
