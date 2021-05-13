package com.tencent.bkrepo.nuget.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.model.v3.search.SearchRequest
import com.tencent.bkrepo.nuget.model.v3.search.SearchResponse
import com.tencent.bkrepo.nuget.service.NugetV3PackageService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Suppress("MVCPathVariableInspection")
@RestController
@RequestMapping("/{projectId}/{repoName}/v3")
class NugetV3PackageController(
    private val nugetV3PackageService: NugetV3PackageService
) {

    @GetMapping("/query", produces = [MediaTypes.APPLICATION_JSON])
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun search(
        @ArtifactPathVariable artifactInfo: NugetArtifactInfo,
        searchRequest: SearchRequest
    ): SearchResponse {
        return nugetV3PackageService.search(artifactInfo, searchRequest)
    }
}
