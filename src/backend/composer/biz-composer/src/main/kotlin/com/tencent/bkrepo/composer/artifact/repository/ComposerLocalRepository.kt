package com.tencent.bkrepo.composer.artifact.repository

import com.google.gson.GsonBuilder
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.composer.COMPOSER_VERSION_INIT
import com.tencent.bkrepo.composer.INIT_PACKAGES
import org.springframework.stereotype.Component
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_MD5
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_SHA256
import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.common.artifact.hash.sha256
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import com.tencent.bkrepo.composer.util.DecompressUtil.wrapperJson
import com.tencent.bkrepo.composer.util.JsonUtil
import com.tencent.bkrepo.composer.util.JsonUtil.wrapperJson
import com.tencent.bkrepo.composer.util.JsonUtil.wrapperPackageJson
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.transaction.annotation.Transactional
import java.io.*

@Component
class ComposerLocalRepository : LocalRepository(), ComposerRepository {

    /**
     * Composer节点创建请求
     */
    fun getCompressNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val filename = artifactInfo.artifactUri
        val sha256 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA256] as String
        val md5 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_MD5] as String
        val composerArtifactInfo = artifactInfo as ComposerArtifactInfo

        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                overwrite = true,
                fullPath = "/direct-dists/${filename.removePrefix("/")}",
                size = context.getArtifactFile().getSize(),
                sha256 = sha256,
                md5 = md5,
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
        val sha256 = artifactFile.getInputStream().sha256()
        val md5 = artifactFile.getInputStream().md5()
        val composerArtifactInfo = artifactInfo as ComposerArtifactInfo

        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                overwrite = true,
                fullPath = fullPath,
                size = artifactFile.getSize(),
                sha256 = sha256,
                md5 = md5,
                operator = context.userId,
                metadata = null
        )
    }

    fun getPackagesNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile()
        val sha256 = artifactFile.getInputStream().sha256()
        val md5 = artifactFile.getInputStream().md5()

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

            val bytes = context.getArtifactFile().getInputStream().readBytes()
            val nodeCreateRequest = getCompressNodeCreateRequest(context)
            nodeResource.create(nodeCreateRequest)
            storageService.store(nodeCreateRequest.sha256!!,
                    ArtifactFileFactory.build(ByteInputStream(bytes, bytes.size)), context.storageCredentials)

            ByteInputStream(bytes, bytes.size).wrapperJson(artifactUri)?.let { paramsMap ->
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
                            storageService.load(jsonNode.nodeInfo.sha256!!, Range.ofFull(jsonNode.nodeInfo.size), context.storageCredentials) ?: return
                        }

                        val existsJson = StringBuilder("").let { stringBuilder ->
                            BufferedReader(InputStreamReader(existsJsonFile)).use { bufferedReader ->
                                while (bufferedReader.readLine().also { it?.let {
                                            stringBuilder.append(it)
                                        } } != null) {}
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

    override fun getJson(context: ArtifactSearchContext): String? {
        with(context.artifactInfo as ComposerArtifactInfo) {
            if (artifactUri.matches(Regex("^/p/(.*)\\.json$"))) {
                val request = HttpContextHolder.getRequest()
                val host = "http://${request.remoteHost}:${request.serverPort}/$projectId/$repoName"
                val packageName = artifactUri.removePrefix("/p/").removeSuffix(".json")
                nodeResource.detail(projectId, repoName, artifactUri).data?.let { resultNode ->
                    resultNode.nodeInfo.takeIf { !it.folder }
                    storageService.load(resultNode.nodeInfo.sha256!!, Range.ofFull(resultNode.nodeInfo.size), context.storageCredentials)?.let {
                        StringBuilder("").let { stringBuilder ->
                            BufferedReader(InputStreamReader(it)).use { bufferedReader ->
                                while (bufferedReader.readLine().also { it?.let {
                                            stringBuilder.append(it)
                                        } } != null) {}
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
            // todo http|https
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
                storageService.load(resultNode.nodeInfo.sha256!!, Range.ofFull(resultNode.nodeInfo.size), context.storageCredentials)?.let {
                    StringBuilder("").let { stringBuilder ->
                        BufferedReader(InputStreamReader(it)).use { bufferedReader ->
                            while (bufferedReader.readLine().also { it?.let {
                                        stringBuilder.append(it)
                                    } } != null) {}
                        }
                        return stringBuilder.toString().wrapperPackageJson(host)
                    }
                }
            }
        }
        return null
    }
}
