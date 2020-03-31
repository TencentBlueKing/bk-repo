package com.tencent.bkrepo.helm.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import javax.servlet.http.HttpServletRequest

@Resolver(HelmArtifactInfo::class)
class HelmArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): HelmArtifactInfo {
        return HelmArtifactInfo(projectId, repoName, artifactUri)
    }
}
