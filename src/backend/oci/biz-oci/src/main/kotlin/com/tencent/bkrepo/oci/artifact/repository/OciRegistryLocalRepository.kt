package com.tencent.bkrepo.oci.artifact.repository

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.oci.constant.OCI_IMAGE_MANIFEST_MEDIA_TYPE
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import org.springframework.stereotype.Component

@Component
class OciRegistryLocalRepository : LocalRepository() {
	/**
	 * 在原有逻辑上增加响应头
	 */
	override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
		val response = context.response
		return when (context.artifactInfo) {
			is OciBlobArtifactInfo -> {
				with(context.artifactInfo as OciBlobArtifactInfo) {
					response.status = HttpStatus.OK.value
					response.contentType = MediaTypes.APPLICATION_OCTET_STREAM
					response.addHeader(HttpHeaders.ETAG, getDigest().toString())
					response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
					response.addHeader(DOCKER_CONTENT_DIGEST, getDigest().toString())
					super.onDownload(context)
				}
			}
			is OciManifestArtifactInfo -> {
				with(context.artifactInfo as OciManifestArtifactInfo) {
					response.status = HttpStatus.OK.value
					response.contentType = OCI_IMAGE_MANIFEST_MEDIA_TYPE
					// 设置返回的contentLength
					response.setContentLength(0)
					response.addHeader(HttpHeaders.ETAG, getDigest().toString())
					response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
					response.addHeader(DOCKER_CONTENT_DIGEST, getDigest().toString())
					super.onDownload(context)
				}
			}
			else -> null
		}
	}
}
