package com.tencent.bkrepo.pypi.artifact

import com.tencent.bkrepo.common.artifact.api.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import org.springframework.util.AntPathMatcher
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Resolver(PackagesArtifactInfo::class)
class PackagesArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): PackagesArtifactInfo {
        var originalArtifactUri = request.let {
            AntPathMatcher.DEFAULT_PATH_SEPARATOR + AntPathMatcher().extractPathWithinPattern(
                request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as String,
                request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE) as String
            )
        }
        originalArtifactUri = if(originalArtifactUri.isNotBlank()) originalArtifactUri else "/"
        return PackagesArtifactInfo(projectId, repoName, originalArtifactUri)
    }
}
