package com.tencent.bkrepo.oci.service.impl

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_UPLOAD_UUID
import com.tencent.bkrepo.oci.pojo.artifact.OciArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.service.OciBlobService
import com.tencent.bkrepo.oci.util.BlobUtils.isEmptyBlob
import com.tencent.bkrepo.oci.util.OciResponseUtils
import com.tencent.bkrepo.oci.util.OciResponseUtils.emptyBlobHeadResponse
import com.tencent.bkrepo.oci.util.OciUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

@Service
class OciBlobServiceImpl(
	private val nodeClient: NodeClient,
	private val repositoryClient: RepositoryClient,
	private val storageService: StorageService,
	private val storageManager: StorageManager
) : OciBlobService, ArtifactService() {

	override fun checkBlobExists(artifactInfo: OciBlobArtifactInfo) {
		logger.info("handing request check blob $artifactInfo with digest ${artifactInfo.getDigestHex()} exists.")
		// digest为空处理
		if (isEmptyBlob(artifactInfo.getDigest())) {
			logger.info("check is empty blob $artifactInfo with digest ${artifactInfo.getDigestHex()}.")
			emptyBlobHeadResponse()
			return
		}
		// digest文件是否存在，先在_uploads目录下查找，找不到再在该仓库下全局查找
		with(artifactInfo) {
			val tempPath = blobTempPath()
			logger.info("search blob in temp path $tempPath first.")
			val nodeDetail = nodeClient.getNodeDetail(projectId, repoName, tempPath).data
			nodeDetail?.let {
				logger.info("attempt to search  blob $this with digest [${this.getDigestHex()}].")
				// 查询name为ociDigest.getFilename()的node节点，感觉应该要查询fullPath为全路径的节点
			} ?: run {
				val response = HttpContextHolder.getResponse()
				response.status = HttpStatus.NOT_FOUND.value
				response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
				response.contentType = MediaTypes.APPLICATION_JSON
				response.setContentLength(157)
				return
			}
			val response = HttpContextHolder.getResponse()
			response.status = HttpStatus.OK.value
			response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
			response.addHeader(DOCKER_CONTENT_DIGEST, getDigest().toString())
			response.addHeader(HttpHeaders.CONTENT_LENGTH, nodeDetail.size.toString())
			response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
		}
	}

	override fun startUploadBlob(artifactInfo: OciArtifactInfo) {
		logger.info("handing request start blob $artifactInfo upload.")
		with(artifactInfo) {
			// 如果mount不为空，这里需要处理
			// artifactInfo.mount?.let { return }
			val repo = repositoryClient.getRepoDetail(projectId, repoName).data
				?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
			val uuid = storageService.createAppendId(repo.storageCredentials)
			val startUrl = "$projectId/$repoName/$packageName/blobs/uploads/$uuid"
			val location = OciResponseUtils.getResponseLocationURI(startUrl)
			val response = HttpContextHolder.getResponse()
			response.status = HttpStatus.ACCEPTED.value
			response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
			response.addHeader(DOCKER_UPLOAD_UUID, uuid)
			response.addHeader(HttpHeaders.RANGE, "0-0")
			response.addHeader(HttpHeaders.CONTENT_LENGTH, "0")
			response.addHeader(HttpHeaders.LOCATION, location.toString())
		}
	}

	override fun uploadBlob(artifactInfo: OciBlobArtifactInfo, artifactFile: ArtifactFile) {
		if (OciUtils.putHasStream()) {
			uploadBlobFromPut(artifactInfo, artifactFile)
		} else {
			TODO()
//			finishPatchUpload(context, digest, uuid)
		}
	}

	/**
	 * 上传blob文件
	 */
	private fun uploadBlobFromPut(artifactInfo: OciBlobArtifactInfo, artifactFile: ArtifactFile) {
		with(artifactInfo) {
			logger.info("deploy docker blob [${blobTempPath()}] into [${getRepoIdentify()}]")
			val repoDetail = repositoryClient.getRepoDetail(projectId, repoName).data
				?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
			val nodeCreateRequest = NodeCreateRequest(
				projectId = projectId,
				repoName = repoName,
				folder = false,
				fullPath = blobTempPath(),
				size = artifactFile.getSize(),
				sha256 = artifactFile.getFileSha256(),
				md5 = artifactFile.getFileMd5(),
				operator = SecurityUtils.getUserId(),
				overwrite = true
			)
			storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, repoDetail.storageCredentials)
			val location = OciResponseUtils.getResponseLocationURI("$packageName/blobs/${getDigest()}")
			val response = HttpContextHolder.getResponse()
			response.status = HttpStatus.CREATED.value
			response.addHeader(HttpHeaders.CONTENT_LENGTH, "0")
			response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
			response.addHeader(DOCKER_CONTENT_DIGEST, getDigest().toString())
			response.addHeader(HttpHeaders.LOCATION, location.toString())
		}
	}

	override fun downloadBlob(artifactInfo: OciBlobArtifactInfo) {
		with(artifactInfo) {
			if (isEmptyBlob(getDigest())) {
				logger.info("get empty layer [$artifactInfo] for image [${getDigest()}]")
				emptyBlobHeadResponse()
				return
			}
			val context = ArtifactDownloadContext()
			repository.download(context)
		}
	}

	companion object {
		private val logger = LoggerFactory.getLogger(OciBlobServiceImpl::class.java)
	}

}
