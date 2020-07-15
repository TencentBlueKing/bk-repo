package com.tencent.bkrepo.rpm.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import javax.servlet.http.HttpServletRequest

@Resolver(RpmArtifactInfo::class)
class RpmArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): ArtifactInfo {
        return RpmArtifactInfo(projectId, repoName, artifactUri)
    }
}
