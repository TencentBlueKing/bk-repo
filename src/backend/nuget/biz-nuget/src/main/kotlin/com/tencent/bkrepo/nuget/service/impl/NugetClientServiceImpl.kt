package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.async.NugetPackageHandler
import com.tencent.bkrepo.nuget.constants.FULL_PATH
import com.tencent.bkrepo.nuget.constants.HTTP_V2_BASE_URL
import com.tencent.bkrepo.nuget.exception.NugetException
import com.tencent.bkrepo.nuget.model.NupkgVersion
import com.tencent.bkrepo.nuget.model.search.NuGetSearchRequest
import com.tencent.bkrepo.nuget.service.NugetClientService
import com.tencent.bkrepo.nuget.util.DecompressUtil.resolverNuspec
import com.tencent.bkrepo.nuget.util.NugetUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class NugetClientServiceImpl(
    private val nugetPackageHandler: NugetPackageHandler
) : NugetClientService {

    override fun getServiceDocument(artifactInfo: NugetArtifactInfo): String {
        return try {
            val serviceDocument = NugetUtils.getServiceDocumentResource()
            // val serviceDocument = inputStream.use { it.toXmlString() }
            serviceDocument.replace(
                "\$\$baseUrl\$\$",
                HTTP_V2_BASE_URL.format(artifactInfo.projectId, artifactInfo.repoName)
            )
        } catch (exception: IOException) {
            logger.error("unable to read resource: $exception")
            StringPool.EMPTY
        }
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    override fun publish(userId: String, artifactInfo: NugetArtifactInfo, artifactFileMap: ArtifactFileMap): String {
        logger.info("user [$userId] handling publish package request in repo [${artifactInfo.getRepoIdentify()}]")
        val artifactFile = artifactFileMap["package"] ?: run {
            throw NugetException("Unable to find 'package' field in request form data.")
        }
        val context = ArtifactUploadContext(artifactFile)
        val nupkgPackage = artifactFile.getInputStream().use { it.resolverNuspec() }
        val nupkgVersion = with(nupkgPackage.metadata) { NupkgVersion(id, version) }
        context.putAttribute(FULL_PATH, nupkgVersion)
        ArtifactContextHolder.getRepository().upload(context)
        nugetPackageHandler.createPackageVersion(userId, artifactInfo, nupkgPackage.metadata, artifactFile.getSize())
        logger.info(
            "user [$userId] publish nuget package [${nupkgVersion.id}] with version [${nupkgVersion.version}] " +
                "success to repo [${artifactInfo.getRepoIdentify()}]"
        )
        artifactFile.delete()
        return "Successfully published NuPkg to: $nupkgVersion"
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    override fun download(userId: String, artifactInfo: NugetArtifactInfo, packageId: String, packageVersion: String) {
        val nupkgFileName = NugetUtils.getNupkgFileName(packageId, packageVersion)
        val context = ArtifactDownloadContext()
        context.putAttribute(FULL_PATH, nupkgFileName)
        ArtifactContextHolder.getRepository().download(context)
    }

    override fun findPackagesById(artifactInfo: NugetArtifactInfo, searchRequest: NuGetSearchRequest) {
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NugetClientServiceImpl::class.java)
    }
}