package com.tencent.bkrepo.npm.controller

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.user.PackageInfoResponse
import com.tencent.bkrepo.npm.pojo.user.NpmPackageInfo
import com.tencent.bkrepo.npm.pojo.user.NpmPackageVersionInfo
import com.tencent.bkrepo.npm.pojo.user.PackageDeleteRequest
import com.tencent.bkrepo.npm.pojo.user.PackageVersionDeleteRequest
import com.tencent.bkrepo.npm.pojo.user.PackageVersionInfo
import com.tencent.bkrepo.npm.service.NpmWebService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("npm 用户接口")
@RestController
@RequestMapping("/api")
class UserNpmController(
    private val npmWebService: NpmWebService
) {

    @ApiOperation("查询包的相关信息")
    @GetMapping("/query/{projectId}/{repoName}/{name}")
    fun queryPackageInfo(
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "包名称", required = true)
        @PathVariable name: String
    ): Response<PackageInfoResponse> {
        return ResponseBuilder.success(npmWebService.queryPackageInfo(artifactInfo, name))
    }

    @ApiOperation("查询包的相关信息")
    @GetMapping("/query/{projectId}/{repoName}/@{scope}/{name}")
    fun queryPackageInfo(
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "scope包名称", required = true)
        @PathVariable scope: String,
        @ApiParam(value = "包名称", required = true)
        @PathVariable name: String
    ): Response<PackageInfoResponse> {
        val pkgName = String.format("@%s/%s", scope, name)
        return ResponseBuilder.success(npmWebService.queryPackageInfo(artifactInfo, pkgName))
    }

    @ApiOperation("查询包的版本详情")
    @GetMapping("/detail/{projectId}/{repoName}/{name}/{version}")
    fun detailVersion(
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "包名称", required = true)
        @PathVariable name: String,
        @ApiParam(value = "包名称", required = true)
        @PathVariable version: String
    ): Response<PackageVersionInfo> {
        return ResponseBuilder.success(npmWebService.detailVersion(artifactInfo, name, version))
    }

    @ApiOperation("查询包的版本详情")
    @GetMapping("/detail/{projectId}/{repoName}/@{scope}/{name}/{version}")
    fun detailVersion(
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "包scope名称", required = true)
        @PathVariable scope: String,
        @ApiParam(value = "包名称", required = true)
        @PathVariable name: String,
        @ApiParam(value = "包名称", required = true)
        @PathVariable version: String
    ): Response<PackageVersionInfo> {
        val pkgName = String.format("@%s/%s", scope, name)
        return ResponseBuilder.success(npmWebService.detailVersion(artifactInfo, pkgName, version))
    }

    @ApiOperation("查询仓库下对应的包列表")
    @GetMapping("/page/{projectId}/{repoName}")
    fun queryPkgList(
        @RequestAttribute
        userId: String?,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "当前页", required = true, defaultValue = "1")
        @RequestParam pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @ApiParam(value = "分页大小", required = true, defaultValue = "20")
        @RequestParam pageSize: Int = DEFAULT_PAGE_SIZE,
        @ApiParam(value = "包名称", required = false, defaultValue = "")
        @RequestParam name: String?,
        @ApiParam(value = "制品状态", required = false, defaultValue = "")
        @RequestParam stageTag: String?
    ): Response<Page<NpmPackageInfo>> {
        return ResponseBuilder.success(
            npmWebService.queryPkgList(
                userId,
                artifactInfo,
                pageNumber,
                pageSize,
                name,
                stageTag
            )
        )
    }

    @ApiOperation("查询仓库下包对应的版本列表")
    @GetMapping("/page/{projectId}/{repoName}/{name}")
    fun queryPkgVersionList(
        @RequestAttribute
        userId: String?,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "当前页", required = true, defaultValue = "1")
        @RequestParam pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @ApiParam(value = "分页大小", required = true, defaultValue = "20")
        @RequestParam pageSize: Int = DEFAULT_PAGE_SIZE,
        @ApiParam(value = "包名称", required = true)
        @PathVariable name: String
    ): Response<Page<NpmPackageVersionInfo>> {
        return ResponseBuilder.success(
            npmWebService.queryPkgVersionList(
                userId,
                artifactInfo,
                pageNumber,
                pageSize,
                name
            )
        )
    }

    @ApiOperation("查询仓库下包对应的版本列表")
    @GetMapping("/page/{projectId}/{repoName}/@{scope}/{name}")
    fun queryPkgVersionList(
        @RequestAttribute
        userId: String?,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "当前页", required = true, defaultValue = "1")
        @RequestParam pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @ApiParam(value = "分页大小", required = true, defaultValue = "20")
        @RequestParam pageSize: Int = DEFAULT_PAGE_SIZE,
        @ApiParam(value = "包scope名称", required = true)
        @PathVariable scope: String,
        @ApiParam(value = "包名称", required = true)
        @PathVariable name: String
    ): Response<Page<NpmPackageVersionInfo>> {
        val pkgName = String.format("@%s/%s", scope, name)
        return ResponseBuilder.success(
            npmWebService.queryPkgVersionList(
                userId,
                artifactInfo,
                pageNumber,
                pageSize,
                pkgName
            )
        )
    }

    @ApiOperation("删除仓库下的包")
    @DeleteMapping("/delete/{projectId}/{repoName}/{name}")
    fun deletePackage(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "包名称", required = true)
        @PathVariable name: String
    ): Response<Void> {
        with(artifactInfo) {
            val deleteRequest = PackageDeleteRequest(
                projectId, repoName, "", name, userId
            )
            npmWebService.deletePackage(deleteRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("删除仓库下的包")
    @DeleteMapping("/delete/{projectId}/{repoName}/@{scope}/{name}")
    fun deletePackage(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "scope名称", required = true)
        @PathVariable scope: String,
        @ApiParam(value = "包名称", required = true)
        @PathVariable name: String
    ): Response<Void> {
        with(artifactInfo) {
            //val pkgName = String.format("@%s/%s", scope, name)
            val deleteRequest = PackageDeleteRequest(
                projectId, repoName, scope, name, userId
            )
            npmWebService.deletePackage(deleteRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("删除仓库下的包")
    @DeleteMapping("/delete/{projectId}/{repoName}/{name}/{version}")
    fun deleteVersion(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "包名称", required = true)
        @PathVariable name: String,
        @ApiParam(value = "包版本", required = true)
        @PathVariable version: String
    ): Response<Void> {
        with(artifactInfo) {
            //val pkgName = String.format("@%s/%s", scope, name)
            val deleteRequest = PackageVersionDeleteRequest(
                projectId, repoName, "", name, version, userId
            )
            npmWebService.deleteVersion(deleteRequest)
            return ResponseBuilder.success()
        }
    }

    @ApiOperation("删除仓库下的包")
    @DeleteMapping("/delete/{projectId}/{repoName}/@{scope}/{name}/{version}")
    fun deleteVersion(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @ApiParam(value = "scope名称", required = true)
        @PathVariable scope: String,
        @ApiParam(value = "包名称", required = true)
        @PathVariable name: String,
        @ApiParam(value = "包版本", required = true)
        @PathVariable version: String
    ): Response<Void> {
        with(artifactInfo) {
            //val pkgName = String.format("@%s/%s", scope, name)
            val deleteRequest = PackageVersionDeleteRequest(
                projectId, repoName, scope, name, version, userId
            )
            npmWebService.deleteVersion(deleteRequest)
            return ResponseBuilder.success()
        }
    }
}