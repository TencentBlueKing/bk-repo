package com.tencent.bkrepo.helm.artifact.repository

import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_MD5
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_SHA256
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.util.HttpResponseUtils
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.helm.constants.CHART_NOT_FOUND
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.INDEX_YAML
import com.tencent.bkrepo.helm.constants.INIT_STR
import com.tencent.bkrepo.helm.utils.JsonUtil
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.util.NodeUtils.FILE_SEPARATOR
import org.apache.commons.fileupload.util.Streams
import org.apache.commons.lang.StringUtils
import org.apache.http.HttpStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
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
            HttpResponseUtils.response(determineFileName(context), file)
            logger.info("User[$userId] download artifact[$artifactUri] success")
            this.onDownloadSuccess(context, file)
        } catch (validateException: ArtifactValidateException) {
            this.onValidateFailed(context, validateException)
        } catch (exception: Exception) {
            this.onDownloadFailed(context, exception)
        }
    }

    fun determineFileName(context: ArtifactDownloadContext): String {
        val fileName = context.artifactInfo.artifactUri.trimStart('/')
        return if (StringUtils.isBlank(fileName)) INDEX_YAML else fileName
    }

    override fun onBeforeDownload(context: ArtifactDownloadContext) {
        // 检查index-cache.yaml文件是否存在，如果不存在则说明是添加仓库
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        val exist = nodeResource.exist(projectId, repoName, fullPath)
        if (!exist.data!!) {
            // 新建index-cache.yaml文件
            createIndexCacheYamlFile()
        }
    }

    // 创建cache-index.yaml文件并初始化
    private fun createIndexCacheYamlFile() {
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss+08:00")
        val initStr = String.format(INIT_STR, LocalDateTime.now().format(format))
        val tempFile = createTempFile("index-cache", ".yaml")
        val fw = FileWriter(tempFile)
        try {
            fw.write(initStr)
        } finally {
            // 关闭临时文件
            fw.flush()
            fw.close()
            tempFile.deleteOnExit()
        }
        val artifactFile = ArtifactFileFactory.build()
        Streams.copy(tempFile.inputStream(), artifactFile.getOutputStream(), true)
        val uploadContext = ArtifactUploadContext(artifactFile)
        uploadContext.contextAttributes[FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
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
        storageService.store(nodeCreateRequest.sha256!!, context.getArtifactFile("chart")
                ?: context.getArtifactFile(), context.storageCredentials)
        nodeResource.create(nodeCreateRequest)
    }

    override fun getNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile("chart") ?: context.getArtifactFile()
        val sha256 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA256] as String
        val md5 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_MD5] as String
        val fullPath = context.contextAttributes[FULL_PATH] as String
        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                fullPath = fullPath,
                size = artifactFile.getSize(),
                sha256 = sha256,
                md5 = md5,
                operator = context.userId,
                overwrite = !fullPath.trim().endsWith(".tgz", true)
        )
    }

    override fun getNodeFullPath(context: ArtifactDownloadContext): String {
        return context.contextAttributes[FULL_PATH] as String
    }

    override fun search(context: ArtifactSearchContext): File? {
        val fullPath = context.contextAttributes[FULL_PATH] as String
        return try {
            this.onSearch(context) ?: throw ArtifactNotFoundException("Artifact[$fullPath] does not exist")
        } catch (exception: Exception) {
            logger.error(exception.message ?: "search error")
            null
        }
    }

    private fun onSearch(context: ArtifactSearchContext): File? {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = context.contextAttributes[FULL_PATH] as String
        val node = nodeResource.detail(projectId, repoName, fullPath).data ?: return null

        node.nodeInfo.takeIf { !it.folder } ?: return null
        return storageService.load(node.nodeInfo.sha256!!, context.storageCredentials) ?: return null
    }

    override fun remove(context: ArtifactRemoveContext) {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = context.contextAttributes[FULL_PATH] as String
        val userId = context.userId
        nodeResource.delete(NodeDeleteRequest(projectId, repoName, fullPath, userId))
    }

    fun searchJson(context: ArtifactSearchContext): String {
        val artifactInfo = context.artifactInfo
        val fullPath = INDEX_CACHE_YAML
        with(artifactInfo) {
            val node = nodeResource.detail(projectId, repoName, fullPath).data ?: return CHART_NOT_FOUND
            val indexYamlFile = storageService.load(node.nodeInfo.sha256!!, context.storageCredentials)
                    ?: return CHART_NOT_FOUND
            return JsonUtil.searchJson(indexYamlFile, artifactUri)
        }
    }

    fun isExists(context: ArtifactSearchContext) {
        val artifactInfo = context.artifactInfo
        val response = HttpContextHolder.getResponse()
        val status: Int = with(artifactInfo) {
            val projectId = Rule.QueryRule("projectId", projectId)
            val repoName = Rule.QueryRule("repoName", repoName)
            val urlList = artifactUri.removePrefix("/").split("/").filter { it.isNotBlank() }
            val rule:Rule? = when (urlList.size) {
                // query with name
                1 -> {
                    val name = Rule.QueryRule("name", urlList[0])
                    Rule.NestedRule(
                        mutableListOf(repoName, projectId, name),
                        Rule.NestedRule.RelationType.AND
                    )
                }
                // query with name and version
                2 -> {
                    val name = Rule.QueryRule("name", urlList[0])
                    val version = Rule.QueryRule("version", urlList[1])
                    Rule.NestedRule(
                        mutableListOf(repoName, projectId, name, version),
                        Rule.NestedRule.RelationType.AND
                    )
                }
                else -> {
                    null
                }
            }
            rule?.let {
                val queryModel = QueryModel(
                page = PageLimit(0, 5),
                sort = Sort(listOf("name"), Sort.Direction.ASC),
                select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
                rule = it
                )
                val nodeList: List<Map<String, Any>>? = nodeResource.query(queryModel).data?.records
                if (nodeList.isNullOrEmpty()) HttpStatus.SC_NOT_FOUND else HttpStatus.SC_OK
            }
            HttpStatus.SC_NOT_FOUND
        }
        response.status = status
    }

     companion object {
        val logger: Logger = LoggerFactory.getLogger(HelmLocalRepository::class.java)
    }
}
