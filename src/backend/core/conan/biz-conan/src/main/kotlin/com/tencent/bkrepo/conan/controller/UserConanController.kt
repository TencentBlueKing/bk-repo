/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.conan.controller

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.REPO_EDIT_ACTION
import com.tencent.bkrepo.common.artifact.audit.REPO_RESOURCE
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.conan.pojo.ConanDomainInfo
import com.tencent.bkrepo.conan.pojo.PackageVersionInfo
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.CONAN_PACKAGE_DELETE_URL
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.CONAN_VERSION_DELETE_URL
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo.Companion.CONAN_VERSION_DETAIL
import com.tencent.bkrepo.conan.pojo.request.IndexRefreshRequest
import com.tencent.bkrepo.conan.service.ConanDeleteService
import com.tencent.bkrepo.conan.service.ConanExtService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Suppress("MVCPathVariableInspection")
@Tag(name = "conan产品接口")
@RestController
@RequestMapping("/ext")
class UserConanController(
    private val conanDeleteService: ConanDeleteService,
    private val conanExtService: ConanExtService
) {

    @Operation(summary = "查询包的版本详情")
    @GetMapping(CONAN_VERSION_DETAIL)
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun detailVersion(
        @RequestAttribute
        userId: String,
        @ArtifactPathVariable artifactInfo: ConanArtifactInfo,
        @Parameter(name = "包唯一Key", required = true)
        @RequestParam packageKey: String,
        @Parameter(name = "包版本", required = true)
        @RequestParam version: String
    ): Response<PackageVersionInfo> {
        return ResponseBuilder.success(conanDeleteService.detailVersion(artifactInfo, packageKey, version))
    }

    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#artifactInfo?.repoName",
            instanceNames = "#artifactInfo?.repoName"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#packageKey")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.REPO_PACKAGE_DELETE_CONTENT
    )
    @Operation(summary = "删除仓库下的包")
    @DeleteMapping(CONAN_PACKAGE_DELETE_URL)
    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun deletePackage(
        @RequestAttribute userId: String,
        artifactInfo: ConanArtifactInfo,
        @Parameter(name = "包唯一key", required = true)
        @RequestParam packageKey: String
    ): Response<Void> {
        conanDeleteService.removePackageByKey(artifactInfo, packageKey)
        return ResponseBuilder.success()
    }

    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#artifactInfo?.repoName",
            instanceNames = "#artifactInfo?.repoName"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.NAME_TEMPLATE, value = "#packageKey"),
            AuditAttribute(name = ActionAuditContent.VERSION_TEMPLATE, value = "#version")

        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.REPO_PACKAGE_VERSION_DELETE_CONTENT
    )
    @Operation(summary = "删除仓库下的包版本")
    @DeleteMapping(CONAN_VERSION_DELETE_URL)
    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    fun deleteVersion(
        @RequestAttribute userId: String,
        artifactInfo: ConanArtifactInfo,
        @Parameter(name = "包唯一key", required = true)
        @RequestParam packageKey: String,
        @Parameter(name = "包版本", required = true)
        @RequestParam version: String
    ): Response<Void> {
        conanDeleteService.removePackageVersion(artifactInfo, packageKey, version)
        return ResponseBuilder.success()
    }

    @Operation(summary = "获取conan域名地址")
    @GetMapping("/address")
    fun getRegistryDomain(): Response<ConanDomainInfo> {
        return ResponseBuilder.success(conanDeleteService.getDomain())
    }

    @Operation(summary = "仓库索引修正")
    @PostMapping("/repo/index/refresh/{projectId}/{repoName}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun repoIndexRefresh(
        @ArtifactPathVariable artifactInfo: ConanArtifactInfo,
    ): Response<PackageVersionInfo> {
        conanExtService.indexRefreshForRepo(artifactInfo.projectId, artifactInfo.repoName)
        return ResponseBuilder.success()
    }

    @Operation(summary = "recipe索引修正")
    @PostMapping("/recipe/index/refresh/{projectId}/{repoName}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun recipeIndexRefresh(
        @ArtifactPathVariable artifactInfo: ConanArtifactInfo,
        @RequestBody request: IndexRefreshRequest
    ): Response<Void> {
        conanExtService.indexRefreshForRecipe(artifactInfo.projectId, artifactInfo.repoName, request)
        return ResponseBuilder.success()
    }


    @Operation(summary = "packagekey下索引修正")
    @PostMapping("/packagekey/index/refresh/{projectId}/{repoName}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun packageKeyIndexRefresh(
        @ArtifactPathVariable artifactInfo: ConanArtifactInfo,
        @RequestParam packageKey: String,
    ): Response<Void> {
        conanExtService.indexRefreshByPackageKey(artifactInfo.projectId, artifactInfo.repoName, packageKey)
        return ResponseBuilder.success()
    }

    @Operation(summary = "重新生成仓库元数据信息")
    @PostMapping("/metadata/refresh/{projectId}/{repoName}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun metadataRefresh(
        @ArtifactPathVariable artifactInfo: ConanArtifactInfo,
    ): Response<Void> {
        conanExtService.metadataRefresh(artifactInfo.projectId, artifactInfo.repoName)
        return ResponseBuilder.success()
    }

    @Operation(summary = "重新生成包元数据信息")
    @PostMapping("/metadata/package/refresh/{projectId}/{repoName}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun packageMetadataRefresh(
        @ArtifactPathVariable artifactInfo: ConanArtifactInfo,
        @RequestParam packageKey: String,
    ): Response<Void> {
        conanExtService.packageMetadataRefresh(artifactInfo.projectId, artifactInfo.repoName, packageKey)
        return ResponseBuilder.success()
    }

    @Operation(summary = "重新生成版本元数据信息")
    @PostMapping("/metadata/version/refresh/{projectId}/{repoName}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
    fun versionMetadataRefresh(
        @ArtifactPathVariable artifactInfo: ConanArtifactInfo,
        @RequestParam packageKey: String,
        @RequestParam version: String,
    ): Response<Void> {
        conanExtService.versionMetadataRefresh(artifactInfo.projectId, artifactInfo.repoName, packageKey, version)
        return ResponseBuilder.success()
    }
}
