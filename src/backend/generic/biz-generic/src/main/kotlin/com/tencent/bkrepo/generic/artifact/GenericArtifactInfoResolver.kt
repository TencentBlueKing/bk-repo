package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import javax.servlet.http.HttpServletRequest

@Resolver(GenericArtifactInfo::class)
class GenericArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): GenericArtifactInfo {
        return GenericArtifactInfo(projectId, repoName, artifactUri)
    }
}
