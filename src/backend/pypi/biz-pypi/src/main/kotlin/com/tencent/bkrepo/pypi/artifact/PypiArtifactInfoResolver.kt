package com.tencent.bkrepo.pypi.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import javax.servlet.http.HttpServletRequest

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Resolver(PypiArtifactInfo::class)
class PypiArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): PypiArtifactInfo {
        return PypiArtifactInfo(projectId, repoName, artifactUri, "", "")
    }
}
