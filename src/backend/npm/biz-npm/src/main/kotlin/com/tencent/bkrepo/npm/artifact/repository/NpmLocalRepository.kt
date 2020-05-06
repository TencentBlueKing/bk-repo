package com.tencent.bkrepo.npm.artifact.repository

import com.google.gson.JsonObject
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_MD5MAP
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_SHA256MAP
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.util.response.ServletResponseUtils
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.npm.constants.APPLICATION_OCTET_STEAM
import com.tencent.bkrepo.npm.constants.ATTRIBUTE_OCTET_STREAM_SHA1
import com.tencent.bkrepo.npm.constants.AUTHOR
import com.tencent.bkrepo.npm.constants.DATE
import com.tencent.bkrepo.npm.constants.DESCRIPTION
import com.tencent.bkrepo.npm.constants.DIST
import com.tencent.bkrepo.npm.constants.ID
import com.tencent.bkrepo.npm.constants.KEYWORDS
import com.tencent.bkrepo.npm.constants.LAST_MODIFIED_DATE
import com.tencent.bkrepo.npm.constants.MAINTAINERS
import com.tencent.bkrepo.npm.constants.METADATA
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.NPM_FILE_FULL_PATH
import com.tencent.bkrepo.npm.constants.NPM_METADATA
import com.tencent.bkrepo.npm.constants.NPM_PACKAGE_TGZ_FILE
import com.tencent.bkrepo.npm.constants.NPM_PKG_TGZ_FULL_PATH
import com.tencent.bkrepo.npm.constants.PACKAGE
import com.tencent.bkrepo.npm.constants.SEARCH_REQUEST
import com.tencent.bkrepo.npm.constants.TARBALL
import com.tencent.bkrepo.npm.constants.VERSION
import com.tencent.bkrepo.npm.constants.VERSIONS
import com.tencent.bkrepo.npm.pojo.NpmSearchResponse
import com.tencent.bkrepo.npm.pojo.metadata.MetadataSearchRequest
import com.tencent.bkrepo.npm.utils.GsonUtils
import com.tencent.bkrepo.repository.api.MetadataResource
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.util.NodeUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import java.io.File

@Component
class NpmLocalRepository : LocalRepository() {

    @Autowired
    lateinit var metadataResource: MetadataResource

