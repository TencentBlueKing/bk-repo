package com.tencent.bkrepo.pypi.artifact

import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.pypi.artifact.url.UrlPatternUtil
import javax.servlet.http.HttpServletRequest

@Resolver(PypiArtifactInfo::class)
class PypiArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest
    ): PypiArtifactInfo {
        when (request.method) {
            "POST" -> {
                when (request.getParameter(":action")) {
                    "file_upload" -> {
                        return UrlPatternUtil.fileUpload(projectId, repoName, request)
                    }
                }
            }
        }
        return PypiArtifactInfo(projectId, repoName, artifactUri)
    }
}
