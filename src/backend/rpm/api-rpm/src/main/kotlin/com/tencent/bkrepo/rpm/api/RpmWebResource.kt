package com.tencent.bkrepo.rpm.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Api("Rpm 产品-web接口")
@RequestMapping("/ext")
interface RpmWebResource {

    @ApiOperation("rpm 包删除接口")
    @DeleteMapping(RpmArtifactInfo.MAVEN_EXT_PACKAGE_DELETE)
    fun deletePackage(
            @ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo,
            @RequestParam packageKey: String
    ): Response<Void>

    @ApiOperation("rpm 包版本删除接口")
    @DeleteMapping(RpmArtifactInfo.MAVEN_EXT_VERSION_DELETE)
    fun deleteVersion(
            @ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo,
            @RequestParam packageKey: String,
            @RequestParam version: String?
    ): Response<Void>

    @ApiOperation("rpm 版本详情接口")
    @GetMapping(RpmArtifactInfo.MAVEN_EXT_DETAIL)
    fun artifactDetail(
            @ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo,
            @RequestParam packageKey: String,
            @RequestParam version: String?
    ): Response<Any?>

}