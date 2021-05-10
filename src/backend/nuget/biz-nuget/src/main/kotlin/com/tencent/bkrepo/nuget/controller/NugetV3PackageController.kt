package com.tencent.bkrepo.nuget.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.pojo.response.VersionListResponse
import com.tencent.bkrepo.nuget.model.v3.RegistrationIndex
import com.tencent.bkrepo.nuget.model.v3.search.SearchRequest
import com.tencent.bkrepo.nuget.model.v3.search.SearchResponse
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDownloadArtifactInfo
import com.tencent.bkrepo.nuget.service.NugetV3PackageService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Suppress("MVCPathVariableInspection")
@RestController
@RequestMapping("/{projectId}/{repoName}/v3")
class NugetV3PackageController(
    private val nugetV3PackageService: NugetV3PackageService
) {

    @GetMapping("/registration-semver2/{packageId}/index.json", produces = [MediaTypes.APPLICATION_JSON])
    fun registrationSemver2Index(
        @ArtifactPathVariable artifactInfo: NugetArtifactInfo,
        @PathVariable packageId: String
    ): RegistrationIndex {
        return nugetV3PackageService.registration(artifactInfo, packageId, "registration-semver2", true)
    }

    /**
     * Download package content (.nupkg)
     *
     * GET {@id}/{LOWER_ID}/{LOWER_VERSION}/{LOWER_ID}.{LOWER_VERSION}.nupkg
     *
     * return binary stream
     */
    @GetMapping("/flatcontainer/{id}/{version}/*.nupkg")
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun downloadPackageContent(
        artifactInfo: NugetDownloadArtifactInfo
    ) {
        nugetV3PackageService.downloadPackageContent(artifactInfo)
    }

    /**
     * Download package manifest (.nuspec)
     *
     * GET {@id}/{LOWER_ID}/{LOWER_VERSION}/{LOWER_ID}.nuspec
     *
     * return xml document
     */
    @GetMapping("/flatcontainer/{id}/{version}/*.nuspec")
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun downloadPackageManifest(
        artifactInfo: NugetDownloadArtifactInfo
    ) {
        nugetV3PackageService.downloadPackageManifest(artifactInfo)
    }

    /**
     * Enumerate package versions
     * This list contains both listed and unlisted package versions.
     *
     * GET {@id}/{LOWER_ID}/index.json
     */
    @GetMapping("/flatcontainer/{packageId}/index.json")
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun packageVersions(
        @ArtifactPathVariable artifactInfo: NugetArtifactInfo,
        @PathVariable packageId: String
    ): ResponseEntity<VersionListResponse> {
        val versionList = nugetV3PackageService.packageVersions(artifactInfo, packageId)
        return ResponseEntity.ok(versionList)
    }

    @GetMapping("/query", produces = [MediaTypes.APPLICATION_JSON])
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun search(
        @ArtifactPathVariable artifactInfo: NugetArtifactInfo,
        searchRequest: SearchRequest
    ): SearchResponse {
        return nugetV3PackageService.search(artifactInfo, searchRequest)
    }
}
