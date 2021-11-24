package com.tencent.bkrepo.oci.pojo.artifact

import com.tencent.bkrepo.oci.pojo.digest.OciDigest

class OciManifestArtifactInfo(
	projectId: String,
	repoName: String,
	packageName: String,
	version: String,
	val tag: String,
	val digest: String? = null
) : OciArtifactInfo(projectId, repoName, packageName, version) {
	private val ociDigest = OciDigest(digest)

	fun getDigest() = ociDigest

//	override fun getArtifactFullPath(): String  = OciUtils.buildManifestPath(packageName, digest.orEmpty())

	private lateinit var manifestPath: String
}
