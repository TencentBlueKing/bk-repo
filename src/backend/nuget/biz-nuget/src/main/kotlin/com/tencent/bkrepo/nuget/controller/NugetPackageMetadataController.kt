package com.tencent.bkrepo.nuget.controller

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.model.v3.RegistrationIndex
import com.tencent.bkrepo.nuget.service.NugetV3PackageService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * reference: https://docs.microsoft.com/en-us/nuget/api/registration-base-url-resource
 */
@Suppress("MVCPathVariableInspection")
@RestController
@RequestMapping("/{projectId}/{repoName}/v3")
class NugetPackageMetadataController(
    private val nugetV3PackageService: NugetV3PackageService
) {
    /**
     * GET {@id}/{LOWER_ID}/index.json
     *
     * Registration index
     *
     * RegistrationsBaseUrl/3.6.0+
     */
    @GetMapping("/registration-semver2/{packageId}/index.json", produces = [MediaTypes.APPLICATION_JSON])
    fun registrationSemver2Index(
        @ArtifactPathVariable artifactInfo: NugetArtifactInfo,
        @PathVariable packageId: String
    ): RegistrationIndex {
        return nugetV3PackageService.registration(artifactInfo, packageId, "registration-semver2", true)
    }
}
