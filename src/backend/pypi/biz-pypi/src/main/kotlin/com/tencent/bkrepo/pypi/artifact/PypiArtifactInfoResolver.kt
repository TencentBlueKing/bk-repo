package com.tencent.bkrepo.pypi.artifact

import com.alibaba.fastjson.JSON
import com.tencent.bkrepo.common.artifact.api.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.pypi.artifact.url.UrlPatternUtil
import javax.servlet.http.HttpServletRequest

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Resolver(PypiArtifactInfo::class)
class PypiArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): PypiArtifactInfo {
        return UrlPatternUtil.urlPattern(projectId, repoName, artifactUri, request)
    }
}
