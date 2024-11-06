package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.constant.NugetQueryType
import com.tencent.bkrepo.nuget.constant.PACKAGE_NAME
import com.tencent.bkrepo.nuget.constant.QUERY_TYPE
import com.tencent.bkrepo.nuget.exception.NugetVersionListNotFoundException
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDownloadArtifactInfo
import com.tencent.bkrepo.nuget.pojo.response.VersionListResponse
import com.tencent.bkrepo.nuget.service.NugetPackageContentService
import org.springframework.stereotype.Service

@Service
class NugetPackageContentServiceImpl : NugetPackageContentService, ArtifactService() {
    override fun downloadPackageContent(artifactInfo: NugetDownloadArtifactInfo) {
        val context = ArtifactDownloadContext()
        repository.download(context)
    }

    override fun downloadPackageManifest(artifactInfo: NugetDownloadArtifactInfo) {
        val context = ArtifactDownloadContext()
        repository.download(context)
    }

    @Suppress("UNCHECKED_CAST")
    override fun packageVersions(artifactInfo: NugetArtifactInfo, packageId: String): VersionListResponse {
        // If the package source has no versions of the provided package ID, a 404 status code is returned.
        val context = ArtifactQueryContext()
        context.putAttribute(QUERY_TYPE, NugetQueryType.PACKAGE_VERSIONS)
        context.putAttribute(PACKAGE_NAME, packageId)
        val repository = ArtifactContextHolder.getRepository()
        val versions = (repository.query(context) as List<String>?).takeUnless { it.isNullOrEmpty() }
            ?: throw NugetVersionListNotFoundException("The specified blob does not exist.")
        return VersionListResponse(versions)
    }
}
