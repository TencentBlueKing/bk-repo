package com.tencent.bkrepo.oci.pojo.artifact

import com.tencent.bkrepo.oci.pojo.digest.OciDigest

/**
 * oci blob信息
 */
class OciBlobArtifactInfo(
	projectId: String,
	repoName: String,
	packageName: String,
	version: String,
	private val digest: String,
	private val uuid: String
) : OciArtifactInfo(projectId, repoName, packageName, version) {
	private val ociDigest = OciDigest(digest)

	fun getDigestAlg(): String {
		return ociDigest.getDigestAlg()
	}

	fun getDigestHex(): String {
		return ociDigest.getDigestHex()
	}
}
