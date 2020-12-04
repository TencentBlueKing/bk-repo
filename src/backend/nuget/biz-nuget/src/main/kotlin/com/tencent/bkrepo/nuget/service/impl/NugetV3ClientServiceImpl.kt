package com.tencent.bkrepo.nuget.service.impl

import com.fasterxml.jackson.core.JsonProcessingException
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.constants.FULL_PATH
import com.tencent.bkrepo.nuget.exception.NugetException
import com.tencent.bkrepo.nuget.model.v3.RegistrationIndex
import com.tencent.bkrepo.nuget.service.NugetV3ClientService
import com.tencent.bkrepo.nuget.util.NugetUtils
import com.tencent.bkrepo.nuget.util.NugetV3RegistrationUtils
import com.tencent.bkrepo.repository.api.PackageClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class NugetV3ClientServiceImpl(
    private val packageClient: PackageClient
) : NugetV3ClientService, NugetAbstractService() {

    override fun getFeed(artifactInfo: NugetArtifactInfo): String {
        return try {
            val feedResource = NugetUtils.getFeedResource()
            feedResource.replace(
                "@NugetV2Url", getV2Url(artifactInfo)
            ).replace(
                "@NugetV3Url", getV3Url(artifactInfo)
            )
        } catch (exception: IOException) {
            logger.error("unable to read resource: $exception")
            StringPool.EMPTY
        }
    }

    override fun registration(
        artifactInfo: NugetArtifactInfo,
        packageId: String,
        registrationPath: String,
        isSemver2Endpoint: Boolean
    ): RegistrationIndex {
        with(artifactInfo) {
            val packageVersionList = packageClient.listVersionPage(projectId, repoName, PackageKeys.ofNuget(packageId)).data!!.records
            if (packageVersionList.isEmpty()){
                throw NugetException("nuget metadata not found for package [$packageId] in repo [${this.getRepoIdentify()}]")
            }
            val metadataList = packageVersionList.map { it.metadata }
            try {
                val v3RegistrationUrl = getV3Url(artifactInfo) + '/' + registrationPath
                return NugetV3RegistrationUtils.metadataToRegistrationIndex(metadataList, v3RegistrationUrl)
            } catch (ignored: JsonProcessingException) {
                logger.error("failed to deserialize metadata to registration index json")
                throw ignored
            }
        }
    }

    // private fun metadataToRegistrationIndex(
    //     metadataList: List<Map<String, Any>>,
    //     v3RegistrationUrl: String
    // ): RegistrationIndex {
    //     val registrationLeafList = metadataList.stream().map {
    //         metadataToRegistrationLeaf(it, v3RegistrationUrl)
    //     }.toList()
    //     // 涉及到分页的问题需要处理
    //     return registrationLeafListToRegistrationIndex(registrationLeafList, v3RegistrationUrl)
    // }
    //
    // private fun registrationLeafListToRegistrationIndex(
    //     registrationLeafList: List<RegistrationLeaf>,
    //     v3RegistrationUrl: String
    // ): RegistrationIndex {
    //     if (registrationLeafList.isEmpty()) {
    //         throw IllegalArgumentException("Cannot build registration with no package version")
    //     } else {
    //         val versionCount = registrationLeafList.size
    //         val pagesCount = versionCount / 64 + if (versionCount % 64 != 0) 1 else 0
    //         val packageId = registrationLeafList[0].catalogEntry.packageId
    //         val registrationPageList =
    //             buildRegistrationPageList(registrationLeafList, v3RegistrationUrl, versionCount, pagesCount, packageId)
    //         return RegistrationIndex(pagesCount, registrationPageList)
    //     }
    // }
    //
    // private fun buildRegistrationPageList(
    //     registrationLeafList: List<RegistrationLeaf>,
    //     v3RegistrationUrl: String,
    //     versionCount: Int,
    //     pagesCount: Int,
    //     packageId: String
    // ): List<RegistrationPage> {
    //     return IntStream.range(0, pagesCount).mapToObj { i ->
    //         val lowerVersion = "1.3.0.12"
    //         val isLastPage = i == pagesCount - 1
    //         val lastPackageIndexInPage = if (isLastPage) versionCount - 1 else 64 * i + 63
    //         val upperVersion = "1.3.0.12"
    //         val packagesInPageCount = lastPackageIndexInPage + 0
    //         RegistrationPage(
    //             URI.create(packageId),
    //             packagesInPageCount,
    //             registrationLeafList,
    //             lowerVersion,
    //             URI.create(v3RegistrationUrl),
    //             upperVersion
    //         )
    //     }.toList()
    // }
    //
    // private fun metadataToRegistrationLeaf(
    //     metadataMap: Map<String, Any>?,
    //     v3RegistrationUrl: String
    // ): RegistrationLeaf {
    //     val writeValueAsString = JsonUtils.objectMapper.writeValueAsString(metadataMap)
    //     val nuspecMetadata = JsonUtils.objectMapper.readValue(writeValueAsString, NuspecMetadata::class.java)
    //     // dependency 需要处理
    //     val catalogEntry = metadataToRegistrationCatalogEntry(nuspecMetadata, v3RegistrationUrl)
    //     return RegistrationLeaf(URI.create("$v3RegistrationUrl/${nuspecMetadata.id}/1.3.0.12.json"), catalogEntry, URI.create("${v3RegistrationUrl.removeSuffix("/registration-semver2")}/flatcontainer/${nuspecMetadata.id}/1.3.0.12/${nuspecMetadata.id}.1.3.0.12.nupkg"), URI.create("$v3RegistrationUrl/${nuspecMetadata.id}/index.json"))
    // }
    //
    // private fun metadataToRegistrationCatalogEntry(
    //     nupkgMetadata: NuspecMetadata,
    //     v3RegistrationUrl: String
    // ): RegistrationCatalogEntry {
    //     with(nupkgMetadata) {
    //         return RegistrationCatalogEntry(
    //             id = URI.create(v3RegistrationUrl),
    //             authors = authors,
    //             dependencyGroups = emptyList(),
    //             deprecation = null,
    //             description = description,
    //             iconUrl = iconUrl?.let { URI.create(it) },
    //             packageId = id,
    //             licenseUrl = licenseUrl?.let { URI.create(it) },
    //             licenseExpression = null,
    //             listed = null,
    //             minClientVersion = minClientVersion,
    //             projectUrl = projectUrl?.let { URI.create(it) },
    //             published = null,
    //             requireLicenseAcceptance = requireLicenseAcceptance,
    //             summary = summary,
    //             tags = emptyList(),
    //             title = title,
    //             version = version
    //         )
    //     }
    // }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    override fun download(artifactInfo: NugetArtifactInfo, packageId: String, packageVersion: String) {
        val nupkgFileName = NugetUtils.getNupkgFileName(packageId, packageVersion)
        val context = ArtifactDownloadContext()
        context.putAttribute(FULL_PATH, nupkgFileName)
        ArtifactContextHolder.getRepository().download(context)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NugetV3ClientServiceImpl::class.java)
    }
}