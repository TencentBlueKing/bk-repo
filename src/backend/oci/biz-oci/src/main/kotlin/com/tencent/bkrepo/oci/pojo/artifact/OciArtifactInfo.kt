package com.tencent.bkrepo.oci.pojo.artifact

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo

/**
 * oci 构件基本信息
 * 其余场景的ArtifactInfo 可以继承该类，如[OciBlobArtifactInfo]
 */
open class OciArtifactInfo(
		projectId: String,
		repoName: String,
		val packageName: String,
		val version: String
) : ArtifactInfo(projectId, repoName, StringPool.EMPTY) {
	override fun getArtifactName() = packageName

	override fun getArtifactVersion() = version
}
