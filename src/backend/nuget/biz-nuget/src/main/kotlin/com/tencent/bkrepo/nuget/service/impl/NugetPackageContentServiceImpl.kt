package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.nuget.constant.PACKAGE
import com.tencent.bkrepo.nuget.exception.NugetVersionListNotFoundException
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDownloadArtifactInfo
import com.tencent.bkrepo.nuget.pojo.response.VersionListResponse
import com.tencent.bkrepo.nuget.service.NugetPackageContentService
import org.springframework.stereotype.Service

@Service
class NugetPackageContentServiceImpl : NugetPackageContentService, ArtifactService() {
    override fun downloadPackageContent(artifactInfo: NugetDownloadArtifactInfo) {
        repository.download(ArtifactDownloadContext())
    }

    override fun downloadPackageManifest(artifactInfo: NugetDownloadArtifactInfo) {
        repository.download(ArtifactDownloadContext())
    }

    override fun packageVersions(packageId: String): VersionListResponse {
        val context = ArtifactQueryContext()
        context.putAttribute(PACKAGE, packageId)
        // If the package source has no versions of the provided package ID, a 404 status code is returned.
        val versions = (repository.query(context) as? List<*>)?.map { it.toString() }
            ?: throw NugetVersionListNotFoundException("The specified blob does not exist.")
        return VersionListResponse(versions)
    }
}
