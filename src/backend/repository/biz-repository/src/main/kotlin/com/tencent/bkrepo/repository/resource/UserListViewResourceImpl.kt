package com.tencent.bkrepo.repository.resource

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.api.UserListViewResource
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListViewItem
import com.tencent.bkrepo.repository.service.NodeService
import com.tencent.bkrepo.repository.util.NodeUtils
import com.tencent.bkrepo.repository.util.NodeUtils.FILE_SEPARATOR
import org.apache.commons.lang.StringEscapeUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 *
 * @author: carrypan
 * @date: 2019/12/11
 */
@RestController
class UserListViewResourceImpl @Autowired constructor(
    private val nodeService: NodeService
) : UserListViewResource {

    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    override fun listView(artifactInfo: ArtifactInfo) {
        with(artifactInfo) {
            val nodeDetail = nodeService.detail(projectId, repoName, artifactUri) ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, artifactUri)
            val response = HttpContextHolder.getResponse()
            response.contentType = "text/html; charset=UTF-8"
            if(nodeDetail.nodeInfo.folder) {
                val nodeList = nodeService.list(artifactInfo.projectId, artifactInfo.repoName, artifactUri, includeFolder = true, deep = false)
                val pageContent = buildPageContent(nodeList, nodeDetail.nodeInfo)
                response.writer.print(pageContent)
            } else {
                // download file
                response.writer.print("暂不支持下载")
            }
        }
    }

    private fun buildPageContent(nodeList: List<NodeInfo>, currentNode: NodeInfo): String {
        val currentPath = computeCurrentPath(currentNode)
        val nameColumnWidth = computeNameColumnWidth(nodeList)
        val itemList = nodeList.map { NodeListViewItem.from(it) }.sorted()
        val listContent = buildListContent(itemList, currentNode, nameColumnWidth)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Index of $currentPath</title>
            </head>
            <body>
                <h1>Index of $currentPath</h1>
                <pre>${"Name".padEnd(nameColumnWidth)}Last modified       Size</pre>
                <hr/>
                <pre>$listContent
                </pre>
                <hr/>
                <address style="font-size:small;">BlueKing Repository</address>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildListContent(itemList: List<NodeListViewItem>, currentNode: NodeInfo, nameColumnWidth: Int): String {
        val builder = StringBuilder()
        if(!NodeUtils.isRootPath(currentNode.fullPath)) {
            builder.append("""<a href="../">../</a>""")
            builder.append("\n")
        }
        if(itemList.isEmpty()) {
            builder.append("The directory is empty.")
        }
        for (item in itemList) {
            builder.append("""<a href="${item.name}">${StringEscapeUtils.escapeXml(item.name)}</a>""")
            builder.append(" ".repeat(nameColumnWidth - item.name.length))
            builder.append(item.lastModifiedDate)
            builder.append("    ")
            builder.append(item.size)
            builder.append("\n")

        }
        return builder.toString()
    }

    private fun computeCurrentPath(currentNode: NodeInfo): String {
        val builder = StringBuilder()
        builder.append(FILE_SEPARATOR)
            .append(currentNode.projectId)
            .append(FILE_SEPARATOR)
            .append(currentNode.repoName)
            .append(currentNode.fullPath)
        if(!NodeUtils.isRootPath(currentNode.fullPath)) {
            builder.append(FILE_SEPARATOR)
        }
        return builder.toString()
    }

    private fun computeNameColumnWidth(nodeList: List<NodeInfo>): Int {
        val maxNameLength = nodeList.maxBy { it.name.length }?.name?.length ?: 0
        return maxNameLength + 4
    }
}