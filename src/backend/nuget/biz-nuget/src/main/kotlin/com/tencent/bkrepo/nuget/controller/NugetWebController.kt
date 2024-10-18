package com.tencent.bkrepo.nuget.controller

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.audit.ActionAuditContent
import com.tencent.bkrepo.common.audit.REPO_EDIT_ACTION
import com.tencent.bkrepo.common.audit.REPO_RESOURCE
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.NUGET_EXT_DELETE_PACKAGE
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.NUGET_EXT_DELETE_VERSION
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.NUGET_EXT_DOMAIN
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.NUGET_EXT_VERSION_DETAIL
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDeleteArtifactInfo
import com.tencent.bkrepo.nuget.pojo.domain.NugetDomainInfo
import com.tencent.bkrepo.nuget.pojo.user.PackageVersionInfo
import com.tencent.bkrepo.nuget.service.NugetWebService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("NUGET web页面操作接口")
@RestController
@RequestMapping("/ext")
class NugetWebController(
    private val nugetWebService: NugetWebService
) {

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
    @Permission(ResourceType.REPO, PermissionAction.DELETE)
    @ApiOperation("删除仓库下的包")
    @DeleteMapping(NUGET_EXT_DELETE_PACKAGE)
    fun deletePackage(
        @RequestAttribute userId: String,
        artifactInfo: NugetDeleteArtifactInfo,
        @ApiParam(value = "包名称", required = true)
        @RequestParam packageKey: String
    ): Response<Void> {
        nugetWebService.deletePackage(userId, artifactInfo)
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
    @Permission(ResourceType.REPO, PermissionAction.DELETE)
    @ApiOperation("删除仓库下的包版本")
    @DeleteMapping(NUGET_EXT_DELETE_VERSION)
    fun deleteVersion(
        @RequestAttribute userId: String,
        artifactInfo: NugetDeleteArtifactInfo,
        @ApiParam(value = "包名称", required = true)
        @RequestParam packageKey: String,
        @ApiParam(value = "包版本", required = true)
        @RequestParam version: String
    ): Response<Void> {
        nugetWebService.deleteVersion(userId, artifactInfo)
        return ResponseBuilder.success()
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @ApiOperation("查询包的版本详情")
    @GetMapping(NUGET_EXT_VERSION_DETAIL)
    fun detailVersion(
        @RequestAttribute
        userId: String,
        artifactInfo: NugetArtifactInfo,
        @ApiParam(value = "包唯一Key", required = true)
        @RequestParam packageKey: String,
        @ApiParam(value = "包版本", required = true)
        @RequestParam version: String
    ): Response<PackageVersionInfo> {
        return ResponseBuilder.success(nugetWebService.detailVersion(artifactInfo, packageKey, version))
    }

    @ApiOperation("获取nuget域名地址")
    @GetMapping(NUGET_EXT_DOMAIN)
    fun getRegistryDomain(): Response<NugetDomainInfo> {
        return ResponseBuilder.success(nugetWebService.getRegistryDomain())
    }
}
