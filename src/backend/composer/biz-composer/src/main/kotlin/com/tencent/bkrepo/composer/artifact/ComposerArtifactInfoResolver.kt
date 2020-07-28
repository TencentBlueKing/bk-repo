package com.tencent.bkrepo.composer.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import io.undertow.servlet.spec.HttpServletRequestImpl
import javax.servlet.http.HttpServletRequest

@Resolver(ComposerArtifactInfo::class)
class ComposerArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): ArtifactInfo {
        // 包名
        val uri = request.servletPath.removePrefix("/$projectId/$repoName")
        // todo 用户设置的参数,暂未发现作用
        val parameters = (request as HttpServletRequestImpl).exchange.pathParameters
        return ComposerArtifactInfo(projectId, repoName, uri)
    }
}
