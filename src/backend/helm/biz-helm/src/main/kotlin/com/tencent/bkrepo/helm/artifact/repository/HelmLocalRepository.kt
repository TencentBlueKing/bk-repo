package com.tencent.bkrepo.helm.artifact.repository

import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_MD5
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_SHA256
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.util.HttpResponseUtils
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.INDEX_YAML
import com.tencent.bkrepo.helm.constants.INIT_STR
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.util.NodeUtils.FILE_SEPARATOR
import org.apache.commons.fileupload.util.Streams
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class HelmLocalRepository : LocalRepository() {
	override fun download(context: ArtifactDownloadContext) {
		val artifactUri = getNodeFullPath(context)
		val userId = context.userId

		try {
			this.onDownloadValidate(context)
			this.onBeforeDownload(context)
			val file =
					this.onDownload(context) ?: throw ArtifactNotFoundException("Artifact[$artifactUri] does not exist")
			HttpResponseUtils.response(INDEX_YAML, file)
			logger.info("User[$userId] download artifact[$artifactUri] success")
			this.onDownloadSuccess(context, file)
		} catch (validateException: ArtifactValidateException) {
			this.onValidateFailed(context, validateException)
		} catch (exception: Exception) {
			this.onDownloadFailed(context, exception)
		}
	}

	override fun onBeforeDownload(context: ArtifactDownloadContext) {
		//检查index-cache.yaml文件是否存在，如果不存在则说明是添加仓库
		val repositoryInfo = context.repositoryInfo
		val projectId = repositoryInfo.projectId
		val repoName = repositoryInfo.name
		val fullPath = getNodeFullPath(context)
		val exist = nodeResource.exist(projectId, repoName, fullPath)
		if (!exist.data!!) {
			//新建index-cache.yaml文件
			createIndexCacheYamlFile()
		}
	}

	//创建cache-index.yaml文件并初始化
	private fun createIndexCacheYamlFile() {
		val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss+08:00")
		val initStr = String.format(INIT_STR, LocalDateTime.now().format(format))
		val tempFile = createTempFile("index-cache", ".yaml")
		val fw = FileWriter(tempFile)
		try {
            fw.write(initStr)
		} finally {
			//关闭临时文件
			fw.flush()
			fw.close()
			tempFile.deleteOnExit()
		}
        val artifactFile = ArtifactFileFactory.build()
        Streams.copy(tempFile.inputStream(),artifactFile.getOutputStream(),true)
        val uploadContext = ArtifactUploadContext(artifactFile)
        this.upload(uploadContext)
    }

	override fun onUploadValidate(context: ArtifactUploadContext) {
		super.onUploadValidate(context)
		context.artifactFileMap.entries.forEach { (name, file) ->
			if (name == "chart") {
				context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA256] = FileDigestUtils.fileSha256(file.getInputStream())
				context.contextAttributes[ATTRIBUTE_OCTET_STREAM_MD5] = FileDigestUtils.fileMd5(file.getInputStream())
			}
		}
	}

	override fun onUpload(context: ArtifactUploadContext) {
		val nodeCreateRequest = getNodeCreateRequest(context)
		storageService.store(nodeCreateRequest.sha256!!, context.getArtifactFile("chart") ?: context.getArtifactFile(), context.storageCredentials)
		nodeResource.create(nodeCreateRequest)
	}

	override fun getNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
		val repositoryInfo = context.repositoryInfo
		val artifactFile = context.getArtifactFile("chart") ?: context.getArtifactFile()
		val sha256 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA256] as String
		val md5 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_MD5] as String

		return NodeCreateRequest(
				projectId = repositoryInfo.projectId,
				repoName = repositoryInfo.name,
				folder = false,
				fullPath = context.contextAttributes[FULL_PATH] as String,
				size = artifactFile.getSize(),
				sha256 = sha256,
				md5 = md5,
				operator = context.userId
		)
	}

	override fun getNodeFullPath(context: ArtifactDownloadContext): String {
		return "$FILE_SEPARATOR$INDEX_CACHE_YAML"
	}

	companion object {
		val logger: Logger = LoggerFactory.getLogger(HelmLocalRepository::class.java)
	}
}
