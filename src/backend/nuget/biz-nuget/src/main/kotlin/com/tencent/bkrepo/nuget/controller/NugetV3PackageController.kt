package com.tencent.bkrepo.nuget.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.model.v3.RegistrationIndex
import com.tencent.bkrepo.nuget.model.v3.search.SearchRequest
import com.tencent.bkrepo.nuget.model.v3.search.SearchResponse
import com.tencent.bkrepo.nuget.service.NugetV3PackageService
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

    /**
     * 获取服务索引 service index
     */
    @GetMapping("/index.json", produces = ["application/json"])
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun feed(
        @ArtifactPathVariable artifactInfo: NugetArtifactInfo
    ): String {
        return nugetV3PackageService.getFeed(artifactInfo)
    }

    @GetMapping("/registration-semver2/{packageId}/index.json", produces = [MediaTypes.APPLICATION_JSON])
    fun registrationSemver2Index(
        @ArtifactPathVariable artifactInfo: NugetArtifactInfo,
        @PathVariable packageId: String
    ): RegistrationIndex {
        return nugetV3PackageService.registration(artifactInfo, packageId, "registration-semver2", true)
    }

    @GetMapping("/flatcontainer/{packageId}/{packageVersion}/*.nupkg")
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun download(
        @ArtifactPathVariable artifactInfo: NugetArtifactInfo,
        @PathVariable packageId: String,
        @PathVariable packageVersion: String
    ) {
        nugetV3PackageService.download(artifactInfo, packageId, packageVersion)
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
