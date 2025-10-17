package com.tencent.bkrepo.media.job.config

import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.media.job.pojo.MediaArtifactInfo
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest

@Component
@Resolver(MediaArtifactInfo::class)
class MediaArtifactInfoResolver : ArtifactInfoResolver {
    override fun resolve(
        projectId: String,
        repoName: String,
        artifactUri: String,
        request: HttpServletRequest,
    ): MediaArtifactInfo {
        return MediaArtifactInfo(projectId, repoName, "/streams$artifactUri")
    }
}
