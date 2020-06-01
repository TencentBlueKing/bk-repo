package com.tencent.bkrepo.helm.artifact.repository

import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_MD5MAP
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_SHA256MAP
import com.tencent.bkrepo.common.artifact.config.OCTET_STREAM
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.helm.constants.EMPTY_CHART_OR_VERSION
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.INDEX_YAML
import com.tencent.bkrepo.helm.constants.INIT_STR
import com.tencent.bkrepo.helm.exception.HelmFileAlreadyExistsException
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.helm.utils.JsonUtil
import com.tencent.bkrepo.helm.utils.YamlUtils
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.util.NodeUtils.FILE_SEPARATOR
import org.apache.commons.lang.StringUtils
import org.apache.http.HttpStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class HelmLocalRepository : LocalRepository() {

    override fun onDownloadBefore(context: ArtifactDownloadContext) {
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

    override fun determineArtifactName(context: ArtifactTransferContext): String {
        val fileName = context.artifactInfo.artifactUri.trimStart('/')
        return if (StringUtils.isBlank(fileName)) INDEX_YAML else fileName
    }

    // 创建cache-index.yaml文件并初始化
    private fun createIndexCacheYamlFile() {
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss+08:00")
        val initStr = String.format(INIT_STR, LocalDateTime.now().format(format))
        val artifactFile = ArtifactFileFactory.build(initStr.byteInputStream())
        val uploadContext = ArtifactUploadContext(artifactFile)
        uploadContext.contextAttributes[OCTET_STREAM + FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        this.upload(uploadContext)
    }

    override fun onUploadBefore(context: ArtifactUploadContext) {
        // 判断是否是强制上传
        val isForce = context.request.getParameter("force")?.let { true } ?: false
        context.contextAttributes["force"] = isForce
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        context.artifactFileMap.entries.forEach { (name, _) ->
            val fullPath = context.contextAttributes[name + "_full_path"] as String
            val isExist = nodeResource.exist(projectId, repoName, fullPath).data!!
            if (isExist && !isOverwrite(fullPath, isForce)) {
                throw HelmFileAlreadyExistsException("${fullPath.trimStart('/')} already exists")
            }
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        context.artifactFileMap.entries.forEach { (name, _) ->
            val nodeCreateRequest = getNodeCreateRequest(name, context)
            storageService.store(
                nodeCreateRequest.sha256!!, context.getArtifactFile(name)
                    ?: context.getArtifactFile(), context.storageCredentials
            )
            nodeResource.create(nodeCreateRequest)
        }
    }

    private fun getNodeCreateRequest(name: String, context: ArtifactUploadContext): NodeCreateRequest {
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile(name) ?: context.getArtifactFile()
        val fileSha256Map = context.contextAttributes[ATTRIBUTE_SHA256MAP] as Map<*, *>
        val fileMd5Map = context.contextAttributes[ATTRIBUTE_MD5MAP] as Map<*, *>
        val sha256 = fileSha256Map[name] as String
        val md5 = fileMd5Map[name] as String
        val fullPath = context.contextAttributes[name + "_full_path"] as String
        val isForce = context.contextAttributes["force"] as Boolean
        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            fullPath = fullPath,
            size = artifactFile.getSize(),
            sha256 = sha256,
            md5 = md5,
            operator = context.userId,
            metadata = parseMetaData(fullPath, isForce),
            overwrite = isOverwrite(fullPath, isForce)
        )
    }

    private fun parseMetaData(fullPath: String, isForce: Boolean): Map<String, String>? {
        if (isOverwrite(fullPath, isForce) || !fullPath.endsWith(".tgz")) {
            return emptyMap()
        }
        val substring = fullPath.trimStart('/').substring(0, fullPath.lastIndexOf('.') - 1)
        val name = substring.substringBeforeLast('-')
        val version = substring.substringAfterLast('-')
        return mapOf("name" to name, "version" to version)
    }

    private fun isOverwrite(fullPath: String, isForce: Boolean): Boolean {
        return isForce || !(fullPath.trim().endsWith(".tgz", true) || fullPath.trim().endsWith(".prov", true))
    }

    override fun determineArtifactUri(context: ArtifactDownloadContext): String {
        return context.contextAttributes[FULL_PATH] as String
    }

    override fun search(context: ArtifactSearchContext): Map<String, Any>? {
        val fullPath = context.contextAttributes[FULL_PATH] as String
        return try {
            this.onSearch(context) ?: throw ArtifactNotFoundException("Artifact[$fullPath] does not exist")
        } catch (exception: Exception) {
            logger.error(exception.message ?: "search error")
            null
        }
    }

    private fun onSearch(context: ArtifactSearchContext): Map<String, Any>? {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = context.contextAttributes[FULL_PATH] as String

        if (fullPath.contains(INDEX_CACHE_YAML)) {
            val indexFilePath = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
            val exist = nodeResource.exist(projectId, repoName, indexFilePath)
            if (!exist.data!!) {
                // 新建index-cache.yaml文件
                createIndexCacheYamlFile()
            }
        }
        val node = nodeResource.detail(projectId, repoName, fullPath).data ?: return null

        node.nodeInfo.takeIf { !it.folder } ?: return null
        return storageService.load(node.nodeInfo.sha256!!, Range.ofFull(node.nodeInfo.size), context.storageCredentials)?.run {
            YamlUtils.convertFileToEntity<Map<String, Any>>(this)
        }
    }

    override fun remove(context: ArtifactRemoveContext) {
        val repositoryInfo = context.repositoryInfo
        val projectId = repositoryInfo.projectId
        val repoName = repositoryInfo.name
        val fullPath = context.contextAttributes[FULL_PATH] as String
        val userId = context.userId
        val isExist = nodeResource.exist(projectId, repoName, fullPath).data!!
        if (!isExist) {
            throw HelmFileNotFoundException("remove $fullPath failed: no such file or directory")
        }
        nodeResource.delete(NodeDeleteRequest(projectId, repoName, fullPath, userId))
    }

    fun searchJson(context: ArtifactSearchContext): String {
        val artifactInfo = context.artifactInfo
        val fullPath = INDEX_CACHE_YAML
        with(artifactInfo) {
            val node = nodeResource.detail(projectId, repoName, fullPath).data ?: return EMPTY_CHART_OR_VERSION
            val inputStream = storageService.load(node.nodeInfo.sha256!!, Range.ofFull(node.nodeInfo.size), context.storageCredentials)
                ?: return EMPTY_CHART_OR_VERSION
            return JsonUtil.searchJson(inputStream, artifactUri)
        }
    }

    fun isExists(context: ArtifactSearchContext) {
        val artifactInfo = context.artifactInfo
        val response = HttpContextHolder.getResponse()
        val status: Int = with(artifactInfo) {
            val projectId = Rule.QueryRule("projectId", projectId)
            val repoName = Rule.QueryRule("repoName", repoName)
            val urlList = artifactUri.removePrefix("/").split("/").filter { it.isNotBlank() }
            val rule: Rule? = when (urlList.size) {
                // query with name
                1 -> {
                    val name = Rule.QueryRule("metadata.name", urlList[0])
                    Rule.NestedRule(
                        mutableListOf(repoName, projectId, name),
                        Rule.NestedRule.RelationType.AND
                    )
                }
                // query with name and version
                2 -> {
                    val name = Rule.QueryRule("metadata.name", urlList[0])
                    val version = Rule.QueryRule("metadata.version", urlList[1])
                    Rule.NestedRule(
                        mutableListOf(repoName, projectId, name, version),
                        Rule.NestedRule.RelationType.AND
                    )
                }
                else -> {
                    null
                }
            }
            if (rule != null) {
                val queryModel = QueryModel(
                    page = PageLimit(0, 5),
                    sort = Sort(listOf("name"), Sort.Direction.ASC),
                    select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
                    rule = rule
                )
                val nodeList: List<Map<String, Any>>? = nodeResource.query(queryModel).data?.records
                if (nodeList.isNullOrEmpty()) HttpStatus.SC_NOT_FOUND else HttpStatus.SC_OK
            } else {
                HttpStatus.SC_NOT_FOUND
            }
        }
        response.status = status
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HelmLocalRepository::class.java)
    }
}
