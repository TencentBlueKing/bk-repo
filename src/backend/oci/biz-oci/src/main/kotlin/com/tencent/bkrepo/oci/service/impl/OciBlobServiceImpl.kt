package com.tencent.bkrepo.oci.service.impl

import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.service.OciBlobService
import com.tencent.bkrepo.oci.util.BlobUtils.isEmptyBlob
import com.tencent.bkrepo.oci.util.OciResponseUtils.emptyBlobHeadResponse
import com.tencent.bkrepo.repository.api.NodeClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class OciBlobServiceImpl(
	private val nodeClient: NodeClient
) : OciBlobService, ArtifactService() {

	override fun checkBlobExists(artifactInfo: OciBlobArtifactInfo): ResponseEntity<Any> {
		logger.info("handing request check blob $artifactInfo with digest ${artifactInfo.getDigestHex()} exists.")
		// digest为空处理
		if (isEmptyBlob(artifactInfo.getDigest())) {
			logger.info("check is empty blob $artifactInfo with digest ${artifactInfo.getDigestHex()}.")
			return emptyBlobHeadResponse()
		}
		// digest文件是否存在，先在_uploads目录下查找，找不到再在该仓库下全局查找
		with(artifactInfo) {
			val tempPath = blobTempPath()
			logger.info("search blob in temp path $tempPath first.")
			val nodeDetail = nodeClient.getNodeDetail(projectId, repoName, tempPath).data
			nodeDetail?.let {
				logger.info("attempt to search  blob $this with digest [${this.getDigestHex()}].")
				// 查询name为ociDigest.getFilename()的node节点

			}

			return ResponseEntity.ok().apply {
				header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
			}.apply {
				header(DOCKER_CONTENT_DIGEST, getDigest().toString())
			}.apply {
				header(HttpHeaders.CONTENT_LENGTH, nodeDetail?.size.toString())
			}.apply {
				header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
			}.build()
		}

	}

	companion object {
		private val logger = LoggerFactory.getLogger(OciBlobServiceImpl::class.java)
	}

}
