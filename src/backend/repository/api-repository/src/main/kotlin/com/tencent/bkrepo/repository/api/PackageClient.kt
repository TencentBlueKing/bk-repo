package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Primary
@FeignClient(SERVICE_NAME, contextId = "PackageClient")
@RequestMapping("/service")
interface PackageClient {

    @ApiOperation("查询包信息")
    @GetMapping("/package/info/{projectId}/{repoName}/{packageKey}")
    fun findPackageByKey(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable packageKey: String
    ): Response<PackageSummary?>

    @ApiOperation("查询版本信息")
    @GetMapping("/package/info/{projectId}/{repoName}/{packageKey}/{versionName}")
    fun findVersionByName(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable packageKey: String,
        @PathVariable versionName: String
    ): Response<PackageVersion?>

    @ApiOperation("创建包版本")
    @PostMapping("/version/create")
    fun createVersion(
        @RequestBody request: PackageVersionCreateRequest
    ): Response<Void>

    @ApiOperation("删除包")
    @DeleteMapping("/package/delete/{projectId}/{repoName}/{packageKey}")
    fun deletePackage(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable packageKey: String
    ): Response<Void>

    @ApiOperation("删除版本")
    @DeleteMapping("/version/delete/{projectId}/{repoName}/{packageKey}/{versionName}")
    fun deleteVersion(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable packageKey: String,
        @PathVariable versionName: String
    ): Response<Void>
}
