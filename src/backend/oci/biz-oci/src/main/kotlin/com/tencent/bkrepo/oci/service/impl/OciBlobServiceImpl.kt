package com.tencent.bkrepo.oci.service.impl

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
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
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
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
		logger.info("handing request check blob [$artifactInfo] exists with digest [${artifactInfo.getDigestHex()}].")
		// digest为空处理
		if (isEmptyBlob(artifactInfo.getDigest())) {
			logger.info("check is empty blob $artifactInfo with digest ${artifactInfo.getDigestHex()}.")
			emptyBlobHeadResponse()
			return
		}
		// digest文件是否存在，先在_uploads目录下查找，找不到再在该仓库下根据sha256值全局查找
		with(artifactInfo) {
			val nodeDetail = queryBlobByFullPathAndDigest(artifactInfo) ?: run {
				logger.info(
					"search blob $this with digest [${this.getDigestHex()}] in repo [${getRepoIdentify()}] failed."
				)
				val response = HttpContextHolder.getResponse()
				response.status = HttpStatus.NOT_FOUND.value
				response.contentType = MediaTypes.APPLICATION_JSON
				response.setHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
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

	private fun queryBlobByFullPathAndDigest(artifactInfo: OciBlobArtifactInfo): NodeDetail? {
		with(artifactInfo) {
			val tempPath = blobTempPath()
			logger.info("search blob in temp path $tempPath in repo [${getRepoIdentify()}] first.")
			val nodeDetail = nodeClient.getNodeDetail(projectId, repoName, tempPath).data
			if (nodeDetail != null) return nodeDetail
			logger.info(
				"attempt to search  blob $this with digest [${this.getDigestHex()}] in repo [${getRepoIdentify()}]."
			)
			val fullPath = queryFullPathByDigest(this)
			return if (fullPath.isEmpty()) null else nodeClient.getNodeDetail(projectId, repoName, fullPath).data
		}
	}

	private fun queryFullPathByDigest(artifactInfo: OciBlobArtifactInfo): String {
		with(artifactInfo) {
			val sha256 = getDigest().getDigestHex()
			val queryModel = NodeQueryBuilder()
				.select("name", "fullPath")
				.sortByAsc("name")
				.page(1, 10)
				.projectId(projectId)
				.repoName(repoName)
				.and()
				.sha256(sha256)
				.excludeFolder()
				.build()
			val result = nodeClient.search(queryModel).data ?: run {
				logger.warn("query blob file with digest [${sha256}] in repo ${getRepoIdentify()} failed.")
				throw NodeNotFoundException("${getRepoIdentify()}/$sha256")
			}
			return result.records.firstOrNull()?.get("fullPath")?.toString().orEmpty()
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
		finishAppend(artifactInfo)
//		if (OciUtils.putHasStream()) {
//			uploadBlobFromPut(artifactInfo, artifactFile)
//		} else {
//			finishAppend(artifactInfo)
//		}
	}

	/**
	 * 完成append追加上传
	 */
	private fun finishAppend(artifactInfo: OciBlobArtifactInfo) {
		with(artifactInfo) {
			logger.debug("handing request finish upload blob [$artifactInfo]")
			val repoDetail = repositoryClient.getRepoDetail(projectId, repoName).data
				?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
			val fileInfo = storageService.finishAppend(uuid, repoDetail.storageCredentials)
			val fullPath = blobTempPath()
			val userId = SecurityUtils.getUserId()
			val nodeCreateRequest = NodeCreateRequest(
				projectId = projectId,
				repoName = repoName,
				folder = false,
				fullPath = fullPath,
				size = fileInfo.size,
				sha256 = fileInfo.sha256,
				md5 = fileInfo.md5,
				operator = userId,
				overwrite = true
			)
			val result = nodeClient.createNode(nodeCreateRequest)

			if (result.isNotOk()) {
				logger.error("user [$userId] finish append file  [$fullPath] failed: [$result]")
				throw ErrorCodeException(
					ArtifactMessageCode.ARTIFACT_RESPONSE_FAILED, fullPath, HttpStatus.INTERNAL_SERVER_ERROR
				)
			}
			val path = "$projectId/$repoName/$packageName/blobs/${getDigest()}"
			val location = OciResponseUtils.getResponseLocationURI(path)
			val response = HttpContextHolder.getResponse()
			response.status = HttpStatus.CREATED.value
			response.setContentLength(0)
			response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
			response.addHeader(HttpHeaders.LOCATION, location.toString())
			response.addHeader(DOCKER_CONTENT_DIGEST, getDigest().toString())
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
			response.setContentLength(0)
			response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
			response.addHeader(DOCKER_CONTENT_DIGEST, getDigest().toString())
			response.addHeader(HttpHeaders.LOCATION, location.toString())
		}
	}

	override fun appendBlobUpload(artifactInfo: OciBlobArtifactInfo, artifactFile: ArtifactFile) {
		logger.info("handing request append upload blob [$artifactInfo].")
		with(artifactInfo) {
			// 如果mount不为空，这里需要处理
			// artifactInfo.mount?.let { return }
			val repoDetail = repositoryClient.getRepoDetail(projectId, repoName).data
				?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
			val appendId = storageService.append(uuid, artifactFile, repoDetail.storageCredentials)
			val startUrl = "$projectId/$repoName/$packageName/blobs/uploads/$uuid"
			val location = OciResponseUtils.getResponseLocationURI(startUrl)
			val response = HttpContextHolder.getResponse()
			response.status = HttpStatus.ACCEPTED.value
			response.setContentLength(0)
			response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
			response.addHeader(DOCKER_UPLOAD_UUID, uuid)
			response.addHeader(HttpHeaders.RANGE, "0-" + (appendId - 1L))
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
