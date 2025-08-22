package com.tencent.bkrepo.nuget.artifact.resolver

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.nuget.constant.ID
import com.tencent.bkrepo.nuget.constant.MANIFEST
import com.tencent.bkrepo.nuget.constant.NUSPEC
import com.tencent.bkrepo.nuget.constant.PACKAGE
import com.tencent.bkrepo.nuget.constant.VERSION
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDownloadArtifactInfo
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping

@Component
@Resolver(NugetDownloadArtifactInfo::class)
class NugetDownloadArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): ArtifactInfo {
        val attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
        val id = attributes[ID].toString().trim()
        val version = attributes[VERSION].toString().trim()
        val type = if (request.requestURL.endsWith(NUSPEC)) MANIFEST else PACKAGE
        return NugetDownloadArtifactInfo(projectId, repoName, id, version, type)
    }
}
