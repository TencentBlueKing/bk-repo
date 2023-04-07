package com.tencent.bkrepo.nuget.controller

import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.service.NugetPackageMetadataService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * reference: https://docs.microsoft.com/en-us/nuget/api/registration-base-url-resource
 */
@Suppress("MVCPathVariableInspection")
@RestController
@RequestMapping("/{projectId}/{repoName}/v3")
class NugetPackageMetadataController(
    private val nugetPackageMetadataService: NugetPackageMetadataService
) {
    /**
     * GET {@id}/{LOWER_ID}/index.json
     *
     * Registration index
     *
     * RegistrationsBaseUrl/3.6.0+
     */
    @GetMapping(
        "/registration/{id}/index.json",
        "/registration{feature}/{id}/index.json",
        produces = [MediaTypes.APPLICATION_JSON]
    )
    fun registrationIndex(
        artifactInfo: NugetRegistrationArtifactInfo,
        @PathVariable feature: String?
    ): ResponseEntity<Any> {
        val isSemver2Endpoint = feature?.contains("semver2") ?: false
        val registrationPath = "registration" + (feature ?: "")
        return nugetPackageMetadataService.registrationIndex(artifactInfo, registrationPath, isSemver2Endpoint)
    }

    @GetMapping(
        "/registration/{id}/page/{lowerVersion}/{upperVersion}.json",
        "/registration{feature}/{id}/page/{lowerVersion}/{upperVersion}.json",
        produces = [MediaTypes.APPLICATION_JSON]
    )
    fun registrationPage(
        artifactInfo: NugetRegistrationArtifactInfo,
        @PathVariable feature: String?
    ): ResponseEntity<Any> {
        val isSemver2Endpoint = feature?.contains("semver2") ?: false
        val registrationPath = "registration" + (feature ?: "")
        return nugetPackageMetadataService.registrationPage(artifactInfo, registrationPath, isSemver2Endpoint)
    }

    @GetMapping(
        "/registration/{id}/{version}.json",
        "/registration{feature}/{id}/{version}.json",
        produces = [MediaTypes.APPLICATION_JSON]
    )
    fun registrationLeaf(
        artifactInfo: NugetRegistrationArtifactInfo,
        @PathVariable feature: String?
    ): ResponseEntity<Any> {
        val isSemver2Endpoint = feature?.contains("semver2") ?: false
        val registrationPath = "registration" + (feature ?: "")
        return nugetPackageMetadataService.registrationLeaf(artifactInfo, registrationPath, isSemver2Endpoint)
    }

    @GetMapping(
        "/registration/proxy/page/{id}",
        "/registration{feature}/proxy/page/{id}",
        produces = [MediaTypes.APPLICATION_JSON]
    )
    fun proxyRegistrationPage(
        artifactInfo: NugetRegistrationArtifactInfo,
        @PathVariable feature: String?,
        @RequestParam proxyChannelName: String,
        @RequestParam url: String
    ): ResponseEntity<Any> {
        val isSemver2Endpoint = feature?.contains("semver2") ?: false
        val registrationPath = "registration" + (feature ?: "")
        return nugetPackageMetadataService.proxyRegistrationPage(
            artifactInfo, proxyChannelName, url, registrationPath, isSemver2Endpoint
        )
    }
}
