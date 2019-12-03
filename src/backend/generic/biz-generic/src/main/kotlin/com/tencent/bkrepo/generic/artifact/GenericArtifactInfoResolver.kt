package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.Resolver
import javax.servlet.http.HttpServletRequest

/**
 *
 * @author: carrypan
 * @date: 2019/11/28
 */
@Resolver(GenericArtifactInfo::class)
class GenericArtifactInfoResolver: ArtifactInfoResolver {
    override fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): GenericArtifactInfo {
        return GenericArtifactInfo(projectId, repoName, artifactUri, artifactUri)
    }
}