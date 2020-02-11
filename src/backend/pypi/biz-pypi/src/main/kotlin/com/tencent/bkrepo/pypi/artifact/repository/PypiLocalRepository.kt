package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_MD5MAP
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_SHA256MAP
import com.tencent.bkrepo.common.artifact.file.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.artifact.url.XmlUtil
import com.tencent.bkrepo.pypi.pojo.xml.XmlConvertUtil
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import java.io.File
import org.springframework.stereotype.Component

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Component
class PypiLocalRepository : LocalRepository() {

    override fun onUpload(context: ArtifactUploadContext) {
        val nodeCreateRequest = getNodeCreateRequest(context)
        nodeResource.create(nodeCreateRequest)
        context.getArtifactFile("content")?.let {
            storageService.store(nodeCreateRequest.sha256!!,
                it, context.storageCredentials)
        }
    }

    /**
     * 获取PYPI节点创建请求
     */
    override fun getNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile("content")
        val filename = (artifactFile as MultipartArtifactFile).getOriginalFilename()
        val map = context.contextAttributes[ATTRIBUTE_SHA256MAP] as LinkedHashMap<*, *>
        val sha256 = map["content"]
        val md5 = (context.contextAttributes[ATTRIBUTE_MD5MAP] as LinkedHashMap<*, *>)["content"]
        val pypiArtifactInfo = artifactInfo as PypiArtifactInfo

        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            overwrite = true,
            fullPath = artifactInfo.artifactUri + "/$filename",
            size = artifactFile.getSize(),
            sha256 = sha256 as String?,
            md5 = md5 as String?,
            operator = context.userId,
            metadata = pypiArtifactInfo.metadata
        )
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

    fun searchXml(
        context: ArtifactListContext,
        xmlString: String
    ) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "text/xml; charset=UTF-8"
        val artifactInfo = context.artifactInfo

        val methodCall = XmlConvertUtil.convert(xmlString)

        val action = methodCall.methodName
        val packageName = methodCall.params.paramList[0].value.struct?.memberList?.get(0)?.value?.array?.data?.valueList?.get(0)?.string
        val summary = methodCall.params.paramList[0].value.struct?.memberList?.get(1)?.value?.array?.data?.valueList?.get(0)?.string
        var xml: String

        if (packageName != null && summary != null) {
            with(artifactInfo) {
                val projectId = Rule.QueryRule("projectId", projectId)
                val repoName = Rule.QueryRule("repoName", repoName)
                val packageQuery = Rule.QueryRule("metadata.name", packageName, OperationType.MATCH)
                val filetypeAuery = Rule.QueryRule("metadata.filetype", "bdist_wheel")
                val summaryQuery = Rule.QueryRule("metadata.summary", summary)
                val rule1 = Rule.NestedRule(mutableListOf(repoName, projectId, packageQuery, filetypeAuery), Rule.NestedRule.RelationType.AND)
                val rule2 = Rule.NestedRule(mutableListOf(rule1, summaryQuery), Rule.NestedRule.RelationType.OR)

                val queryModel = QueryModel(
                    page = PageLimit(0, 10),
                    sort = Sort(listOf("name"), Sort.Direction.ASC),
                    select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
                    rule = rule1
                )
                val nodoList: List<Map<String, Any>>? = nodeResource.query(queryModel).data?.records
                if (nodoList != null) {
                    xml = XmlUtil.getXmlMethodResponse(nodoList)
                    response.writer.print(xml)
                }
            }
        }
    }

    //节点数据与元数据分开查询。
    override fun list(context: ArtifactListContext) {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        with(artifactInfo) {
            val nodeDetail = nodeResource.detail(projectId, repositoryInfo.name, artifactUri).data
                    ?: throw com.tencent.bkrepo.common.api.exception.ErrorCodeException(
                            com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.NODE_NOT_FOUND,
                            artifactUri
                    )

            val response = HttpContextHolder.getResponse()
            response.contentType = "text/html; charset=UTF-8"
            //请求不带包名，返回包名列表。
            if (artifactUri == "/") {
                if (nodeDetail.nodeInfo.folder) {
                    val nodeList = nodeResource.list(projectId, repositoryInfo.name, artifactUri, includeFolder = true, deep = true).data
                            ?: throw com.tencent.bkrepo.common.api.exception.ErrorCodeException(
                                    com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.NODE_NOT_FOUND,
                                    artifactInfo.artifactUri
                            )
                    //过滤掉'根节点'，
                    val htmlContent = buildPypiSimpleListContent(
                        artifactInfo.projectId,
                        artifactInfo.repoName,
                        nodeList.filter { it.folder }.filter { it.path == "/" })
                    response.writer.print(htmlContent)
                }
            }
            //请求中带包名，返回对应包的文件列表。
            else {
                if (nodeDetail.nodeInfo.folder) {
                    val nodeList = nodeResource.list(projectId, repositoryInfo.name, artifactUri, includeFolder = false, deep = true).data
                            ?: throw com.tencent.bkrepo.common.api.exception.ErrorCodeException(
                                    com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.NODE_NOT_FOUND,
                                    artifactUri
                            )
                    val htmlContent = buildPypiPageContent(buildPypiPackageListContent(artifactInfo.projectId, artifactInfo.repoName, nodeList))
                    response.writer.print(htmlContent)
                }
            }
        }
    }

    /**
     * @param listContent 显示的包名列表
     */
    private fun buildPypiPageContent(listContent: String): String {
        return """
            <html>
                <head><title>Simple Index</title><meta name="api-version" value="2" /></head>
                <body>
                    $listContent
                </body>
            </html>
        """.trimIndent()
    }

    /**
     * @param projectId
     * @param repoName
     * @param nodeList 包节点列表
     */
    private fun buildPypiPackageListContent(projectId: String, repoName: String, nodeList: List<NodeInfo>): String {
        val builder = StringBuilder()
        if (nodeList.isEmpty()) {
            builder.append("The directory is empty.")
        }
        for (node in nodeList) {
            val md5 = node.md5
            builder.append("<a data-requires-python=\">=${node.metadata?.get("requires_python")}\" href=\"/$projectId/$repoName/packages${node.fullPath}#md5=$md5\" rel=\"internal\" >${node.name}</a><br/>")
        }
        return builder.toString()
    }

    /**
     * @param projectId
     * @param repoName
     * @param nodeList 文件节点列表
     */
    private fun buildPypiSimpleListContent(projectId: String, repoName: String, nodeList: List<NodeInfo>): String {
        val builder = StringBuilder()
        if (nodeList.isEmpty()) {
            builder.append("The directory is empty.")
        }
        for (node in nodeList) {
            builder.append("<a data-requires-python=\">=${node.metadata?.get("requires_python")}\" href=\"/$projectId/$repoName/simple/${node.name}\" rel=\"internal\" >${node.name}</a><br/>")
        }
        return builder.toString()
    }
}
