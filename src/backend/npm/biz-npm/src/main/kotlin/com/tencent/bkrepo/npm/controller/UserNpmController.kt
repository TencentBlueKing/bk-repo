package com.tencent.bkrepo.npm.controller

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.PackageInfoResponse
import com.tencent.bkrepo.npm.pojo.user.NpmPackageInfo
import com.tencent.bkrepo.npm.pojo.user.NpmPackageVersionInfo
import com.tencent.bkrepo.npm.pojo.user.PackageDeleteRequest
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
    @GetMapping("/query/{projectId}/{repoName}/*")
    fun queryPackageInfo(@ArtifactPathVariable artifactInfo: NpmArtifactInfo): Response<PackageInfoResponse> {
        return ResponseBuilder.success(npmWebService.queryPackageInfo(artifactInfo))
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
                projectId, repoName, artifactUri, userId
            )
            npmWebService.deletePackage(deleteRequest)
            return ResponseBuilder.success()
        }
    }
}