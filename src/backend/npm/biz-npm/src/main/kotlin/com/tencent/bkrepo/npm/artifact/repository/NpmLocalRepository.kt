package com.tencent.bkrepo.npm.artifact.repository

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_MD5MAP
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_SHA256MAP
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.util.HttpResponseUtils
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.npm.constants.APPLICATION_OCTET_STEAM
import com.tencent.bkrepo.npm.constants.ATTRIBUTE_OCTET_STREAM_SHA1
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_METADATA
import com.tencent.bkrepo.npm.constants.NPM_PACKAGE_TGZ_FILE
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.util.NodeUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStreamReader

@Component
class NpmLocalRepository : LocalRepository() {

    override fun onUploadValidate(context: ArtifactUploadContext) {
        super.onUploadValidate(context)
        context.artifactFileMap.entries.forEach { (name, file) ->
            if (name == NPM_PACKAGE_TGZ_FILE) {
                // 校验MIME_TYPE
                context.contextAttributes[APPLICATION_OCTET_STEAM].takeIf { it == MediaType.APPLICATION_OCTET_STREAM_VALUE }
                    ?: throw ArtifactValidateException("Request MIME_TYPE is not ${MediaType.APPLICATION_OCTET_STREAM_VALUE}")
                // 计算sha1并校验
                val calculatedSha1 = FileDigestUtils.fileSha1(listOf(file.getInputStream()))
                val uploadSha1 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA1] as String?
                if (uploadSha1 != null && calculatedSha1 != uploadSha1) {
                    throw ArtifactValidateException("File shasum validate failed.")
                }
            }
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        context.artifactFileMap.entries.forEach { (name, _) ->
            val nodeCreateRequest = getNodeCreateRequest(name, context)
            nodeResource.create(nodeCreateRequest)
            storageService.store(
                nodeCreateRequest.sha256!!,
                context.getArtifactFile(name)!!,
                context.storageCredentials
            )
        }
    }

    private fun getNodeCreateRequest(name: String, context: ArtifactUploadContext): NodeCreateRequest {
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile(name)
        val contextAttributes = context.contextAttributes
        val fileSha256Map = context.contextAttributes[ATTRIBUTE_SHA256MAP] as Map<String, String>
        val fileMd5Map = context.contextAttributes[ATTRIBUTE_MD5MAP] as Map<String, String>
        val sha256 = fileSha256Map[name]
        val md5 = fileMd5Map[name]

        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            fullPath = contextAttributes[name + "_full_path"] as String,
            size = artifactFile?.getSize(),
            sha256 = sha256,
            md5 = md5,
            operator = context.userId,
            metadata = parseMetaData(name, contextAttributes),
            overwrite = name != NPM_PACKAGE_TGZ_FILE
        )
    }

    fun parseMetaData(name: String, contextAttributes: MutableMap<String, Any>): Map<String, String> {
        return if (name == NPM_PACKAGE_TGZ_FILE) contextAttributes[NPM_METADATA] as Map<String, String> else emptyMap()
    }

    override fun download(context: ArtifactDownloadContext) {
        val artifactUri = getNodeFullPath(context)
        val userId = context.userId

        try {
            this.onDownloadValidate(context)
            this.onBeforeDownload(context)
            val file =
                this.onDownload(context) ?: throw ArtifactNotFoundException("Artifact[$artifactUri] does not exist")
            val name = NodeUtils.getName(getNodeFullPath(context))
            HttpResponseUtils.response(name, file)
            logger.info("User[$userId] download artifact[$artifactUri] success")
            this.onDownloadSuccess(context, file)
        } catch (validateException: ArtifactValidateException) {
            this.onValidateFailed(context, validateException)
        } catch (exception: Exception) {
            this.onDownloadFailed(context, exception)
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): File? {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = getNodeFullPath(context)
        val node = nodeResource.detail(projectId, repoName, fullPath).data ?: return null

        node.nodeInfo.takeIf { !it.folder } ?: return null
        return storageService.load(node.nodeInfo.sha256!!, context.storageCredentials)
    }

    override fun getNodeFullPath(context: ArtifactDownloadContext): String {
        return context.contextAttributes[NPM_FILE_FULL_PATH] as String
    }

    override fun search(context: ArtifactSearchContext): JsonObject? {
        val fullPath = context.contextAttributes[NPM_FILE_FULL_PATH] as String
        return try {
            this.onSearch(context) ?: throw ArtifactNotFoundException("Artifact[$fullPath] does not exist")
        } catch (exception: Exception) {
            logger.error(exception.message ?: "search error")
            return null
        }
    }

    private fun onSearch(context: ArtifactSearchContext): JsonObject? {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = context.contextAttributes[NPM_FILE_FULL_PATH] as String
        val node = nodeResource.detail(projectId, repoName, fullPath).data ?: return null

        node.nodeInfo.takeIf { !it.folder } ?: return null
        val file = storageService.load(node.nodeInfo.sha256!!, context.storageCredentials) ?: return null
        return GsonUtils.gson.fromJson<JsonObject>(
            InputStreamReader(file.inputStream()),
            object : TypeToken<JsonObject>() {}.type
        )
    }

    override fun remove(context: ArtifactRemoveContext) {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = context.contextAttributes[NPM_FILE_FULL_PATH] as List<String>
        val userId = context.userId
        fullPath.forEach {
            nodeResource.delete(NodeDeleteRequest(projectId, repoName, it, userId))
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NpmLocalRepository::class.java)
    }
}
