package com.tencent.bkrepo.oci.artifact.resolver

import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerMapping
import javax.servlet.http.HttpServletRequest

@Component
@Resolver(OciManifestArtifactInfo::class)
class OciManifestArtifactInfoResolver : ArtifactInfoResolver {
	override fun resolve(
		projectId: String,
		repoName: String,
		artifactUri: String,
		request: HttpServletRequest
	): ArtifactInfo {
		val requestUrl = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).toString()
		val packageName = requestUrl.substringBeforeLast("/manifests").removePrefix("/v2/$projectId/$repoName/")
		Preconditions.checkNotBlank(packageName, "packageName")
		val attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
		// 解析tag
		val tag = attributes["tag"].toString().trim()
		// 解析digest
		val digest = attributes["digest"]?.toString()?.trim()
		return OciManifestArtifactInfo(projectId, repoName, packageName, "", tag, digest)
	}
}
