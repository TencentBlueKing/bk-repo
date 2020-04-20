package com.tencent.bkrepo.composer.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.composer.artifact.url.UrlPatternUtil
import javax.servlet.http.HttpServletRequest

@Resolver(ComposerArtifactInfo::class)
class ComposerArtifactInfoResolver: ArtifactInfoResolver {
    override fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): ArtifactInfo {
        when (request.method) {
            "PUT" -> {
                    return UrlPatternUtil.fileUpload(projectId, repoName, artifactUri, request)
                }
            }
        return ComposerArtifactInfo(projectId, repoName, artifactUri, null, null, null)
    }
}
