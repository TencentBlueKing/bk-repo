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
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): PypiArtifactInfo {
        val name = request.getParameter("name")
        val version = request.getParameter("version")
        val filetype = request.getParameter("filetype")
        val coord = "/$name/$version/$filetype"
        return PypiArtifactInfo(projectId, repoName, coord, name, version, filetype)
    }
}