    @Value("\${npm.tarball.prefix}")
    private val tarballPrefix: String = StringPool.SLASH

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
            ServletResponseUtils.response(name, file)
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
        return getPkgInfo(context, file)
    }

    private fun getPkgInfo(context: ArtifactSearchContext, file: File): JsonObject {
        val projectId = context.artifactInfo.projectId
        val repoName = context.artifactInfo.repoName
        val fileJson = GsonUtils.transferFileToJson(file)
        val containsVersion = fileJson[ID].asString.substring(1).contains('@')
        if (containsVersion) {
            val name = fileJson.get(NAME).asString
            val version = fileJson[VERSION].asString
            val tgzFullPath = String.format(NPM_PKG_TGZ_FULL_PATH, name, name, version)
            val metadataInfo =
                metadataResource.query(context.artifactInfo.projectId, context.artifactInfo.repoName, tgzFullPath).data
            metadataInfo?.forEach { (key, value) ->
                if (StringUtils.isNotBlank(value)) fileJson.addProperty(key, value)
                if (key == KEYWORDS || key == MAINTAINERS) fileJson.add(key, GsonUtils.stringToArray(value))
            }
            val oldTarball = fileJson.getAsJsonObject(DIST)[TARBALL].asString
            val prefix = oldTarball.split(name)[0].trimEnd('/')
            val newTarball = oldTarball.replace(prefix, tarballPrefix.trimEnd('/').plus("/$projectId").plus("/$repoName"))
            fileJson.getAsJsonObject(DIST).addProperty(TARBALL, newTarball)
        } else {
            val name = fileJson.get(NAME).asString
            val versions = fileJson.getAsJsonObject(VERSIONS)
            versions.keySet().forEach {
                val tgzFullPath = String.format(NPM_PKG_TGZ_FULL_PATH, name, name, it)
                val metadataInfo =
                    metadataResource.query(context.artifactInfo.projectId, context.artifactInfo.repoName, tgzFullPath)
                        .data
                metadataInfo?.forEach { (key, value) ->
                    if (StringUtils.isNotBlank(value)) versions.getAsJsonObject(it).addProperty(key, value)
                    if (key == KEYWORDS || key == MAINTAINERS) versions.getAsJsonObject(it).add(
                        key,
                        GsonUtils.stringToArray(value)
                    )
                }
                val versionObject = versions.getAsJsonObject(it)
                val oldTarball = versionObject.getAsJsonObject(DIST)[TARBALL].asString
                val prefix = oldTarball.split(name)[0].trimEnd('/')
                val newTarball = oldTarball.replace(prefix, tarballPrefix.trimEnd('/').plus("/$projectId").plus("/$repoName"))
                versionObject.getAsJsonObject(DIST).addProperty(TARBALL, newTarball)
            }
        }
        return fileJson
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

    override fun list(context: ArtifactListContext): NpmSearchResponse {
        val searchRequest = context.contextAttributes[SEARCH_REQUEST] as MetadataSearchRequest
        val projectId = Rule.QueryRule("projectId", context.repositoryInfo.projectId)
        val repoName = Rule.QueryRule("repoName", context.repositoryInfo.name)
        val fullPath = Rule.QueryRule("fullPath", ".tgz", OperationType.SUFFIX)

        val nameMd = Rule.QueryRule("metadata.name", searchRequest.text, OperationType.MATCH)
        val descMd = Rule.QueryRule("metadata.description", searchRequest.text, OperationType.MATCH)
        val maintainerMd = Rule.QueryRule("metadata.maintainers", searchRequest.text, OperationType.MATCH)
        val versionMd = Rule.QueryRule("metadata.version", searchRequest.text, OperationType.MATCH)
        val keywordsMd = Rule.QueryRule("metadata.keywords", searchRequest.text, OperationType.MATCH)
        val metadata = Rule.NestedRule(
            mutableListOf(nameMd, descMd, maintainerMd, versionMd, keywordsMd),
            Rule.NestedRule.RelationType.OR
        )
        val rule = Rule.NestedRule(mutableListOf(projectId, repoName, fullPath, metadata))
        val queryModel = QueryModel(
            page = PageLimit(searchRequest.from, searchRequest.size),
            sort = Sort(listOf("lastModifiedDate"), Sort.Direction.DESC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata", "lastModifiedDate"),
            rule = rule
        )
        val result = nodeResource.query(queryModel)
        val data = result.data ?: return NpmSearchResponse()
        return transferRecords(data.records)
    }

    private fun transferRecords(records: List<Map<String, Any>>): NpmSearchResponse {
        val listInfo = mutableListOf<Map<String, Any>>()
        if (records.isNullOrEmpty()) return NpmSearchResponse()
        records.forEach {
            val packageInfo = mutableMapOf<String, Any>()
            val metadataInfo = mutableMapOf<String, Any?>()
            val date = it[LAST_MODIFIED_DATE] as String
            val metadata = it[METADATA] as Map<String, String?>
            metadataInfo[NAME] = metadata[NAME]
            metadataInfo[DESCRIPTION] = metadata[DESCRIPTION]
            metadataInfo[MAINTAINERS] = parseJsonArrayToList(metadata[MAINTAINERS])
            metadataInfo[VERSION] = metadata[VERSION]
            metadataInfo[DATE] = date
            metadataInfo[KEYWORDS] = parseJsonArrayToList(metadata[KEYWORDS])
            metadataInfo[AUTHOR] = metadata[AUTHOR]
            packageInfo[PACKAGE] = metadataInfo
            listInfo.add(packageInfo)
        }
        return NpmSearchResponse(listInfo)
    }

    private fun parseJsonArrayToList(jsonArray: String?): List<Map<String, Any>> {
        return jsonArray?.let { GsonUtils.gsonToList<Map<String, Any>>(it) } ?: emptyList()
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NpmLocalRepository::class.java)
    }
}
