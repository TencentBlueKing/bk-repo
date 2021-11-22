package com.tencent.bkrepo.oci.service.impl

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.oci.constant.OCI_IMAGE_MANIFEST_MEDIA_TYPE
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.service.OciManifestService
import com.tencent.bkrepo.oci.util.OciResponseUtils
import com.tencent.bkrepo.oci.util.OciUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OciManifestServiceImpl(
	private val repositoryClient: RepositoryClient,
	private val storageManager: StorageManager,
	private val packageClient: PackageClient,
	private val nodeClient: NodeClient
) : OciManifestService, ArtifactService() {

	override fun checkManifestsExists(artifactInfo: OciManifestArtifactInfo) {
		with(artifactInfo) {
			val response = HttpContextHolder.getResponse()
			val manifestPath = OciUtils.buildManifestPath(packageName, tag)
			logger.info("handing request check manifest exists [$manifestPath]")
			// 先检查包是否存在，在检查文件是否存在
			val nodeDetail = nodeClient.getNodeDetail(projectId, repoName, manifestPath).data
			nodeDetail?.let {
				response.status = HttpStatus.OK.value
				response.setContentLengthLong(it.size)
				response.contentType = OCI_IMAGE_MANIFEST_MEDIA_TYPE
				response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
				response.addHeader(DOCKER_CONTENT_DIGEST, OciDigest.fromSha256(it.sha256.orEmpty()).toString())
				response.addHeader(HttpHeaders.ETAG, OciDigest.fromSha256(it.sha256.orEmpty()).toString())
			}
		}
	}

	override fun uploadManifest(artifactInfo: OciManifestArtifactInfo, artifactFile: ArtifactFile) {
		with(artifactInfo) {
			val manifestPath = OciUtils.buildManifestPath(packageName, tag)
			logger.info("handing request upload manifest [$manifestPath]")
			val repoDetail = repositoryClient.getRepoDetail(projectId, repoName).data
				?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, repoName)
			val sha256 = artifactFile.getFileSha256()
			val nodeCreateRequest = NodeCreateRequest(
				projectId = projectId,
				repoName = repoName,
				folder = false,
				fullPath = manifestPath,
				size = artifactFile.getSize(),
				sha256 = sha256,
				md5 = artifactFile.getFileMd5(),
				operator = SecurityUtils.getUserId(),
				overwrite = true
			)
			storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, repoDetail.storageCredentials)
			val request =
				PackageVersionCreateRequest(
					projectId = projectId,
					repoName = repoName,
					packageName = packageName,
					packageKey = PackageKeys.ofOci(packageName),
					packageType = PackageType.OCI,
					packageDescription = null,
					versionName = tag,
					size = artifactFile.getSize(),
					manifestPath = manifestPath,
					artifactPath = null,
					stageTag = null,
					metadata = null,
					overwrite = true,
					createdBy = SecurityUtils.getUserId()
				)
			packageClient.createVersion(request)
			val digest = OciDigest.fromSha256(sha256)
			val location = OciResponseUtils.getResponseLocationURI("$packageName/manifests/${digest}")
			val response = HttpContextHolder.getResponse()
			response.status = HttpStatus.CREATED.value
			response.setContentLength(0)
			response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
			response.addHeader(DOCKER_CONTENT_DIGEST, digest.toString())
			response.addHeader(HttpHeaders.LOCATION, location.toString())
		}
	}

	override fun downloadManifests(artifactInfo: OciManifestArtifactInfo) {
		logger.info("get manifest params [$artifactInfo]")
		val context = ArtifactDownloadContext()
		repository.download(context)
	}

	companion object {
		private val logger = LoggerFactory.getLogger(OciManifestServiceImpl::class.java)
	}
}
