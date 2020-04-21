package com.tencent.bkrepo.composer.artifact.repository

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.composer.COMPOSER_VERSION_INIT
import com.tencent.bkrepo.composer.INIT_PACKAGES
import com.tencent.bkrepo.composer.util.DecompressUtil
import com.tencent.bkrepo.composer.util.JsonUtil.jsonValue
import com.tencent.bkrepo.composer.util.UriUtil
import org.apache.commons.fileupload.util.Streams
import org.springframework.stereotype.Component
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_MD5MAP
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_SHA256MAP
import com.tencent.bkrepo.common.artifact.file.MultipartArtifactFile
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import com.tencent.bkrepo.composer.util.JsonUtil
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import java.io.*

@Component
class ComposerLocalRepository: LocalRepository() {

    /**
     * Composer节点创建请求
     */
    override fun getNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.artifactFileMap["octet-stream"]?.getInputStream()
        val filename = artifactInfo.artifactUri
        val map = context.contextAttributes[ATTRIBUTE_SHA256MAP] as LinkedHashMap<*, *>
        val sha256 = map["octet-stream"]
        val md5 = (context.contextAttributes[ATTRIBUTE_MD5MAP] as LinkedHashMap<*, *>)["octet-stream"]
        val composerArtifactInfo = artifactInfo as ComposerArtifactInfo

        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                overwrite = true,
                fullPath = "/${artifactInfo.packageName}/${artifactInfo.version}/${artifactInfo.artifactUri}",
                size = 123,
                sha256 = sha256 as String?,
                md5 = md5 as String?,
                operator = context.userId,
                metadata = composerArtifactInfo.metadata
        )
    }

    fun getJsonNodeCreateRequest(context: ArtifactUploadContext,
                                 artifactFile: ArtifactFile,
    fullPath: String,
    fileName: String): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = artifactFile
        val filename = fileName
        val map = context.contextAttributes[ATTRIBUTE_SHA256MAP] as LinkedHashMap<*, *>
        val sha256 = map["content"]
        val md5 = (context.contextAttributes[ATTRIBUTE_MD5MAP] as LinkedHashMap<*, *>)["content"]
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

    override fun onUpload(context: ArtifactUploadContext) {
        with(context.artifactInfo) {
            val file = context.artifactFileMap["octet-stream"]?.getInputStream()?: return
            //TODO("request uri format friendly")
            UriUtil.getUriArgs(artifactUri.removePrefix("/").removeSuffix("/"))?.let { args ->

                //store compress file
                val nodeCreateRequest = getNodeCreateRequest(context)
                nodeResource.create(nodeCreateRequest)
                context.artifactFileMap["octet-stream"]?.let {
                    storageService.store(nodeCreateRequest.sha256!!,
                            it, context.storageCredentials)
                }

                val fileJson = args["format"]?.let { DecompressUtil.getComposerJson(file, it) }

                //query "/p/%package%.json" node ,if not exists create it.
                val pArtifactUri = "/p/${args["filename"]}/${args["filename"]}.json"
                fileJson?.let {
                    val name = fileJson jsonValue "name"
                    val version = fileJson jsonValue "version"
                    var resultJson: JsonObject? = null
                    val artifactFile = ArtifactFileFactory.build()
                    if(nodeResource.detail(projectId, repoName, pArtifactUri).data != null) {
                        resultJson = JsonUtil.addComposerVersion(String.format(COMPOSER_VERSION_INIT, name), fileJson, name, version)
                    }else{
                        //if "/p/%package%.json" is Exists
                        //load version jsonFile
                        val existsJsonFile = with(context.artifactInfo) {
                            val jsonNode = nodeResource.detail(projectId, repoName, "/p/$name/$name.json").data ?: return
                            jsonNode.nodeInfo.takeIf { !it.folder } ?: return
                            storageService.load(jsonNode.nodeInfo.sha256!!, context.storageCredentials) ?: return
                        }

                        val existsJson = StringBuilder("").let { stringBuilder ->
                            BufferedReader(InputStreamReader(FileInputStream(existsJsonFile))).use { bufferedReader ->
                                var line: String
                                while(bufferedReader.readLine().also { it?.let {
                                            stringBuilder.append(it)
                                        }} != null);
                            }
                            stringBuilder.toString()
                        }

                        //update "/p/%package%.json" 。override by default
                        resultJson = JsonUtil.addComposerVersion(existsJson, fileJson, name, version)
                    }
                    with(context.copy(repositoryInfo = context.repositoryInfo) as ArtifactUploadContext) {
                        ByteArrayInputStream(GsonBuilder().create().toJson(resultJson).toByteArray()).use {
                            Streams.copy(it, artifactFile.getOutputStream(), true)
                        }
                        val nodeCreateRequest = getJsonNodeCreateRequest(context = this,
                                artifactFile = artifactFile,
                                fullPath = "/p/$name/$name.json",
                                fileName = "$name.json"
                        )
                        nodeResource.create(nodeCreateRequest)
                        artifactFile.let {
                            storageService.store(nodeCreateRequest.sha256!!,
                                    it, context.storageCredentials)
                        }
                    }

                }
            }
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): File? {
        with(context.artifactInfo) {
            //if packages.json not exists , create node
            if (artifactUri.removePrefix("/").removeSuffix("/") == "packages.json") {
                val node: Any = nodeResource.detail(projectId, repoName, artifactUri).data ?: {
                    val artifactUploadContext = context.copy(repositoryInfo = context.repositoryInfo) as ArtifactUploadContext
                    val artifactFile = ArtifactFileFactory.build()
                    ByteArrayInputStream(INIT_PACKAGES.toByteArray()).use {
                        Streams.copy(it, artifactFile.getOutputStream(), true)
                    }
                    val nodeCreateRequest = getNodeCreateRequest(context = artifactUploadContext)
                    nodeResource.create(nodeCreateRequest)
                    artifactUploadContext.getArtifactFile().let {
                        storageService.store(nodeCreateRequest.sha256!!,
                                it, context.storageCredentials)
                    }
                }
            }
            val resultNode = nodeResource.detail(projectId, repoName, artifactUri).data ?: return null
            resultNode.nodeInfo.takeIf { !it.folder } ?: return null
            return storageService.load(resultNode.nodeInfo.sha256!!, context.storageCredentials)
        }
    }


}
