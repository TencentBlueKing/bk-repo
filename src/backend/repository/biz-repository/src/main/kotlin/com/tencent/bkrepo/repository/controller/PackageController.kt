package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.service.PackageService
import org.springframework.web.bind.annotation.RestController

@RestController
class PackageController(
    private val packageService: PackageService
): PackageClient {

    override fun findPackageByKey(projectId: String, repoName: String, packageKey: String): Response<PackageSummary?> {
        val packageSummary = packageService.findPackageByKey(projectId, repoName, packageKey)
        return ResponseBuilder.success(packageSummary)
    }

    override fun findVersionByName(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String
    ): Response<PackageVersion?> {
        val packageVersion = packageService.findVersionByName(projectId, repoName, packageKey, version)
        return ResponseBuilder.success(packageVersion)
    }

    override fun createVersion(request: PackageVersionCreateRequest): Response<Void> {
        packageService.createPackageVersion(request)
        return ResponseBuilder.success()
    }

    override fun deletePackage(projectId: String, repoName: String, packageKey: String): Response<Void> {
        packageService.deletePackage(projectId, repoName, packageKey)
        return ResponseBuilder.success()
    }

    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        version: String
    ): Response<Void> {
        packageService.deleteVersion(projectId, repoName, packageKey, version)
        return ResponseBuilder.success()
    }

    override fun searchPackage(queryModel: QueryModel): Response<Page<MutableMap<*, *>>> {
        return ResponseBuilder.success(packageService.searchPackage(queryModel))
    }
}