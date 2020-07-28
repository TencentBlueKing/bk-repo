package com.tencent.bkrepo.common.artifact.resolve.path

import com.tencent.bkrepo.common.artifact.api.DefaultArtifactInfo
import javax.servlet.http.HttpServletRequest

/**
 * 默认实现
 *
 * @author: carrypan
 * @date: 2019/12/2
 */
@Resolver(DefaultArtifactInfo::class, default = true)
class DefaultArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): DefaultArtifactInfo {
        return DefaultArtifactInfo(projectId, repoName, artifactUri)
    }
}
