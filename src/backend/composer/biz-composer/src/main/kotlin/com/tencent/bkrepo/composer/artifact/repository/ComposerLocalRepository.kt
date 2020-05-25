package com.tencent.bkrepo.composer.artifact.repository

import com.google.gson.GsonBuilder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.composer.COMPOSER_VERSION_INIT
import com.tencent.bkrepo.composer.INIT_PACKAGES
import org.springframework.stereotype.Component
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_MD5
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_SHA256
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import com.tencent.bkrepo.composer.util.DecompressUtil.wrapperJson
import com.tencent.bkrepo.composer.util.JsonUtil
import com.tencent.bkrepo.composer.util.JsonUtil.wrapperJson
import com.tencent.bkrepo.composer.util.JsonUtil.wrapperPackageJson
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.File
import org.springframework.transaction.annotation.Transactional

@Component
class ComposerLocalRepository : LocalRepository(),ComposerRepository {

    /**
     * Composer节点创建请求
     */
    fun getCompressNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val filename = artifactInfo.artifactUri
        val sha256 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA256]
        val md5 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_MD5]
        val composerArtifactInfo = artifactInfo as ComposerArtifactInfo

        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                overwrite = true,
                fullPath = "/direct-dists/$filename",
                size = 123,
                sha256 = sha256 as String?,
                md5 = md5 as String?,
                operator = context.userId,
                metadata = null
        )
    }

    fun getJsonNodeCreateRequest(
        context: ArtifactUploadContext,
        artifactFile: ArtifactFile,
        fullPath: String,
        fileName: String
    ): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = artifactFile
        val filename = fileName
        val sha256 = FileDigestUtils.fileSha256(artifactFile.getInputStream())
        val md5 = FileDigestUtils.fileMd5(artifactFile.getInputStream())
        val composerArtifactInfo = artifactInfo as ComposerArtifactInfo

        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                overwrite = true,
                fullPath = fullPath,
                size = artifactFile.getSize(),
                sha256 = sha256 as String?,
                md5 = md5 as String?,
                operator = context.userId,
                metadata = null
        )
    }

    fun getPackagesNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile()
        val sha256 = FileDigestUtils.fileSha256(artifactFile.getInputStream())
        val md5 = FileDigestUtils.fileMd5(artifactFile.getInputStream())

        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                overwrite = true,
                fullPath = artifactInfo.artifactUri,
                size = artifactFile.getSize(),
                sha256 = sha256,
                md5 = md5,
                operator = context.userId
        )
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun onUpload(context: ArtifactUploadContext) {
        with(context.artifactInfo as ComposerArtifactInfo) {

            // store compress file
            val nodeCreateRequest = getCompressNodeCreateRequest(context)
            nodeResource.create(nodeCreateRequest)
            storageService.store(nodeCreateRequest.sha256!!,
                        context.getArtifactFile(), context.storageCredentials)

            val fileInputStream = context.artifactFileMap["octet-stream"]?.getInputStream()
            fileInputStream?.wrapperJson(artifactUri)?.let { paramsMap ->
                val packageName = paramsMap["packageName"]
                val version = paramsMap["version"]
                val json = paramsMap["json"]
                // query "/p/%package%.json" node ,if not exists create it.
                val pArtifactUri = "/p/$packageName.json"
                json?.let { json -> packageName?.let { packageName -> version?.let { version ->
                    val resultJson = if (nodeResource.detail(projectId, repoName, pArtifactUri).data == null) {
                        JsonUtil.addComposerVersion(String.format(COMPOSER_VERSION_INIT, packageName), json, packageName, version)
                    } else {
                        // if "/p/%package%.json" is Exists
                        // load version jsonFile
                        val existsJsonFile = with(context.artifactInfo) {
                            val jsonNode = nodeResource.detail(projectId, repoName, "/p/$packageName.json").data ?: return
                            jsonNode.nodeInfo.takeIf { !it.folder } ?: return
                            storageService.load(jsonNode.nodeInfo.sha256!!, context.storageCredentials) ?: return
                        }

                        val existsJson = StringBuilder("").let { stringBuilder ->
                            BufferedReader(InputStreamReader(FileInputStream(existsJsonFile))).use { bufferedReader ->
                                while (bufferedReader.readLine().also { it?.let {
                                            stringBuilder.append(it)
                                        } } != null){}
                            }
                            stringBuilder.toString()
                        }

                        // update "/p/%package%.json" 。override by default
                        JsonUtil.addComposerVersion(existsJson, json, packageName, version)
                    }
                    val byteArrayInputStream = ByteArrayInputStream(GsonBuilder().create().toJson(resultJson).toByteArray())
                    val jsonFile = ArtifactFileFactory.build(byteArrayInputStream)
                    ArtifactUploadContext(jsonFile).let { jsonUploadContext ->
                        val jsonNodeCreateRequest = getJsonNodeCreateRequest(context = jsonUploadContext,
                                artifactFile = jsonFile,
                                fullPath = "/p/$packageName.json",
                                fileName = "${packageName.split("/").last()}.json"
                        )
                        nodeResource.create(jsonNodeCreateRequest)
                        jsonFile.let {
                            storageService.store(jsonNodeCreateRequest.sha256!!,
                                    it, context.storageCredentials)
                        }
                    }
                } } }
            }
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): File? {
        with(context.artifactInfo) {
            val resultNode = nodeResource.detail(projectId, repoName, artifactUri).data ?: return null
            resultNode.nodeInfo.takeIf { !it.folder } ?: return null
            return storageService.load(resultNode.nodeInfo.sha256!!, context.storageCredentials)
        }
    }

    override fun getJson(context: ArtifactSearchContext): String? {
        with(context.artifactInfo as ComposerArtifactInfo) {
            if (artifactUri.matches(Regex("^/p/(.*)\\.json$"))) {
                val request = HttpContextHolder.getRequest()
                val host = "http://${request.remoteHost}:${request.serverPort}/$projectId/$repoName"
                val packageName = artifactUri.removePrefix("/p/").removeSuffix(".json")
                nodeResource.detail(projectId, repoName, artifactUri).data?.let { resultNode ->
                    resultNode.nodeInfo.takeIf { !it.folder }
                    storageService.load(resultNode.nodeInfo.sha256!!, context.storageCredentials)?.let {
                        StringBuilder("").let { stringBuilder ->
                            BufferedReader(InputStreamReader(FileInputStream(it))).use { bufferedReader ->
                                while (bufferedReader.readLine().also { it?.let {
                                            stringBuilder.append(it)
                                        } } != null){}
                            }
                            return stringBuilder.toString().wrapperJson(host, packageName)
                        }
                    }
                }
            }
        }
        return null
    }

    override fun packages(context: ArtifactSearchContext): String? {
        with(context.artifactInfo) {
            val request = HttpContextHolder.getRequest()
            val host = "http://${request.remoteHost}:${request.serverPort}/$projectId/$repoName"
            while (nodeResource.detail(projectId, repoName, artifactUri).data == null) {
                val byteArrayInputStream = ByteArrayInputStream(INIT_PACKAGES.toByteArray())
                val artifactFile = ArtifactFileFactory.build(byteArrayInputStream)
                val artifactUploadContext = ArtifactUploadContext(artifactFile)
                val nodeCreateRequest = getPackagesNodeCreateRequest(context = artifactUploadContext)
                nodeResource.create(nodeCreateRequest)
                artifactUploadContext.getArtifactFile().let {
                    storageService.store(nodeCreateRequest.sha256!!,
                            it, context.storageCredentials)
                }
            }
            nodeResource.detail(projectId, repoName, artifactUri).data?.let { resultNode ->
                resultNode.nodeInfo.takeIf { !it.folder }
                storageService.load(resultNode.nodeInfo.sha256!!, context.storageCredentials)?.let {
                    StringBuilder("").let { stringBuilder ->
                        BufferedReader(InputStreamReader(FileInputStream(it))).use { bufferedReader ->
                            while (bufferedReader.readLine().also { it?.let {
                                        stringBuilder.append(it)
                                    } } != null){}
                        }
                        return stringBuilder.toString().wrapperPackageJson(host)
                    }
                }
            }
        }
        return null
    }
}
