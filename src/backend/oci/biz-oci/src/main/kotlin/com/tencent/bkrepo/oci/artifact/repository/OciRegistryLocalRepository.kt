package com.tencent.bkrepo.oci.artifact.repository

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.oci.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.oci.constant.DOCKER_CONTENT_DIGEST
import com.tencent.bkrepo.oci.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.oci.constant.MANIFEST
import com.tencent.bkrepo.oci.constant.OCI_IMAGE_MANIFEST_MEDIA_TYPE
import com.tencent.bkrepo.oci.model.Descriptor
import com.tencent.bkrepo.oci.model.ManifestSchema2
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.pojo.digest.OciDigest
import com.tencent.bkrepo.oci.util.BlobUtils.EMPTY_BLOB_CONTENT
import com.tencent.bkrepo.oci.util.BlobUtils.isEmptyBlob
import com.tencent.bkrepo.oci.util.OciResponseUtils
import com.tencent.bkrepo.oci.util.OciUtils
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeRenameRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream

@Component
class OciRegistryLocalRepository : LocalRepository() {

	/**
	 * 上传
	 */
	override fun onUpload(context: ArtifactUploadContext) {
		when (context.artifactInfo) {
			is OciManifestArtifactInfo -> {
				// 上传manifest文件，需要将blob文件合并，并且创建包
				with(context.artifactInfo as OciManifestArtifactInfo) {
					val artifactFile = context.getArtifactFile()
					val manifest = JsonUtils.objectMapper.readValue(
						artifactFile.getInputStream(), ManifestSchema2::class.java
					)
					uploadManifest(context)
					syncBlob(context, manifest)
					createVersion(context)
					val digest = OciDigest.fromSha256(artifactFile.getFileSha256())
					val location = OciResponseUtils.getResponseLocationURI("$packageName/manifests/${digest}")
					val response = context.response
					response.status = HttpStatus.CREATED.value
					response.setContentLength(0)
					response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
					response.addHeader(DOCKER_CONTENT_DIGEST, digest.toString())
					response.addHeader(HttpHeaders.LOCATION, location.toString())
				}
			}
		}
	}

	/**
	 * 创建包
	 */
	private fun createVersion(context: ArtifactUploadContext) {
		with(context.artifactInfo as OciManifestArtifactInfo) {
			val contentType = HeaderUtils.getHeader(HttpHeaders.CONTENT_TYPE).orEmpty()
			val metadata = mutableMapOf(
				"content-type" to contentType,
				"sha1sum" to context.getArtifactSha1(),
				"sha256sum" to context.getArtifactSha256()
			)
			val request =
				PackageVersionCreateRequest(
					projectId = projectId,
					repoName = repoName,
					packageName = packageName,
					packageKey = PackageKeys.ofOci(packageName),
					packageType = PackageType.OCI,
					versionName = reference,
					size = context.getArtifactFile().getSize(),
					artifactPath = getArtifactFullPath(),
					metadata = metadata,
					overwrite = true,
					createdBy = context.userId
				)
			packageClient.createVersion(request)
		}
	}

	/**
	 * 同步blob层的数据和config里面的数据
	 */
	private fun syncBlob(context: ArtifactUploadContext, manifest: ManifestSchema2) {
		logger.info("start to sync repository blobs [${manifest.toJsonString()}]")
		val manifestConfig = manifest.config
		val manifestLayer = manifest.layers.iterator()
		while (manifestLayer.hasNext()) {
			val layerBlobInfo = manifestLayer.next()
			doSyncBlob(layerBlobInfo, context)
		}
		// 同步config blob
		doSyncBlob(manifestConfig, context)
		logger.info("finish sync docker repository blobs")
	}

