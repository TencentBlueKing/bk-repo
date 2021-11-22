package com.tencent.bkrepo.oci.service

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.oci.pojo.artifact.OciArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo

interface OciBlobService {
	/**
	 * 根据[artifactInfo]的信息来判断blob文件是否存在
	 */
	fun checkBlobExists(artifactInfo: OciBlobArtifactInfo)

	/**
	 * 根据[artifactInfo]的信息来上传blob文件，返回appendID
	 */
	fun startUploadBlob(artifactInfo: OciArtifactInfo)

	/**
	 * 根据[artifactInfo]的信息来上传[artifactFile]文件
	 */
	fun uploadBlob(artifactInfo: OciBlobArtifactInfo, artifactFile: ArtifactFile)

	/**
	 * 根据[artifactInfo]的信息来上传[artifactFile]文件
	 */
	fun appendBlobUpload(artifactInfo: OciBlobArtifactInfo, artifactFile: ArtifactFile)

	/**
	 * 根据[artifactInfo]的信息下载blob文件
	 */
	fun downloadBlob(artifactInfo: OciBlobArtifactInfo)
}
