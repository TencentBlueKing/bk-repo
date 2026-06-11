package com.tencent.bkrepo.pypi.service

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.node.NodeSearchService
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.pypi.artifact.PypiProperties
import com.tencent.bkrepo.pypi.artifact.PypiSimpleArtifactInfo
import com.tencent.bkrepo.pypi.constants.INDENT
import com.tencent.bkrepo.pypi.constants.LINE_BREAK
import com.tencent.bkrepo.pypi.constants.NON_ALPHANUMERIC_SEQ_REGEX
import com.tencent.bkrepo.pypi.constants.PACKAGE_INDEX_TITLE
import com.tencent.bkrepo.pypi.constants.REQUIRES_PYTHON
import com.tencent.bkrepo.pypi.constants.REQUIRES_PYTHON_ATTR
import com.tencent.bkrepo.pypi.constants.SIMPLE_PAGE_CONTENT
import com.tencent.bkrepo.pypi.constants.VERSION
import com.tencent.bkrepo.pypi.constants.VERSION_INDEX_TITLE
import com.tencent.bkrepo.pypi.exception.PypiSimpleNotFoundException
import com.tencent.bkrepo.pypi.util.HtmlUtils
import com.tencent.bkrepo.pypi.util.PypiSimpleIndexUtils
import com.tencent.bkrepo.repository.constant.FULL_PATH
import com.tencent.bkrepo.repository.constant.METADATA
import com.tencent.bkrepo.repository.constant.NAME
import com.tencent.bkrepo.repository.constant.NODE_METADATA
import com.tencent.bkrepo.repository.constant.PROJECT_ID
import com.tencent.bkrepo.repository.constant.REPO_NAME
import com.tencent.bkrepo.repository.constant.SHA256
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import com.tencent.bkrepo.common.artifact.path.PathUtils.ROOT
import com.tencent.bkrepo.common.metadata.util.version.SemVersion
import com.tencent.bkrepo.common.metadata.util.version.SemVersionParser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File