	private fun doSyncBlob(layerBlobInfo: Descriptor, context: ArtifactUploadContext) {
		with(context.artifactInfo as OciManifestArtifactInfo) {
			logger.info("sync docker blob digest [${layerBlobInfo.digest}]")
			// check digest
			if (layerBlobInfo.digest.isEmpty() || !OciDigest.isValid(layerBlobInfo.digest)) {
				logger.info("blob format error [$layerBlobInfo]")
				return
			}
			val blobDigest = OciDigest(layerBlobInfo.digest)
			val fileName = blobDigest.fileName()
			val tempPath = "/$packageName/_uploads/$fileName"
			val finalPath = "/$packageName/$reference/$fileName"
			// check path exist
			if (checkExists(context.projectId, context.repoName, finalPath)) {
				logger.info("node [$finalPath] exist in the repo [${getRepoIdentify()}]")
				return
			}
			// check is empty digest
			if (isEmptyBlob(blobDigest)) {
				logger.info("found empty layer [$fileName] in manifest  ,create blob in path [$finalPath]")
				val blobContent = ByteArrayInputStream(EMPTY_BLOB_CONTENT)
				val artifactFile = ArtifactFileFactory.build(blobContent)
				val request = buildEmptyBlobCreateRequest(this, artifactFile, finalPath)
				storageManager.storeArtifactFile(request, artifactFile, context.repositoryDetail.storageCredentials)
				artifactFile.delete()
				return
			}
			// temp path exist, move from it to final
			if (checkExists(context.projectId, context.repoName, tempPath)) {
				logger.info("move blob from the temp path [$tempPath] to final path [$finalPath] in repo [${getRepoIdentify()}].")
				if (!moveNode(context, tempPath, finalPath)) {
					logger.warn("move blob failed [$finalPath]")
					//throw DockerFileSaveFailedException(finalPath)
				}
				return
			}
			// final copy from other blob
			logger.info("blob temp file [$tempPath] doesn't exist in temp, try to copy")
			if (!copyBlobFromRepo(context, fileName, finalPath)) {
				logger.warn("copy file from other path failed [$finalPath]")
				//					throw DockerFileSaveFailedException(finalPath)
			}
		}
	}

	private fun copyBlobFromRepo(context: ArtifactUploadContext, fileName: String, finalPath: String): Boolean {
		with(context) {
			val searchNodeList = searchNode(projectId, repoName, name = fileName)
			if (searchNodeList.isEmpty()) return false
			val nodeInfoMap = searchNodeList.first()
			val sourcePath = nodeInfoMap["fullPath"]?.toString().orEmpty()
			if (sourcePath == finalPath) return true
			return copyNode(context, sourcePath, finalPath)
		}
	}

	private fun copyNode(context: ArtifactUploadContext, from: String, to: String): Boolean {
		with(context) {
			val copyRequest = NodeMoveCopyRequest(
				srcProjectId = projectId,
				srcRepoName = repoName,
				srcFullPath = from,
				destProjectId = projectId,
				destRepoName = repoName,
				destFullPath = to,
				overwrite = true,
				operator = userId
			)
			val result = nodeClient.copyNode(copyRequest)
			if (result.isNotOk()) {
				logger.error("user [$userId] request [$copyRequest] copy file fail")
				return false
			}
			return true
		}
	}

	private fun moveNode(context: ArtifactUploadContext, from: String, to: String): Boolean {
		with(context) {
			val renameRequest = NodeRenameRequest(projectId, repoName, from, to, userId)
			logger.debug("move request [$renameRequest]")
			val result = nodeClient.renameNode(renameRequest)
			if (result.isNotOk()) {
				logger.error("user [$userId] request [$renameRequest] move file fail")
				return false
			}
			return true
		}
	}

	private fun checkExists(projectId: String, repoName: String, fullPath: String): Boolean {
		return nodeClient.checkExist(projectId, repoName, fullPath).data ?: false
	}

	private fun buildEmptyBlobCreateRequest(
		artifactInfo: OciManifestArtifactInfo,
		artifactFile: ArtifactFile,
		fullPath: String
	): NodeCreateRequest {
		return NodeCreateRequest(
			projectId = artifactInfo.projectId,
			repoName = artifactInfo.repoName,
			folder = false,
			fullPath = fullPath,
			size = artifactFile.getSize(),
			sha256 = artifactFile.getFileSha256(),
			md5 = artifactFile.getFileMd5(),
			operator = SecurityUtils.getUserId()
		)
	}

