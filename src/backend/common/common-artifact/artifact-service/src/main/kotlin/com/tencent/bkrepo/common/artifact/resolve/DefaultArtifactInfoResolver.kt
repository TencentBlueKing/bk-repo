package com.tencent.bkrepo.common.artifact.resolve

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactInfoResolver
import javax.servlet.http.HttpServletRequest

/**
 * 默认实现
 *
 * @author: carrypan
 * @date: 2019/12/2
 */
@Resolver(DefaultArtifactInfo::class, default = true)
class DefaultArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(projectId: String, repoName: String, fullPath: String, request: HttpServletRequest): DefaultArtifactInfo {
        return DefaultArtifactInfo(projectId, repoName, fullPath)
    }
}

class DefaultArtifactInfo(projectId: String, repoName: String, fullPath: String) : ArtifactInfo(projectId, repoName, fullPath)
