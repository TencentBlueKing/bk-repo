package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.service.PackageService
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 包管理接口
 */
@RestController
@RequestMapping("/api")
class UserPackageController(
    private val packageService: PackageService
) {

    @ApiOperation("分页查询包")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/package/page/{projectId}/{repoName}")
    fun listPackagePage(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam packageName: String? = null,
        @RequestParam pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @RequestParam pageSize: Int = DEFAULT_PAGE_SIZE
    ): Response<Page<PackageSummary>> {
        return ResponseBuilder.success(packageService.listPackagePageByName(projectId, repoName, packageName, pageNumber, pageSize))
    }

    @ApiOperation("分页查询版本")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/version/page/{projectId}/{repoName}/{packageKey}")
    fun listVersionPage(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable packageKey: String,
        @RequestParam pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @RequestParam pageSize: Int = DEFAULT_PAGE_SIZE
    ): Response<Page<PackageVersion>> {
        return ResponseBuilder.success(packageService.listVersionPage(projectId, repoName, packageKey, pageNumber, pageSize))
    }

    @ApiOperation("查询包信息")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/package/info/{projectId}/{repoName}/{packageKey}")
    fun findPackageByKey(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable packageKey: String
    ): Response<PackageSummary?> {
        return ResponseBuilder.success(packageService.findPackageByKey(projectId, repoName, packageKey))
    }

    @ApiOperation("删除包")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @DeleteMapping("/package/delete/{projectId}/{repoName}/{packageKey}")
    fun deletePackage(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable packageKey: String
    ): Response<Void> {
        packageService.deletePackage(projectId, repoName, packageKey)
        return ResponseBuilder.success()
    }

    @ApiOperation("删除版本")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    @DeleteMapping("/version/delete/{projectId}/{repoName}/{packageKey}/{version}")
    fun deleteVersion(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable packageKey: String,
        @PathVariable version: String
    ): Response<Void> {
        packageService.deleteVersion(projectId, repoName, packageKey, version)
        return ResponseBuilder.success()
    }
}