	/**
	 * 保存manifest文件内容
	 */
	private fun uploadManifest(context: ArtifactUploadContext) {
		val request = buildNodeCreateRequest(context).copy(overwrite = true)
		storageManager.storeArtifactFile(request, context.getArtifactFile(), context.storageCredentials)
	}

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
					val fullPath = queryBlobFullPathByNameAndDigest(this)
					if (fullPath.isEmpty()) return null
					val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
					val inputStream = storageManager.loadArtifactInputStream(node, context.storageCredentials) ?: return null
					val responseName = getResponseName()
					return ArtifactResource(inputStream, responseName, node, ArtifactChannel.LOCAL, context.useDisposition)
				}
			}
			is OciManifestArtifactInfo -> {
				with(context.artifactInfo as OciManifestArtifactInfo) {
					val fullPath = if (isValidDigest) {
						queryManifestFullPathByNameAndDigest(this)
					} else {
						OciUtils.buildManifestPath(packageName, reference)
					}
					if (fullPath.isEmpty()) return null
					with(context) {
						val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
						val inputStream = storageManager.loadArtifactInputStream(node, storageCredentials) ?: return null
						val responseName = artifactInfo.getResponseName()
						response.status = HttpStatus.OK.value
						// 设置返回的contentLength
						response.setContentLength(0)
						val digest = OciDigest.fromSha256(node?.sha256.orEmpty())
						response.addHeader(HttpHeaders.ETAG, digest.toString())
						response.addHeader(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION)
						response.addHeader(DOCKER_CONTENT_DIGEST, digest.toString())
						val resource =  ArtifactResource(inputStream, responseName, node, ArtifactChannel.LOCAL, useDisposition)
						resource.contentType = OCI_IMAGE_MANIFEST_MEDIA_TYPE
						return resource
					}
				}
			}
			else -> null
		}
	}


	private fun queryManifestFullPathByNameAndDigest(artifactInfo: OciManifestArtifactInfo): String {
		with(artifactInfo) {
			val digest = OciDigest(reference)
			val sha256 = digest.getDigestHex()
			val searchNodeList =
				searchNode(projectId, repoName, sha256 = sha256, name = MANIFEST, fullPathPrefix = packageName)
			if (searchNodeList.isEmpty()) {
				logger.warn("query manifest file with digest [$sha256] in repo ${getRepoIdentify()} failed.")
				throw NodeNotFoundException("${getRepoIdentify()}/$sha256")
			}
			return searchNodeList.firstOrNull()?.get("fullPath")?.toString().orEmpty()
		}
	}

	private fun queryBlobFullPathByNameAndDigest(artifactInfo: OciBlobArtifactInfo): String {
		with(artifactInfo) {
			val searchNodeList =
				searchNode(projectId, repoName, sha256 = getDigestHex(), fullPathPrefix = packageName)
			if (searchNodeList.isEmpty()) {
				logger.warn("query blob file with name [$packageName] in repo ${getRepoIdentify()} failed.")
				throw NodeNotFoundException("${getRepoIdentify()}/$packageName")
			}
			return searchNodeList.firstOrNull()?.get("fullPath")?.toString().orEmpty()
		}
	}

	private fun searchNode(
		projectId: String,
		repoName: String,
		sha256: String? = null,
		name: String? = null,
		fullPathPrefix: String? = null
	): List<Map<String, Any?>> {
		val queryModel = NodeQueryBuilder()
			.select("name", "fullPath")
			.sortByAsc("name")
			.page(1, 10)
			.projectId(projectId)
			.repoName(repoName)
			.excludeFolder()
		sha256?.let { queryModel.and().sha256(it) }
		name?.let { queryModel.and().name(it) }
		fullPathPrefix?.let { queryModel.and().fullPath("/$it", OperationType.PREFIX) }
		val result = nodeClient.search(queryModel.build()).data ?: run {
			logger.warn("node not found in repo [$projectId/$repoName]")
			return emptyList()
		}
		return result.records
	}

	companion object {
		private val logger = LoggerFactory.getLogger(OciRegistryLocalRepository::class.java)
	}
}
