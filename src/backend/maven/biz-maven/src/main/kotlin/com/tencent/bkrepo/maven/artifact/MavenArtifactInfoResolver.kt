package com.tencent.bkrepo.maven.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import javax.servlet.http.HttpServletRequest

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Resolver(MavenArtifactInfo::class)
class MavenArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): MavenArtifactInfo {
        return MavenArtifactInfo(projectId, repoName, artifactUri, "", "", "")
    }
}
