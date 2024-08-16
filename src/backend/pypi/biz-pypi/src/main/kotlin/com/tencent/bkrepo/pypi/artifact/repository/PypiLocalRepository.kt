/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.path.PathUtils.ROOT
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.util.PackageKeys
import com.tencent.bkrepo.common.artifact.util.version.SemVersion
import com.tencent.bkrepo.common.artifact.util.version.SemVersionParser
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.pypi.artifact.PypiProperties
import com.tencent.bkrepo.pypi.artifact.PypiSimpleArtifactInfo
import com.tencent.bkrepo.pypi.artifact.url.UrlPatternUtil.parameterMaps
import com.tencent.bkrepo.pypi.artifact.xml.Value
import com.tencent.bkrepo.pypi.artifact.xml.XmlUtil
import com.tencent.bkrepo.pypi.constants.INDENT
import com.tencent.bkrepo.pypi.constants.LINE_BREAK
import com.tencent.bkrepo.pypi.constants.NON_ALPHANUMERIC_SEQ_REGEX
import com.tencent.bkrepo.pypi.constants.PACKAGE_INDEX_TITLE
import com.tencent.bkrepo.pypi.constants.REQUIRES_PYTHON
import com.tencent.bkrepo.pypi.constants.REQUIRES_PYTHON_ATTR
import com.tencent.bkrepo.pypi.constants.SIMPLE_PAGE_CONTENT
import com.tencent.bkrepo.pypi.constants.SUMMARY
import com.tencent.bkrepo.pypi.constants.VERSION
import com.tencent.bkrepo.pypi.constants.VERSION_INDEX_TITLE
import com.tencent.bkrepo.pypi.exception.PypiSimpleNotFoundException
import com.tencent.bkrepo.pypi.pojo.Basic
import com.tencent.bkrepo.pypi.pojo.PypiArtifactVersionData
import com.tencent.bkrepo.pypi.util.HtmlUtils
import com.tencent.bkrepo.pypi.util.PypiVersionUtils.toPypiPackagePojo
import com.tencent.bkrepo.pypi.util.XmlUtils
import com.tencent.bkrepo.pypi.util.XmlUtils.readXml
import com.tencent.bkrepo.repository.api.StageClient
import com.tencent.bkrepo.repository.constant.FULL_PATH
import com.tencent.bkrepo.repository.constant.METADATA
import com.tencent.bkrepo.repository.constant.NAME
import com.tencent.bkrepo.repository.constant.NODE_METADATA
import com.tencent.bkrepo.repository.constant.SHA256
import com.tencent.bkrepo.repository.pojo.download.PackageDownloadRecord
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.packages.PackageType
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PypiLocalRepository(
    private val stageClient: StageClient,
    private val pypiProperties: PypiProperties
) : LocalRepository() {

    /**
     * 获取PYPI节点创建请求
     */
    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val repositoryDetail = context.repositoryDetail
        val artifactFile = context.getArtifactFile("content")
        val metadata = context.request.parameterMaps().map { MetadataModel(key = it.key, value = it.value) }
        val filename = (artifactFile as MultipartArtifactFile).getOriginalFilename()
        val sha256 = artifactFile.getFileSha256()
        val md5 = artifactFile.getFileMd5()
        val name: String = context.request.getParameter("name")
        val version: String = context.request.getParameter("version")
        val artifactFullPath = "/$name/$version/$filename"

        return NodeCreateRequest(
            projectId = repositoryDetail.projectId,
            repoName = repositoryDetail.name,
            folder = false,
            overwrite = true,
            fullPath = artifactFullPath,
            size = artifactFile.getSize(),
            sha256 = sha256,
            md5 = md5,
            operator = context.userId,
            nodeMetadata = metadata
        )
    }

    override fun onUpload(context: ArtifactUploadContext) {
        val nodeCreateRequest = buildNodeCreateRequest(context)
        val artifactFile = context.getArtifactFile("content")
        val name: String = context.request.getParameter("name")
        val version: String = context.request.getParameter("version")
        packageClient.createVersion(
            PackageVersionCreateRequest(
                projectId = context.projectId,
                repoName = context.repoName,
                packageName = name,
                packageKey = PackageKeys.ofPypi(name),
                packageType = PackageType.PYPI,
                versionName = version,
                size = context.getArtifactFile("content").getSize(),
                artifactPath = nodeCreateRequest.fullPath,
                overwrite = true,
                createdBy = context.userId,
                packageDescription = context.request.getParameter(SUMMARY)?.ifBlank { null }
            ),
            HttpContextHolder.getClientAddress()
        )
        store(nodeCreateRequest, artifactFile, context.storageCredentials)
    }

    override fun onDownloadBefore(context: ArtifactDownloadContext) {
        super.onDownloadBefore(context)
        packageVersion(context)?.let { downloadIntercept(context, it) }
    }

    private fun combineSameParamQuery(entry: Map.Entry<String, List<String>>): Rule.NestedRule {
        val sameParamQueryList = mutableListOf<Rule>()
        for (value in entry.value) {
            sameParamQueryList.add(
                Rule.QueryRule("metadata.${entry.key}", "*$value*", OperationType.MATCH_I)
            )
        }
        return Rule.NestedRule(sameParamQueryList, Rule.NestedRule.RelationType.OR)
    }

    private fun combineParamQuery(
        map: Map<String, List<String>>,
        paramQueryList: MutableList<Rule>,
        operation: String
    ): Rule.NestedRule {
        for (param in map) {
            if (param.value.isNullOrEmpty()) continue
            if (param.value.size == 1) {
                paramQueryList.add(
                    Rule.QueryRule("metadata.${param.key}", "*${param.value[0]}*", OperationType.MATCH_I)
                )
            } else if (param.value.size > 1) {
                // 同属性值固定为`or` 参考：https://warehouse.readthedocs.io/api-reference/xml-rpc.html#
                // Within the spec, a field’s value can be a string or a list of strings
                // (the values within the list are combined with an OR)
                paramQueryList.add(combineSameParamQuery(param))
            }
        }
        val relationType = when (operation) {
            "or" -> Rule.NestedRule.RelationType.OR
            "and" -> Rule.NestedRule.RelationType.AND
            else -> Rule.NestedRule.RelationType.OR
        }
        return Rule.NestedRule(paramQueryList, relationType)
    }

    /**
     * pypi search
     */
    override fun search(context: ArtifactSearchContext): List<Value> {
        val pypiSearchPojo = XmlUtils.getPypiSearchPojo(context.request.reader.readXml())
        val projectId = Rule.QueryRule("projectId", context.projectId)
        val repoName = Rule.QueryRule("repoName", context.repoName)
        val filetypeQuery = Rule.QueryRule("metadata.filetype", "bdist_wheel")
        val paramQueryList = mutableListOf<Rule>()
        val paramQuery = if (pypiSearchPojo.map.isNotEmpty()) {
            combineParamQuery(pypiSearchPojo.map, paramQueryList, pypiSearchPojo.operation)
        } else Rule.NestedRule(paramQueryList)
        val rule = if (paramQueryList.isNotEmpty()) {
            Rule.NestedRule(
                mutableListOf(projectId, repoName, filetypeQuery, paramQuery), Rule.NestedRule.RelationType.AND
            )
        } else {
            Rule.NestedRule(
                mutableListOf(projectId, repoName, filetypeQuery), Rule.NestedRule.RelationType.AND
            )
        }
        val queryModel = QueryModel(
            page = PageLimit(pageLimitCurrent, pageLimitSize),
            sort = Sort(listOf("name"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
            rule = rule
        )
        val nodeList: List<Map<String, Any?>>? = nodeClient.queryWithoutCount(queryModel).data?.records
        if (nodeList != null) {
            return XmlUtil.nodeLis2Values(nodeList)
        }
        return mutableListOf()
    }

    /**
     * pypi 产品删除接口
     */
    override fun remove(context: ArtifactRemoveContext) {
        val packageKey = HttpContextHolder.getRequest().getParameter("packageKey")
        val name = PackageKeys.resolvePypi(packageKey)
        val version = HttpContextHolder.getRequest().getParameter("version")
        if (version.isNullOrBlank()) {
            // 删除包
            nodeClient.deleteNode(
                NodeDeleteRequest(
                    context.projectId,
                    context.repoName,
                    "/$name",
                    context.userId
                )
            )
            packageClient.deletePackage(
                context.projectId,
                context.repoName,
                packageKey,
                HttpContextHolder.getClientAddress()
            )
        } else {
            // 删除版本
            nodeClient.deleteNode(
                NodeDeleteRequest(
                    context.projectId,
                    context.repoName,
                    "/$name/$version",
                    context.userId
                )
            )
            packageClient.deleteVersion(
                context.projectId,
                context.repoName,
                packageKey,
                version,
                HttpContextHolder.getClientAddress()
            )
        }
    }

    /**
     * 1，pypi 产品 版本详情
     * 2，pypi simple html页面
     */
    override fun query(context: ArtifactQueryContext): Any? {
        return when (val artifactInfo = context.artifactInfo) {
            is PypiSimpleArtifactInfo -> getSimpleHtml(artifactInfo)
            else -> getVersionDetail(context)
        }
    }

    fun getVersionDetail(context: ArtifactQueryContext): PypiArtifactVersionData? {
        val packageKey = context.request.getParameter("packageKey")
        val version = context.request.getParameter("version")
        logger.info("Get version detail. packageKey[$packageKey], version[$version]")
        val name = PackageKeys.resolvePypi(packageKey)
        val trueVersion = packageClient.findVersionByName(
            context.projectId,
            context.repoName,
            packageKey,
            version
        ).data ?: return null
        val artifactPath = trueVersion.contentPath ?: return null
        with(context.artifactInfo) {
            val jarNode = nodeClient.getNodeDetail(
                projectId, repoName, artifactPath
            ).data ?: return null
            val stageTag = stageClient.query(projectId, repoName, packageKey, version).data
            val packageVersion = packageClient.findVersionByName(
                projectId, repoName, packageKey, version
            ).data
            val count = packageVersion?.downloads ?: 0
            val pypiArtifactBasic = Basic(
                name,
                version,
                jarNode.size, jarNode.fullPath,
                jarNode.createdBy, jarNode.createdDate,
                jarNode.lastModifiedBy, jarNode.lastModifiedDate,
                count,
                jarNode.sha256,
                jarNode.md5,
                stageTag,
                null
            )
            return PypiArtifactVersionData(pypiArtifactBasic, packageVersion?.packageMetadata)
        }
    }

    // https://packaging.python.org/en/latest/specifications/simple-repository-api/
    fun getSimpleHtml(artifactInfo: PypiSimpleArtifactInfo): String? {
        with(artifactInfo) {
            logger.info("Get simple html, artifactInfo: $this")
            // 请求不带包名，返回包名列表.
            if (packageName == null) {
                val nodeList = nodeClient.listNode(projectId, repoName, ROOT, includeFolder = true).data
                    ?.filter { it.folder }?.takeIf { it.isNotEmpty() }
                    ?: throw PypiSimpleNotFoundException(StringPool.SLASH)
                // 过滤掉'根节点',
                return buildPypiPageContent(PACKAGE_INDEX_TITLE, buildPackageListContent(nodeList))
            }
            // 请求中带包名，返回对应包的文件列表。
            val nodes = nodeClient.listNode(
                projectId = projectId,
                repoName = repoName,
                path = "/$packageName",
                includeFolder = false,
                deep = true,
                includeMetadata = true
            ).data
            if (!nodes.isNullOrEmpty()) {
                return buildPypiPageContent(
                    String.format(VERSION_INDEX_TITLE, packageName),
                    buildPackageFileListContent(nodes)
                )
            }
            if (!pypiProperties.enableRegexQuery) {
                throw PypiSimpleNotFoundException(packageName!!)
            }
            logger.info("not found nodeList by packageName[$packageName], use regex query")
            // 客户端标准化包名规则：1.连续的[-_.]转换为单个"-"；2.转换为全小写。此处需要以反向规则查询
            var pageNumber = 1
            val nodeList = mutableListOf<Map<String, Any?>>()
            do {
                val queryModel = NodeQueryBuilder()
                    .select(NAME, FULL_PATH, METADATA, SHA256)
                    .sortByAsc(NAME)
                    .page(pageNumber, PAGE_SIZE)
                    .projectId(projectId)
                    .repoName(repoName)
                    .path("^/${packageName!!.replace("-", NON_ALPHANUMERIC_SEQ_REGEX)}/", OperationType.REGEX_I)
                    .excludeFolder()
                    .build()
                val records = nodeClient.queryWithoutCount(queryModel).data!!.records
                nodeList.addAll(records)
                pageNumber++
            } while (records.size == PAGE_SIZE)
            nodeList.ifEmpty { throw PypiSimpleNotFoundException(packageName!!) }
            return buildPypiPageContent(
                String.format(VERSION_INDEX_TITLE, packageName),
                buildPackageFileNodeListContent(nodeList)
            )
        }
    }

    /**
     * html 页面公用的元素
     * @param listContent 显示的内容
     */
    private fun buildPypiPageContent(title: String, listContent: String) =
        String.format(SIMPLE_PAGE_CONTENT, title, title, listContent)

    /**
     * 对应包中的文件列表
     * [nodeList]
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildPackageFileNodeListContent(nodeList: List<Map<String, Any?>>): String {
        val builder = StringBuilder()
        val sortedNodeList = nodeList.sortedBy { node ->
            try {
                SemVersionParser.parse(
                    (node[NODE_METADATA] as List<Map<String, Any?>>).find { it["key"] == VERSION }
                        ?.get("value").toString()
                )
            } catch (ignore: IllegalArgumentException) {
                SemVersion(0, 0, 0)
            }
        }
        // data-requires-python属性值中的"<"和">"需要转换为HTML编码
        sortedNodeList.forEachIndexed { i, node ->
            val requiresPython = (node[NODE_METADATA] as List<Map<String, Any?>>)
                .find { it["key"] == REQUIRES_PYTHON }?.get("value")?.toString()?.ifBlank { null }
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

    /**
     * 对应包中的文件列表
     * [nodeList]
     */
    private fun buildPackageFileListContent(nodeList: List<NodeInfo>): String {
        val builder = StringBuilder()
        val sortedNodeList = nodeList.sortedBy {
            try {
                SemVersionParser.parse(it.metadata?.get("version").toString())
            } catch (ignore: IllegalArgumentException) {
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

    /**
     * 所有包列表
     * @param nodeList
     */
    private fun buildPackageListContent(nodeList: List<NodeInfo>): String {
        val builder = StringBuilder()
        if (nodeList.isEmpty()) {
            builder.append("The directory is empty.")
        }
        // href中的包名需要根据PEP 503规范进行标准化，且以"/"结尾
        nodeList.forEachIndexed { i, node ->
            val href = "\"${node.name.replace(nonAlphanumericSeqRegex, "-").toLowerCase()}/\""
            builder.append("$INDENT<a href=$href rel=\"internal\">${node.name}</a>$LINE_BREAK")
            if (i != nodeList.size - 1) builder.append("\n")
        }
        return builder.toString()
    }

    fun store(node: NodeCreateRequest, artifactFile: ArtifactFile, storageCredentials: StorageCredentials?) {
        storageManager.storeArtifactFile(node, artifactFile, storageCredentials)
        artifactFile.delete()
        with(node) { logger.info("Success to store$projectId/$repoName/$fullPath") }
        logger.info("Success to insert $node")
    }

    // pypi 客户端下载统计
    override fun buildDownloadRecord(
        context: ArtifactDownloadContext,
        artifactResource: ArtifactResource
    ): PackageDownloadRecord? {
        with(context) {
            val fullPath = context.artifactInfo.getArtifactFullPath()
            val pypiPackagePojo = fullPath.toPypiPackagePojo()
            val packageKey = PackageKeys.ofPypi(pypiPackagePojo.name)
            return PackageDownloadRecord(
                projectId, repoName,
                packageKey, pypiPackagePojo.version
            )
        }
    }

    private fun packageVersion(context: ArtifactDownloadContext): PackageVersion? {
        with(context) {
            val pypiPackagePojo = try {
                artifactInfo.getArtifactFullPath().toPypiPackagePojo()
            } catch (e: Exception) {
                logger.info("parse path[${artifactInfo.getArtifactFullPath()}] to pypi package version failed: ", e)
                null
            } ?: return null

            val packageKey = PackageKeys.ofPypi(pypiPackagePojo.name)
            return packageClient.findVersionByName(projectId, repoName, packageKey, pypiPackagePojo.version).data
        }
    }

    companion object {
        private const val PAGE_SIZE = 1000
        private val nonAlphanumericSeqRegex = Regex(NON_ALPHANUMERIC_SEQ_REGEX)
        val logger: Logger = LoggerFactory.getLogger(PypiLocalRepository::class.java)
        const val pageLimitCurrent = 0
        const val pageLimitSize = 10
    }
}
