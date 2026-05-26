package com.tencent.bkrepo.skill.artifact.resolver

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.skill.exception.ClawHubPayloadInvalidException
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubResolveInfo
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
@Resolver(ClawHubResolveInfo::class)
class ClawHubResolveInfoResolver : ArtifactInfoResolver {

    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest,
    ): ArtifactInfo {
        val slug = request.getParameter("slug")!!.trim()
        val hash = request.getParameter("hash")!!.trim().lowercase()
        if (!FINGERPRINT_HASH_PATTERN.matches(hash)) {
            throw ClawHubPayloadInvalidException()
        }
        return ClawHubResolveInfo(projectId, repoName, slug, hash)
    }

    companion object {
        private val FINGERPRINT_HASH_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}
