package com.tencent.bkrepo.oci.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_UPLOAD_UUID
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.service.OciBlobService
import com.tencent.bkrepo.oci.util.BlobUtils.isEmptyBlob
import com.tencent.bkrepo.oci.util.OciResponseUtils
import com.tencent.bkrepo.oci.util.OciResponseUtils.emptyBlobHeadResponse
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class OciBlobServiceImpl(
	private val nodeClient: NodeClient,
	private val repositoryClient: RepositoryClient,
	private val storageService: StorageService
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
				// 查询name为ociDigest.getFilename()的node节点，感觉应该要查询fullPath为全路径的节点

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

	override fun startUploadBlob(artifactInfo: OciBlobArtifactInfo) {
		logger.info("handing request start blob $artifactInfo upload.")
		with(artifactInfo) {
			// 如果mount不为空
			// artifactInfo.mount?.let { return }
			val repo = repositoryClient.getRepoDetail(projectId, repoName).data
				?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
			val uuid = storageService.createAppendId(repo.storageCredentials)
			val startUrl = "$projectId/$repoName/$packageName/blobs/uploads/$uuid"
			val location = OciResponseUtils.getResponseLocationURI(startUrl)
			val response = HttpContextHolder.getResponse()
			response.status = HttpStatus.ACCEPTED.value()
			response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
			response.addHeader(DOCKER_UPLOAD_UUID, uuid)
			response.addHeader(HttpHeaders.LOCATION, location.toString())
		}
	}

	companion object {
		private val logger = LoggerFactory.getLogger(OciBlobServiceImpl::class.java)
	}

}
