package com.tencent.bkrepo.oci.artifact.repository

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.oci.constant.OCI_IMAGE_MANIFEST_MEDIA_TYPE
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.slf4j.LoggerFactory
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
					val fullPath = queryFullPathByNameAndDigest(this)
					if (fullPath.isEmpty()) return null
					// 先根据
					with(context) {
						val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
						val inputStream = storageManager.loadArtifactInputStream(node, storageCredentials) ?: return null
						val responseName = artifactInfo.getResponseName()
						return ArtifactResource(inputStream, responseName, node, ArtifactChannel.LOCAL, useDisposition)
					}
				}
			}
			else -> null
		}
	}

	private fun queryFullPathByNameAndDigest(artifactInfo: OciManifestArtifactInfo): String {
		with(artifactInfo) {
			val sha256 = getDigest().getDigestHex()
			val queryModel = NodeQueryBuilder()
				.select( "name", "fullPath")
				.sortByAsc("name")
				.page(1, 10)
				.projectId(projectId)
				.repoName(repoName)
				.and()
				.sha256(sha256)
				.excludeFolder()
				.build()
			val result = nodeClient.search(queryModel).data ?: run {
				logger.warn("query manifest file with digest [${sha256}] in repo ${getRepoIdentify()} failed.")
				throw NodeNotFoundException("${getRepoIdentify()}/$sha256")
			}
			return result.records.firstOrNull()?.get("fullPath")?.toString().orEmpty()
		}
	}

	companion object {
		private val logger = LoggerFactory.getLogger(OciRegistryLocalRepository::class.java)
	}
}
