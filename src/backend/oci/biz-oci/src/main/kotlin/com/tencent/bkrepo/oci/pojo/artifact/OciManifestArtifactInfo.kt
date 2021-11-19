package com.tencent.bkrepo.oci.pojo.artifact

class OciManifestArtifactInfo(
	projectId: String,
	repoName: String,
	packageName: String,
	version: String,
	val tag: String
) : OciArtifactInfo(projectId, repoName, packageName, version) {
	private lateinit var manifestPath: String
}
