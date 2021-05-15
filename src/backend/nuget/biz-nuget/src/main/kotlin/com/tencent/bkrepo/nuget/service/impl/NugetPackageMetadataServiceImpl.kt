package com.tencent.bkrepo.nuget.service.impl

import com.fasterxml.jackson.core.JsonProcessingException
import com.google.common.net.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.nuget.constant.NUGET_V3_NOT_FOUND
import com.tencent.bkrepo.nuget.constant.VERSION
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.service.NugetPackageMetadataService
import com.tencent.bkrepo.nuget.util.NugetUtils
import com.tencent.bkrepo.nuget.util.NugetV3RegistrationUtils
import com.tencent.bkrepo.nuget.util.NugetVersionUtils
import com.tencent.bkrepo.repository.api.PackageClient
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import kotlin.streams.toList

@Service
class NugetPackageMetadataServiceImpl(
    private val packageClient: PackageClient
) : NugetPackageMetadataService {
    override fun registrationIndex(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        with(artifactInfo) {
            val packageVersionList =
                packageClient.listAllVersion(projectId, repoName, PackageKeys.ofNuget(packageName)).data
            if (packageVersionList == null || packageVersionList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND.value)
                    .header(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_XML)
                    .body(NUGET_V3_NOT_FOUND)
//              throw NugetMetadataListNotFoundException(NUGET_V3_NOT_FOUND)
            }
            val metadataList = packageVersionList.map { it.metadata }.stream()
                .sorted { o1, o2 -> NugetVersionUtils.compareSemVer(o1[VERSION] as String, o2[VERSION] as String) }
                .toList()
            try {
                val v3RegistrationUrl = NugetUtils.getV3Url(artifactInfo) + '/' + registrationPath
                return ResponseEntity.ok(
                    NugetV3RegistrationUtils.metadataToRegistrationIndex(metadataList, v3RegistrationUrl)
                )
            } catch (ignored: JsonProcessingException) {
                logger.error("failed to deserialize metadata to registration index json")
                throw ignored
            }
        }
    }

    override fun registrationPage(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        with(artifactInfo) {
            val packageVersionList =
                packageClient.listAllVersion(projectId, repoName, PackageKeys.ofNuget(packageName)).data
            if (packageVersionList == null || packageVersionList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND.value)
                    .header(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_XML)
                    .body(NUGET_V3_NOT_FOUND)
//              throw NugetMetadataListNotFoundException(NUGET_V3_NOT_FOUND)
            }
            val metadataList = packageVersionList.map { it.metadata }.stream()
                .sorted { o1, o2 -> NugetVersionUtils.compareSemVer(o1[VERSION] as String, o2[VERSION] as String) }
                .toList()
            try {
                val v3RegistrationUrl = NugetUtils.getV3Url(artifactInfo) + '/' + registrationPath
                return ResponseEntity.ok(
                    NugetV3RegistrationUtils.metadataToRegistrationPage(
                        metadataList, packageName, lowerVersion, upperVersion, v3RegistrationUrl
                    )
                )
            } catch (ignored: JsonProcessingException) {
                logger.error("failed to deserialize metadata to registration index json")
                throw ignored
            }
        }
    }

    override fun registrationLeaf(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        with(artifactInfo) {
            // 确保version一定存在
            packageClient.findVersionByName(projectId, repoName, PackageKeys.ofNuget(packageName), version).data
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND.value)
                    .header(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_XML)
                    .body(NUGET_V3_NOT_FOUND)
            try {
                val v3RegistrationUrl = NugetUtils.getV3Url(artifactInfo) + '/' + registrationPath
                return ResponseEntity.ok(
                    NugetV3RegistrationUtils.metadataToRegistrationLeaf(packageName, version, true, v3RegistrationUrl)
                )
            } catch (ignored: JsonProcessingException) {
                logger.error("failed to deserialize metadata to registration index json")
                throw ignored
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NugetPackageMetadataServiceImpl::class.java)
    }
}
