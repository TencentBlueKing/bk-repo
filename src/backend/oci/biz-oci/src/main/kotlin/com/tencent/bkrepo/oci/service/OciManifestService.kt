package com.tencent.bkrepo.oci.service

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo

interface OciManifestService {
	/**
	 * 根据[artifactInfo]信息来上传manifest文件
	 */
	fun uploadManifest(artifactInfo: OciManifestArtifactInfo, artifactFile: ArtifactFile)
}
