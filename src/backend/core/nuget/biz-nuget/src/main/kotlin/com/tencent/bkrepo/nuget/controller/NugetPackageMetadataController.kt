package com.tencent.bkrepo.nuget.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.NUGET_ROOT_URI_V3
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.REGISTRATION_INDEX
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.REGISTRATION_INDEX_FEATURE
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.REGISTRATION_LEAF
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.REGISTRATION_LEAF_FEATURE
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.REGISTRATION_PAGE
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.REGISTRATION_PAGE_FEATURE
import com.tencent.bkrepo.nuget.constant.REGISTRATION
import com.tencent.bkrepo.nuget.constant.SEMVER2
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.service.NugetPackageMetadataService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * reference: https://docs.microsoft.com/en-us/nuget/api/registration-base-url-resource
 */
@Suppress("MVCPathVariableInspection")
@RestController
@RequestMapping(NUGET_ROOT_URI_V3)
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
    @GetMapping(REGISTRATION_INDEX, REGISTRATION_INDEX_FEATURE, produces = [MediaTypes.APPLICATION_JSON])
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun registrationIndex(
        artifactInfo: NugetRegistrationArtifactInfo,
        @PathVariable feature: String?
    ): ResponseEntity<Any> {
        val isSemver2Endpoint = feature?.contains(SEMVER2) ?: false
        val registrationPath = REGISTRATION + (feature ?: "")
        return nugetPackageMetadataService.registrationIndex(artifactInfo, registrationPath, isSemver2Endpoint)
    }

    @GetMapping(REGISTRATION_PAGE, REGISTRATION_PAGE_FEATURE, produces = [MediaTypes.APPLICATION_JSON])
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun registrationPage(
        artifactInfo: NugetRegistrationArtifactInfo,
        @PathVariable feature: String?
    ): ResponseEntity<Any> {
        val isSemver2Endpoint = feature?.contains(SEMVER2) ?: false
        val registrationPath = REGISTRATION + (feature ?: "")
        return nugetPackageMetadataService.registrationPage(artifactInfo, registrationPath, isSemver2Endpoint)
    }

    @GetMapping(REGISTRATION_LEAF, REGISTRATION_LEAF_FEATURE, produces = [MediaTypes.APPLICATION_JSON])
    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun registrationLeaf(
        artifactInfo: NugetRegistrationArtifactInfo,
        @PathVariable feature: String?
    ): ResponseEntity<Any> {
        val isSemver2Endpoint = feature?.contains(SEMVER2) ?: false
        val registrationPath = REGISTRATION + (feature ?: "")
        return nugetPackageMetadataService.registrationLeaf(artifactInfo, registrationPath, isSemver2Endpoint)
    }
}
