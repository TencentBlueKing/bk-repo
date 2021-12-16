package com.tencent.bkrepo.oci.artifact.resolver

import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoResolver
import com.tencent.bkrepo.common.artifact.resolve.path.Resolver
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
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
		val attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
		// 解析tag
		val reference = attributes["reference"].toString().trim()
		validate(packageName, reference)
		val isValidDigest = OciDigest.isValid(reference)
		return OciManifestArtifactInfo(projectId, repoName, packageName, "", reference, isValidDigest)
	}

	private fun validate(packageName: String, reference: String) {
		// packageName格式校验
		Preconditions.checkNotBlank(packageName, "packageName")
		Preconditions.matchPattern(packageName, PACKAGE_NAME_PATTERN, "package name [$packageName] invalid")
		// reference格式校验，只能为digest或者tag
		Preconditions.checkNotBlank(reference, "reference")
		Preconditions.checkArgument(reference.length <= NAME_MAX_LENGTH, reference)
//		Preconditions.matchPattern(packageName, PACKAGE_REFERENCE_PATTERN, "reference [$reference] invalid")
	}

	companion object {
		const val PACKAGE_NAME_PATTERN = "[a-z0-9]+([._-][a-z0-9]+)*(/[a-z0-9]+([._-][a-z0-9]+)*)*"
		const val PACKAGE_REFERENCE_PATTERN = "[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}"
		const val NAME_MAX_LENGTH = 128
	}
}
