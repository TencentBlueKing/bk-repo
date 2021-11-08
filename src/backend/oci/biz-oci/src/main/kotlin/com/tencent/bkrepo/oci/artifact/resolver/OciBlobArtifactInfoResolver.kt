package com.tencent.bkrepo.oci.artifact.resolver

import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest

@Component
@Resolver(OciBlobArtifactInfo::class)
class OciBlobArtifactInfoResolver : ArtifactInfoResolver {
	override fun resolve(projectId: String, repoName: String, artifactUri: String, request: HttpServletRequest): ArtifactInfo {
		TODO("Not yet implemented")
	}
}
