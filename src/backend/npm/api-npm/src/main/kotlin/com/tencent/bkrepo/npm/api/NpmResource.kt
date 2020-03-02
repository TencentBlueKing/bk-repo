package com.tencent.bkrepo.npm.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PACKAGE_DIST_TAG_ADD_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PACKAGE_DIST_TAG_INFO_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PACKAGE_INFO_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PACKAGE_SCOPE_SIMPLE_TGZ_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PACKAGE_SEARCH_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PACKAGE_TGZ_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PACKAGE_VERSION_INFO_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_PKG_PUBLISH_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_SCOPE_PACKAGE_VERSION_INFO_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_SCOPE_PKG_PUBLISH_MAPPING_URI
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo.Companion.NPM_UNPUBLISH_MAPPING_URI
import com.tencent.bkrepo.npm.pojo.metadata.MetadataSearchRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody

@Api("npm接口定义")
interface NpmResource {
    @ApiOperation("publish package")
    @PutMapping(NPM_PKG_PUBLISH_MAPPING_URI, NPM_SCOPE_PKG_PUBLISH_MAPPING_URI)
    fun publish(
        @RequestAttribute userId: String,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @RequestBody body: String
    ): Response<Void>

    @ApiOperation("search package.json info")
    @GetMapping(
        NPM_SCOPE_PACKAGE_VERSION_INFO_MAPPING_URI,
        NPM_PACKAGE_INFO_MAPPING_URI,
        NPM_PACKAGE_VERSION_INFO_MAPPING_URI,
        produces = [MediaType.APPLICATION_JSON_UTF8_VALUE]
    )
    fun searchPackageInfo(@ArtifactPathVariable artifactInfo: NpmArtifactInfo): Map<String, Any>

    @ApiOperation("install tgz file")
    @GetMapping(value = [NPM_PACKAGE_TGZ_MAPPING_URI, NPM_PACKAGE_SCOPE_SIMPLE_TGZ_MAPPING_URI])
    fun download(@ArtifactPathVariable artifactInfo: NpmArtifactInfo)

    @ApiOperation("unpublish package")
    @DeleteMapping(NPM_UNPUBLISH_MAPPING_URI)
    fun unpublish(@RequestAttribute userId: String, @ArtifactPathVariable artifactInfo: NpmArtifactInfo): Response<Void>

    @ApiOperation("npm search")
    @GetMapping(NPM_PACKAGE_SEARCH_MAPPING_URI)
    fun search(@ArtifactPathVariable artifactInfo: NpmArtifactInfo, searchRequest: MetadataSearchRequest): Map<String, Any>

    @ApiOperation("npm get dist-tag ls")
    @GetMapping(NPM_PACKAGE_DIST_TAG_INFO_MAPPING_URI)
    fun getDistTagsInfo(@ArtifactPathVariable artifactInfo: NpmArtifactInfo): Map<String, String>

    @ApiOperation("npm dist-tag add")
    @PutMapping(NPM_PACKAGE_DIST_TAG_ADD_MAPPING_URI)
    fun addDistTags(@ArtifactPathVariable artifactInfo: NpmArtifactInfo, @RequestBody body: String): Map<String, String>

    @ApiOperation("npm dist-tag rm")
    @DeleteMapping(NPM_PACKAGE_DIST_TAG_ADD_MAPPING_URI)
    fun deleteDistTags(@ArtifactPathVariable artifactInfo: NpmArtifactInfo)
}
