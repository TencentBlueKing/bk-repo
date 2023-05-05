package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.nuget.constant.NUGET_V3_NOT_FOUND
import com.tencent.bkrepo.nuget.constant.NugetQueryType
import com.tencent.bkrepo.nuget.constant.QUERY_TYPE
import com.tencent.bkrepo.nuget.constant.REGISTRATION_PATH
import com.tencent.bkrepo.nuget.constant.SEMVER2_ENDPOINT
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationIndex
import com.tencent.bkrepo.nuget.pojo.v3.metadata.leaf.RegistrationLeaf
import com.tencent.bkrepo.nuget.pojo.v3.metadata.page.RegistrationPage
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
        val repository = ArtifactContextHolder.getRepository()
        val context = ArtifactQueryContext()
        context.putAttribute(QUERY_TYPE, NugetQueryType.REGISTRATION_INDEX)
        context.putAttribute(REGISTRATION_PATH, registrationPath)
        context.putAttribute(SEMVER2_ENDPOINT, isSemver2Endpoint)
        val registrationIndex = (repository.query(context) as RegistrationIndex?) ?: return buildNotFoundResponse()
        return ResponseEntity.ok(registrationIndex)
    }

    override fun registrationPage(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        val repository = ArtifactContextHolder.getRepository()
        val context = ArtifactQueryContext()
        context.putAttribute(QUERY_TYPE, NugetQueryType.REGISTRATION_PAGE)
        context.putAttribute(REGISTRATION_PATH, registrationPath)
        context.putAttribute(SEMVER2_ENDPOINT, isSemver2Endpoint)
        val registrationPage = (repository.query(context) as RegistrationPage?) ?: return buildNotFoundResponse()
        return ResponseEntity.ok(registrationPage)
    }

    override fun registrationLeaf(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): ResponseEntity<Any> {
        val repository = ArtifactContextHolder.getRepository()
        val context = ArtifactQueryContext()
        context.putAttribute(QUERY_TYPE, NugetQueryType.REGISTRATION_LEAF)
        context.putAttribute(REGISTRATION_PATH, registrationPath)
        context.putAttribute(SEMVER2_ENDPOINT, isSemver2Endpoint)
        val registrationLeaf = (repository.query(context) as RegistrationLeaf?) ?: return buildNotFoundResponse()
        return ResponseEntity.ok(registrationLeaf)
    }

    private fun buildNotFoundResponse(): ResponseEntity<Any> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND.value)
            .header(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_XML)
            .body(NUGET_V3_NOT_FOUND)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NugetPackageMetadataServiceImpl::class.java)
    }
}
