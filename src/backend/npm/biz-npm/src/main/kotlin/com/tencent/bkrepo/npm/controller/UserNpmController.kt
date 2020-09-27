package com.tencent.bkrepo.npm.controller

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.user.request.PackageDeleteRequest
import com.tencent.bkrepo.npm.pojo.user.request.PackageVersionDeleteRequest
import com.tencent.bkrepo.npm.pojo.user.PackageVersionInfo
import com.tencent.bkrepo.npm.service.NpmWebService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("npm 用户接口")
@RequestMapping("/ext")
@RestController
class UserNpmController(
    private val npmWebService: NpmWebService
) {

    @ApiOperation("查询包的版本详情")
    @GetMapping("/version/detail/{projectId}/{repoName}")
    fun detailVersion(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "包唯一Key", required = true)
        @RequestParam packageKey: String,
        @ApiParam(value = "包版本", required = true)
        @RequestParam version: String
    ): Response<PackageVersionInfo> {
        val name = PackageKeys.resolveNpm(packageKey)
        return ResponseBuilder.success(npmWebService.detailVersion(artifactInfo, name, version))
    }

    @ApiOperation("删除仓库下的包")
    @DeleteMapping("/package/delete/{projectId}/{repoName}")
    fun deletePackage(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "包名称", required = true)
        @RequestParam packageKey: String
    ): Response<Void> {
        with(artifactInfo) {
            val pkgName = PackageKeys.resolveNpm(packageKey)
            val deleteRequest = PackageDeleteRequest(
                projectId, repoName, pkgName, userId
            )
            npmWebService.deletePackage(deleteRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("删除仓库下的包版本")
    @DeleteMapping("/version/delete/{projectId}/{repoName}")
    fun deleteVersion(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "包名称", required = true)
        @RequestParam packageKey: String,
        @ApiParam(value = "包版本", required = true)
        @RequestParam version: String
    ): Response<Void> {
        with(artifactInfo) {
            val pkgName = PackageKeys.resolveNpm(packageKey)
            val deleteRequest = PackageVersionDeleteRequest(
                projectId, repoName, pkgName, version, userId
            )
            npmWebService.deleteVersion(deleteRequest)
            return ResponseBuilder.success()
        }
    }
}