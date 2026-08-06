package com.tencent.bkrepo.skill.artifact.resolver

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.skill.pojo.artifact.ClawHubSkillModerationInfo
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping

@Component
@Resolver(ClawHubSkillModerationInfo::class)
class ClawHubSkillModerationInfoResolver : ArtifactInfoResolver {

    @Suppress("UNCHECKED_CAST")
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest,
    ): ArtifactInfo {
        val attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
        val slug = attributes["slug"]!!.toString().trim()
        return ClawHubSkillModerationInfo(projectId, repoName, slug)
    }
}