@Service
class PypiSimpleIndexService(
    private val nodeService: NodeService,
    private val nodeSearchService: NodeSearchService,
    private val storageManager: StorageManager,
    private val repositoryService: RepositoryService,
    private val pypiProperties: PypiProperties,
) {

    fun getSimpleHtml(artifactInfo: PypiSimpleArtifactInfo): String {
        if (!pypiProperties.enableSimpleIndexCache) {
            return generateSimpleHtml(artifactInfo)
        }
        val cachePath = getCachePath(artifactInfo)
        loadCachedHtml(artifactInfo.projectId, artifactInfo.repoName, cachePath)?.let { return it }
        val html = generateSimpleHtml(artifactInfo)
        storeCachedHtml(artifactInfo.projectId, artifactInfo.repoName, cachePath, html)
        return html
    }

    fun refreshAfterUpload(projectId: String, repoName: String, packageName: String, operator: String) {
        if (!pypiProperties.enableSimpleIndexCache) {
            return
        }
        runCatching {
            refreshPackageListIndex(projectId, repoName, operator)
            refreshPackageIndex(projectId, repoName, packageName, operator)
            val normalized = PypiSimpleIndexUtils.normalizePackageName(packageName)
            if (normalized != packageName) {
                refreshPackageIndex(projectId, repoName, normalized, operator)
            }
        }.onFailure { e ->
            logger.warn(
                "Failed to refresh simple index cache after upload [$projectId/$repoName/$packageName]: ${e.message}",
                e
            )
        }
    }

    fun refreshAfterRemove(
        projectId: String,
        repoName: String,
        packageName: String,
        deleteWholePackage: Boolean,
        operator: String
    ) {
        if (!pypiProperties.enableSimpleIndexCache) {
            return
        }
        runCatching {
            refreshPackageListIndex(projectId, repoName, operator)
            if (deleteWholePackage) {
                PypiSimpleIndexUtils.packageCachePaths(packageName).forEach { cachePath ->
                    deleteCachedHtml(projectId, repoName, cachePath, operator)
                }
            } else {
                refreshPackageIndex(projectId, repoName, packageName, operator)
                val normalized = PypiSimpleIndexUtils.normalizePackageName(packageName)
                if (normalized != packageName) {
                    refreshPackageIndex(projectId, repoName, normalized, operator)
                }
            }
        }.onFailure { e ->
            logger.warn(
                "Failed to refresh simple index cache after remove [$projectId/$repoName/$packageName]: ${e.message}",
                e
            )
        }
    }

    private fun getCachePath(artifactInfo: PypiSimpleArtifactInfo): String {
        return if (artifactInfo.packageName == null) {
            PypiSimpleIndexUtils.getPackageListCachePath()
        } else {
            PypiSimpleIndexUtils.getPackageCachePath(artifactInfo.packageName!!)
        }
    }

    private fun refreshPackageListIndex(projectId: String, repoName: String, operator: String) {
        try {
            val html = generatePackageListHtml(projectId, repoName)
            storeCachedHtml(projectId, repoName, PypiSimpleIndexUtils.getPackageListCachePath(), html, operator)
        } catch (e: PypiSimpleNotFoundException) {
            deleteCachedHtml(projectId, repoName, PypiSimpleIndexUtils.getPackageListCachePath(), operator)
        } catch (e: Exception) {
            logger.warn(
                "Failed to refresh package list index cache [$projectId/$repoName]: ${e.message}",
                e
            )
        }
    }

    private fun refreshPackageIndex(projectId: String, repoName: String, packageName: String, operator: String) {
        try {
            val html = generatePackageHtml(projectId, repoName, packageName)
            storeCachedHtml(
                projectId,
                repoName,
                PypiSimpleIndexUtils.getPackageCachePath(packageName),
                html,
                operator
            )
        } catch (e: PypiSimpleNotFoundException) {
            deleteCachedHtml(projectId, repoName, PypiSimpleIndexUtils.getPackageCachePath(packageName), operator)
        } catch (e: Exception) {
            logger.warn(
                "Failed to refresh package index cache [$projectId/$repoName/$packageName]: ${e.message}",
                e
            )
        }
    }

    private fun loadCachedHtml(projectId: String, repoName: String, fullPath: String): String? {
        return try {
            val node = nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath)) ?: return null
            if (node.folder) {
                return null
            }
            val storageCredentials = getStorageCredentials(projectId, repoName) ?: return null
            storageManager.loadArtifactInputStream(node, storageCredentials)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            logger.warn(
                "Failed to load simple index cache [$projectId/$repoName$fullPath]: ${e.message}",
                e
            )
            null
        }
    }

    private fun storeCachedHtml(
        projectId: String,
        repoName: String,
        fullPath: String,
        html: String,
        operator: String = SYSTEM_OPERATOR
    ) {
        val storageCredentials = getStorageCredentials(projectId, repoName)
        if (storageCredentials == null) {
            logger.warn(
                "Skip storing simple index cache [$projectId/$repoName$fullPath]: " +
                    "storage credentials not found"
            )
            return
        }
        val tempFile = File.createTempFile("pypi-simple-index-", ".html")
        try {
            tempFile.writeBytes(html.toByteArray(Charsets.UTF_8))
            val artifactFile = FileSystemArtifactFile(tempFile)
            val nodeCreateRequest = NodeCreateRequest(
                projectId = projectId,
                repoName = repoName,
                folder = false,
                fullPath = fullPath,
                size = artifactFile.getSize(),
                sha256 = artifactFile.getFileSha256(),
                crc64ecma = artifactFile.getFileCrc64ecma(),
                md5 = artifactFile.getFileMd5(),
                overwrite = true,
                operator = operator
            )
            storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, storageCredentials)
            artifactFile.delete()
            logger.info("Stored simple index cache [$projectId/$repoName$fullPath]")
        } catch (e: Exception) {
            tempFile.delete()
            logger.warn(
                "Failed to store simple index cache [$projectId/$repoName$fullPath]: ${e.message}",
                e
            )
        }
    }

    private fun deleteCachedHtml(projectId: String, repoName: String, fullPath: String, operator: String) {
        try {
            if (nodeService.getNodeDetail(ArtifactInfo(projectId, repoName, fullPath)) == null) {
                return
            }
            nodeService.deleteNode(
                com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest(
                    projectId,
                    repoName,
                    fullPath,
                    operator
                )
            )
            logger.info("Deleted simple index cache [$projectId/$repoName$fullPath]")
        } catch (e: Exception) {
            logger.warn(
                "Failed to delete simple index cache [$projectId/$repoName$fullPath]: ${e.message}",
                e
            )
        }
    }

    private fun getStorageCredentials(projectId: String, repoName: String): StorageCredentials? {
        return repositoryService.getRepoDetail(projectId, repoName, RepositoryType.PYPI.name)?.storageCredentials
    }

    private fun generateSimpleHtml(artifactInfo: PypiSimpleArtifactInfo): String {
        return if (artifactInfo.packageName == null) {
            generatePackageListHtml(artifactInfo.projectId, artifactInfo.repoName)
        } else {
            generatePackageHtml(artifactInfo.projectId, artifactInfo.repoName, artifactInfo.packageName!!)
        }
    }

    private fun generatePackageListHtml(projectId: String, repoName: String): String {
        val nodeList = nodeService.listNode(
            ArtifactInfo(projectId, repoName, ROOT),
            NodeListOption(includeFolder = true)
        ).filter { it.folder && !PypiSimpleIndexUtils.isSimpleIndexFolder(it.name) }
            .takeIf { it.isNotEmpty() } ?: throw PypiSimpleNotFoundException(StringPool.SLASH)
        return buildPypiPageContent(PACKAGE_INDEX_TITLE, buildPackageListContent(nodeList))
    }

    private fun generatePackageHtml(projectId: String, repoName: String, packageName: String): String {
        val nodes = nodeService.listNodeWithMetadataKeys(
            ArtifactInfo(projectId, repoName, "/$packageName"),
            NodeListOption(
                includeFolder = false,
                deep = true,
            ),
            listOf(VERSION, REQUIRES_PYTHON),
        )
        if (!nodes.isNullOrEmpty()) {
            return buildPypiPageContent(
                String.format(VERSION_INDEX_TITLE, packageName),
                buildPackageFileListContent(nodes)
            )
        }
        if (!pypiProperties.enableRegexQuery) {
            throw PypiSimpleNotFoundException(packageName)
        }
        logger.info("not found nodeList by packageName[$packageName], use regex query")
        val nodeList = searchPackageNodesByRegex(projectId, repoName, packageName)
        nodeList.ifEmpty { throw PypiSimpleNotFoundException(packageName) }
        return buildPypiPageContent(
            String.format(VERSION_INDEX_TITLE, packageName),
            buildPackageFileNodeListContent(nodeList)
        )
    }

    private fun searchPackageNodesByRegex(
        projectId: String,
        repoName: String,
        packageName: String
    ): List<Map<String, Any?>> {
        var pageNumber = 1
        val nodeList = mutableListOf<Map<String, Any?>>()
        do {
            val queryModel = NodeQueryBuilder()
                .select(NAME, FULL_PATH, METADATA, SHA256)
                .sortByAsc(NAME)
                .page(pageNumber, PAGE_SIZE)
                .projectId(projectId)
                .repoName(repoName)
                .path("^/${packageName.replace("-", NON_ALPHANUMERIC_SEQ_REGEX)}/", OperationType.REGEX_I)
                .excludeFolder()
                .build()
            val records = nodeSearchService.searchWithoutCount(queryModel).records
            nodeList.addAll(records)
            pageNumber++
        } while (records.size == PAGE_SIZE)
        return nodeList
    }

    private fun buildPypiPageContent(title: String, listContent: String) =
        String.format(SIMPLE_PAGE_CONTENT, title, title, listContent)

    @Suppress("UNCHECKED_CAST")
    private fun buildPackageFileNodeListContent(nodeList: List<Map<String, Any?>>): String {
        val builder = StringBuilder()
        val sortedNodeList = nodeList.sortedBy { node ->
            try {
                SemVersionParser.parse(
                    getNodeMetadata(node).find { it.key == VERSION }?.value.toString()
                )
            } catch (_: IllegalArgumentException) {
                SemVersion(0, 0, 0)
            }
        }
        sortedNodeList.forEachIndexed { i, node ->
            val requiresPython = getNodeMetadata(node)
                .find { it.key == REQUIRES_PYTHON }?.value?.toString()?.ifBlank { null }
            builder.append(
                buildPackageFileNodeLink(
                    fullPath = node[FULL_PATH].toString(),
                    name = node[NAME].toString(),
                    sha256 = node[SHA256]?.toString(),
                    requiresPython = requiresPython
                )
            )
            if (i != nodeList.size - 1) builder.append("\n")
        }
        return builder.toString()
    }

    private fun getNodeMetadata(node: Map<String, Any?>): List<MetadataModel> {
        return try {
            node[NODE_METADATA] as List<MetadataModel>
        } catch (e: ClassCastException) {
            logger.error(
                "node[${node[PROJECT_ID]}/${node[REPO_NAME]}${node[FULL_PATH]}] " +
                    "metadata is not List<MetadataModel>, ${e.message}"
            )
            emptyList()
        }
    }

    private fun buildPackageFileListContent(nodeList: List<NodeInfo>): String {
        val builder = StringBuilder()
        val sortedNodeList = nodeList.sortedBy {
            try {
                SemVersionParser.parse(it.metadata?.get("version").toString())
            } catch (_: IllegalArgumentException) {
                SemVersion(0, 0, 0)
            }
        }
        sortedNodeList.forEachIndexed { i, node ->
            val requiresPython = node.nodeMetadata
                ?.find { it.key == REQUIRES_PYTHON }?.value?.toString()?.ifBlank { null }
            builder.append(buildPackageFileNodeLink(node.fullPath, node.name, node.sha256, requiresPython))
            if (i != nodeList.size - 1) builder.append("\n")
        }
        return builder.toString()
    }

    private fun buildPackageFileNodeLink(
        fullPath: String,
        name: String,
        sha256: String?,
        requiresPython: String?
    ): String {
        val href = "../../packages$fullPath#sha256=$sha256"
        val requiresPythonAttr = requiresPython
            ?.let { " $REQUIRES_PYTHON_ATTR=\"${HtmlUtils.partialEncode(it)}\"" } ?: ""
        return "$INDENT<a href=\"$href\"$requiresPythonAttr rel=\"internal\">$name</a>$LINE_BREAK"
    }

    private fun buildPackageListContent(nodeList: List<NodeInfo>): String {
        val builder = StringBuilder()
        if (nodeList.isEmpty()) {
            builder.append("The directory is empty.")
        }
        nodeList.forEachIndexed { i, node ->
            val href = "\"${PypiSimpleIndexUtils.normalizePackageName(node.name)}/\""
            builder.append("$INDENT<a href=$href rel=\"internal\">${node.name}</a>$LINE_BREAK")
            if (i != nodeList.size - 1) builder.append("\n")
        }
        return builder.toString()
    }

    companion object {
        private const val PAGE_SIZE = 1000
        private const val SYSTEM_OPERATOR = "system"
        private val logger = LoggerFactory.getLogger(PypiSimpleIndexService::class.java)
    }
}
