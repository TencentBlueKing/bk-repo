package com.tencent.bkrepo.media.artifact.resolver

import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.media.artifact.CosArchiveArtifactInfo
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
@Resolver(CosArchiveArtifactInfo::class)
class CosArchiveArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest,
    ): CosArchiveArtifactInfo {
        return CosArchiveArtifactInfo(projectId, repoName, artifactUri)
    }
}
