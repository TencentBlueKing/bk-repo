package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.nuget.artifact.repository.NugetCompositeRepository
import com.tencent.bkrepo.nuget.artifact.repository.NugetRepository
import com.tencent.bkrepo.nuget.constant.NUGET_V3_NOT_FOUND
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.service.NugetPackageMetadataService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class NugetPackageMetadataServiceImpl : NugetPackageMetadataService, ArtifactService() {
    override fun registrationIndex(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        val repository = ArtifactContextHolder.getRepository() as NugetRepository
        val context = ArtifactQueryContext()
        context.putAttribute("registrationPath", registrationPath)
        context.putAttribute("isSemver2Endpoint", isSemver2Endpoint)
        val registrationIndex = repository.registrationIndex(context)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND.value)
                .header(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_XML)
                .body(NUGET_V3_NOT_FOUND)
        return ResponseEntity.ok(registrationIndex)
    }

    override fun registrationPage(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        val repository = ArtifactContextHolder.getRepository() as NugetRepository
        val context = ArtifactQueryContext()
        context.putAttribute("registrationPath", registrationPath)
        context.putAttribute("isSemver2Endpoint", isSemver2Endpoint)
        val registrationPage = repository.registrationPage(context)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND.value)
                .header(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_XML)
                .body(NUGET_V3_NOT_FOUND)
        return ResponseEntity.ok(registrationPage)
    }

    override fun proxyRegistrationPage(
        artifactInfo: NugetRegistrationArtifactInfo,
        proxyChannelName: String,
        url: String,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        val repository = ArtifactContextHolder.getRepository() as NugetCompositeRepository
        val registrationPage = repository.proxyRegistrationPage(
            artifactInfo, proxyChannelName, url, registrationPath, isSemver2Endpoint
        ) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND.value)
            .header(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_XML)
            .body(NUGET_V3_NOT_FOUND)
        return ResponseEntity.ok(registrationPage)
    }

    override fun registrationLeaf(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        val repository = ArtifactContextHolder.getRepository() as NugetRepository
        val context = ArtifactQueryContext()
        context.putAttribute("registrationPath", registrationPath)
        context.putAttribute("isSemver2Endpoint", isSemver2Endpoint)
        val registrationLeaf = repository.registrationLeaf(context)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND.value)
                .header(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_XML)
                .body(NUGET_V3_NOT_FOUND)
        return ResponseEntity.ok(registrationLeaf)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NugetPackageMetadataServiceImpl::class.java)
    }
}
