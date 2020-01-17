package com.tencent.bkrepo.pypi.artifact.repository

import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_MD5MAP
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_SHA256MAP
import com.tencent.bkrepo.common.artifact.file.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactListContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.artifact.url.XmlUtil
import com.tencent.bkrepo.pypi.pojo.xml.XmlConvertUtil
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeSearchRequest
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

    /**
     * 创建PYPI simple请求
     */
    fun getNodeSearchRequest(context: ArtifactListContext): NodeSearchRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo

        val map = context.contextAttributes[ATTRIBUTE_SHA256MAP]
        map as LinkedHashMap<String, String>
        val sha256 = map["content"]

        val repoNameList: MutableList<String> = ArrayList()
        repoNameList.add(repositoryInfo.name)

        val pathVariable: MutableList<String> = ArrayList()
        if (artifactInfo is PypiArtifactInfo) {
            pathVariable.add(artifactInfo.artifactUri)
        }

        val metadataCondition: MutableMap<String, String> = HashMap()

        return NodeSearchRequest(
            projectId = repositoryInfo.projectId,
            repoNameList = repoNameList,
            pathPattern = pathVariable,
            metadataCondition = metadataCondition
        )
    }

    fun searchXml(
        context: ArtifactListContext,
        xmlString: String
    ) {
        val artifactInfo = context.artifactInfo

        val methodCall = XmlConvertUtil.convert(xmlString)
        val action = methodCall.methodName
        val packageName = methodCall.params.paramList[0].value.struct?.memberList?.get(0)?.value?.array?.data?.valueList?.get(0)?.string
        val summary = methodCall.params.paramList[0].value.struct?.memberList?.get(1)?.value?.array?.data?.valueList?.get(0)?.string
        var xml: String

//        with(artifactInfo) {
//
//            val projectId = Rule.QueryRule("projectId", projectId)
//            val repoName = Rule.QueryRule("repoName", repoName)
//            val target = Rule.QueryRule("metadata.name", "weaving")
//            val rule1 = Rule.NestedRule(mutableListOf(repoName, projectId, target), Rule.NestedRule.RelationType.AND)
//            val queryModel = QueryModel(
//                    page = PageLimit(0, 10),
//                    sort = Sort(listOf("name"), Sort.Direction.ASC),
//                    select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
//                    rule = rule1
//            )
//            val map = nodeResource.query(queryModel)
//            val test = "dd"
//        }

        with(artifactInfo) {
            val nodeDetail = packageName?.let { nodeResource.detail(projectId, repoName, it).data } ?: throw packageName?.let {
                com.tencent.bkrepo.common.api.exception.ErrorCodeException(
                        com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.NODE_NOT_FOUND,
                        it
                )
            }!!
            val response = HttpContextHolder.getResponse()
            response.contentType = "text/xml; charset=UTF-8"
            if (nodeDetail.nodeInfo.folder) {
                var nodeList = nodeResource.list(artifactInfo.projectId, artifactInfo.repoName, packageName, includeFolder = true, deep = true).data ?: throw packageName?.let {
                    com.tencent.bkrepo.common.api.exception.ErrorCodeException(
                            com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.NODE_NOT_FOUND,
                            it
                    )
                }!!
                xml = XmlUtil.getXmlMethodResponse02(packageName, nodeList)
                response.writer.print(xml)
            }
        }
    }

    override fun list(context: ArtifactListContext) {
        val artifactInfo = context.artifactInfo
        with(artifactInfo) {
            val nodeDetail = nodeResource.detail(projectId, repoName, artifactUri).data
                    ?: throw com.tencent.bkrepo.common.api.exception.ErrorCodeException(
                            com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.NODE_NOT_FOUND,
                            artifactUri
                    )

            val response = HttpContextHolder.getResponse()
            response.contentType = "text/html; charset=UTF-8"
            if (artifactUri == "/") {
                if (nodeDetail.nodeInfo.folder) {
                    val nodeList = nodeResource.list(artifactInfo.projectId, artifactInfo.repoName, artifactInfo.artifactUri, includeFolder = true, deep = true).data
                            ?: throw com.tencent.bkrepo.common.api.exception.ErrorCodeException(
                                    com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.NODE_NOT_FOUND,
                                    artifactInfo.artifactUri
                            )
                    response.writer.print(buildPypiSimplePageContent(nodeList.filter { it.folder }.filter { it.path == "/" }))
                }
            } else {
                if (nodeDetail.nodeInfo.folder) {
                    val nodeList = nodeResource.list(artifactInfo.projectId, artifactInfo.repoName, artifactUri, includeFolder = false, deep = true).data
                            ?: throw com.tencent.bkrepo.common.api.exception.ErrorCodeException(
                                    com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.NODE_NOT_FOUND,
                                    artifactUri
                            )
                    response.writer.print(buildPypiPageContent(nodeList))
                }
            }
        }
    }

    private fun buildPypiPageContent(nodeList: List<NodeInfo>): String {
        val listContent = buildPypiListContent(nodeList)
        return """
            <html>
                <head><title>Simple Index</title><meta name="api-version" value="2" /></head>
                <body>
                    $listContent
                </body>
            </html>
        """.trimIndent()
    }

    private fun buildPypiListContent(nodeList: List<NodeInfo>): String {
        val builder = StringBuilder()
        if (nodeList.isEmpty()) {
            builder.append("The directory is empty.")
        }
        for (node in nodeList) {
            val md5 = node.md5
            builder.append("<a data-requires-python=\">=${node.metadata?.get("requires_python")}\" href=\"../../packages${node.fullPath}#md5=$md5\" rel=\"internal\" >${node.name}</a><br/>")
        }
        return builder.toString()
    }

    private fun buildPypiSimplePageContent(nodeList: List<NodeInfo>): String {
        val listContent = buildPypiSimpleListContent(nodeList)
        return """
            <html>
                <head><title>Simple Index</title><meta name="api-version" value="2" /></head>
                <body>
                    $listContent
                </body>
            </html>
        """.trimIndent()
    }

    private fun buildPypiSimpleListContent(nodeList: List<NodeInfo>): String {
        val builder = StringBuilder()
        if (nodeList.isEmpty()) {
            builder.append("The directory is empty.")
        }
        for (node in nodeList) {
            builder.append("<a data-requires-python=\">=${node.metadata?.get("requires_python")}\" href=\"simple/${node.name}\" rel=\"internal\" >${node.name}</a><br/>")
        }
        return builder.toString()
    }
}
