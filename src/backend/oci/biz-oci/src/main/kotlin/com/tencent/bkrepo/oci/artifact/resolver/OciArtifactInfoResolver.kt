package com.tencent.bkrepo.oci.artifact.resolver

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.oci.pojo.artifact.OciArtifactInfo
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

@Component
@Resolver(OciArtifactInfo::class)
class OciArtifactInfoResolver : ArtifactInfoResolver {
	override fun resolve(
		projectId: String,
		repoName: String,
		artifactUri: String,
		request: HttpServletRequest
	): ArtifactInfo {
		val requestUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
		val packageName = requestUrl.replaceAfterLast("/blobs", StringPool.EMPTY).removeSuffix("/blobs")
				.removePrefix("/v2/$projectId/$repoName/")
		Preconditions.checkNotBlank(packageName, "packageName")
		return OciArtifactInfo(projectId, repoName, packageName, "")
	}
}
