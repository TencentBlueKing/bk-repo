package com.tencent.bkrepo.composer.artifact.repository

import com.google.gson.GsonBuilder
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
import java.io.ByteArrayInputStream
import java.io.File
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_MD5MAP
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_SHA256MAP
import com.tencent.bkrepo.common.artifact.file.MultipartArtifactFile
import com.tencent.bkrepo.composer.artifact.ComposerArtifactInfo
import com.tencent.bkrepo.composer.util.JsonUtil
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import java.io.InputStream

@Component
class ComposerLocalRepository: LocalRepository() {

    /**
     * Composer节点创建请求
     */
    override fun getNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile("content")
        val filename = (artifactFile as MultipartArtifactFile).getOriginalFilename()
        val map = context.contextAttributes[ATTRIBUTE_SHA256MAP] as LinkedHashMap<*, *>
        val sha256 = map["content"]
        val md5 = (context.contextAttributes[ATTRIBUTE_MD5MAP] as LinkedHashMap<*, *>)["content"]
        val composerArtifactInfo = artifactInfo as ComposerArtifactInfo

        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                overwrite = true,
                fullPath = "${artifactInfo.packageName}/${artifactInfo.version}",
                size = artifactFile.getSize(),
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
            val file = context.getArtifactFile().getInputStream()
            //TODO("request uri format friendly")
            UriUtil.getUriArgs(artifactUri.removePrefix("/").removeSuffix("/"))?.let { args ->

                //store compress file
                val nodeCreateRequest = getNodeCreateRequest(context)
                nodeResource.create(nodeCreateRequest)
                context.getArtifactFile().let {
                    storageService.store(nodeCreateRequest.sha256!!,
                            it, context.storageCredentials)
                }

                //query "/p/%package%.json" node ,if not exists create it.
                val pArtifactUri = "/p/${args["filename"]}.json"
                nodeResource.detail(projectId, repoName, pArtifactUri).data?.let {
                    firstUploadWithName(context, args, file)
                }
                //if "/p/%package%.json" is Exists
                updatePackageJson(context, args, file)
            }
            val jsonStr = DecompressUtil.getZipComposerJson(file)
        }
    }

    fun firstUploadWithName(context: ArtifactUploadContext, args: HashMap<String, String>, file: InputStream) {
        //get uploadFile json content
        val fileJson = args["format"]?.let { DecompressUtil.getComposerJson(file, it) }
        fileJson?.let { fileJson ->
            val name = fileJson jsonValue "name"
            val version = fileJson jsonValue "version"
            //init version json
            val resultJsonObject = JsonUtil.addComposerVersion(String.format(COMPOSER_VERSION_INIT, name), fileJson, name, version)
            //store "/p/%package%.json"
            val artifactFile = ArtifactFileFactory.build()
            with(context.copy(repositoryInfo = context.repositoryInfo) as ArtifactUploadContext) {
                ByteArrayInputStream(GsonBuilder().create().toJson(resultJsonObject).toByteArray()).use {
                    Streams.copy(it, artifactFile.getOutputStream(), true)
                }
                val nodeCreateRequest = getJsonNodeCreateRequest(context = this,
                        artifactFile = artifactFile,
                        fullPath = "/p/$name.json",
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


    fun updatePackageJson(context: ArtifactUploadContext, args: HashMap<String, String>, file: InputStream) {
        //get uploadFile json content
        val fileJson = args["format"]?.let { DecompressUtil.getComposerJson(file, it) }
        fileJson?.let { fileJson ->
            val name = fileJson jsonValue "name"
            val version = fileJson jsonValue "version"
            //load version json
            with(context.artifactInfo) {
                val jsonNode = nodeResource.detail(projectId, repoName, artifactUri).data ?: return null
                resultNode.nodeInfo.takeIf { !it.folder } ?: return null
                return storageService.load(resultNode.nodeInfo.sha256!!, context.storageCredentials)
            }

            //store "/p/%package%.json"
            val artifactFile = ArtifactFileFactory.build()
            with(context.copy(repositoryInfo = context.repositoryInfo) as ArtifactUploadContext) {
                ByteArrayInputStream(GsonBuilder().create().toJson(resultJsonObject).toByteArray()).use {
                    Streams.copy(it, artifactFile.getOutputStream(), true)
                }
                val nodeCreateRequest = getJsonNodeCreateRequest(context = this,
                        artifactFile = artifactFile,
                        fullPath = "/p/$name.json",
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
