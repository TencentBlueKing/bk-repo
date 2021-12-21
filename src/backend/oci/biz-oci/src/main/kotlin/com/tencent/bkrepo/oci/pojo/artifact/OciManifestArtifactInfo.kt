package com.tencent.bkrepo.oci.pojo.artifact

import com.tencent.bkrepo.oci.util.OciUtils

class OciManifestArtifactInfo(
	projectId: String,
	repoName: String,
	packageName: String,
	version: String,
	val reference: String,
	val isValidDigest: Boolean
) : OciArtifactInfo(projectId, repoName, packageName, version) {
	override fun getArtifactFullPath(): String = OciUtils.buildManifestPath(packageName, reference)
}
