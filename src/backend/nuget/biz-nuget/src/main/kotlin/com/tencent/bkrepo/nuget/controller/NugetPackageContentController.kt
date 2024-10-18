package com.tencent.bkrepo.nuget.controller

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.NODE_DOWNLOAD_ACTION
import com.tencent.bkrepo.common.artifact.audit.NODE_RESOURCE
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.DOWNLOAD_MANIFEST
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.DOWNLOAD_V3
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.ENUMERATE_VERSIONS
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.NUGET_PACKAGE_CONTENT_ROOT_URI
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDownloadArtifactInfo
import com.tencent.bkrepo.nuget.pojo.response.VersionListResponse
import com.tencent.bkrepo.nuget.service.NugetPackageContentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * reference: https://docs.microsoft.com/en-us/nuget/api/package-base-address-resource
 */
@Suppress("MVCPathVariableInspection")
@RestController
@RequestMapping(NUGET_PACKAGE_CONTENT_ROOT_URI)
class NugetPackageContentController(
    private val packageContentService: NugetPackageContentService
) {
    /**
     * Download package content (.nupkg)
     *
     * GET {@id}/{LOWER_ID}/{LOWER_VERSION}/{LOWER_ID}.{LOWER_VERSION}.nupkg
     *
     * return binary stream
     */
    @AuditEntry(
        actionId = NODE_DOWNLOAD_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DOWNLOAD_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.artifactUri",
            instanceNames = "#artifactInfo?.artifactUri"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_DOWNLOAD_CONTENT
    )
    @GetMapping(DOWNLOAD_V3)
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun downloadPackageContent(
        artifactInfo: NugetDownloadArtifactInfo
    ) {
        packageContentService.downloadPackageContent(artifactInfo)
    }

    /**
     * Download package manifest (.nuspec)
     *
     * GET {@id}/{LOWER_ID}/{LOWER_VERSION}/{LOWER_ID}.nuspec
     *
     * return xml document
     */
    @AuditEntry(
        actionId = NODE_DOWNLOAD_ACTION
    )
    @ActionAuditRecord(
        actionId = NODE_DOWNLOAD_ACTION,
        instance = AuditInstanceRecord(
            resourceType = NODE_RESOURCE,
            instanceIds = "#artifactInfo?.artifactUri",
            instanceNames = "#artifactInfo?.artifactUri"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#artifactInfo?.projectId"),
            AuditAttribute(name = ActionAuditContent.REPO_NAME_TEMPLATE, value = "#artifactInfo?.repoName")
        ],
        scopeId = "#artifactInfo?.projectId",
        content = ActionAuditContent.NODE_DOWNLOAD_CONTENT
    )
    @GetMapping(DOWNLOAD_MANIFEST)
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun downloadPackageManifest(
        artifactInfo: NugetDownloadArtifactInfo
    ) {
        packageContentService.downloadPackageManifest(artifactInfo)
    }

    /**
     * Enumerate package versions
     * This list contains both listed and unlisted package versions.
     *
     * GET {@id}/{LOWER_ID}/index.json
     */
    @GetMapping(ENUMERATE_VERSIONS)
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun packageVersions(
        artifactInfo: NugetArtifactInfo,
        @PathVariable packageId: String
    ): ResponseEntity<VersionListResponse> {
        val versionList = packageContentService.packageVersions(artifactInfo, packageId)
        return ResponseEntity.ok(versionList)
    }
}
