package com.tencent.bkrepo.oci.service

import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import org.springframework.http.ResponseEntity

interface OciBlobService {
	/**
	 * 根据[artifactInfo]的信息来判断blob文件是否存在
	 */
	fun checkBlobExists(artifactInfo: OciBlobArtifactInfo): ResponseEntity<Any>

	/**
	 * 根据[artifactInfo]的信息来上传blob文件，返回appendID
	 */
	fun startUploadBlob(artifactInfo: OciBlobArtifactInfo)
}
