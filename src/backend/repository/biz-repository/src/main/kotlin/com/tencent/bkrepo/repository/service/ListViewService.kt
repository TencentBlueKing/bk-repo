package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.pojo.list.HeaderItem
import com.tencent.bkrepo.repository.pojo.list.ListViewObject
import com.tencent.bkrepo.repository.pojo.list.RowItem
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListViewItem
import com.tencent.bkrepo.repository.pojo.repo.RepoListViewItem
import com.tencent.bkrepo.repository.util.NodeUtils
import org.apache.commons.lang.StringEscapeUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.PrintWriter

/**
 *
 * @author: carrypan
 * @date: 2019/12/22
 */
@Service
class ListViewService @Autowired constructor(
    private val projectService: ProjectService,
    private val repositoryService: RepositoryService,
    private val nodeService: NodeService
) {
    fun listNodeView(artifactInfo: ArtifactInfo) {
        with(artifactInfo) {
            val nodeDetail = nodeService.detail(projectId, repoName, artifactUri) ?: throw ErrorCodeException(
                ArtifactMessageCode.NODE_NOT_FOUND, artifactUri)
            val response = HttpContextHolder.getResponse()
            response.contentType = "text/html; charset=UTF-8"
            if (nodeDetail.nodeInfo.folder) {
                trailingSlash()
                val nodeList = nodeService.list(artifactInfo.projectId, artifactInfo.repoName, artifactUri, includeFolder = true, deep = false)
                val currentPath = computeCurrentPath(nodeDetail.nodeInfo)
                val headerList = listOf(
                    HeaderItem("Name"),
                    HeaderItem("Last modified"),
                    HeaderItem("Created by"),
                    HeaderItem("Size")
                )
                val itemList = nodeList.map { NodeListViewItem.from(it) }.sorted()
                val rowList = itemList.map { RowItem(listOf(it.name, it.lastModified, it.createdBy, it.size)) }
                writePageContent(ListViewObject(currentPath, headerList, rowList, footer, true))
            } else {
                val context = ArtifactDownloadContext()
                val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
                repository.download(context)
            }
        }
    }

    fun listRepoView(projectId: String? = null) {
        trailingSlash()
        val itemList = projectId?.run {
            repositoryService.list(this).map { RepoListViewItem.from(it) }
        } ?: run {
            projectService.list().map { RepoListViewItem.from(it) }
        }
        val title = if (projectId == null) "Project" else "Repository[$projectId]"
        val headerList = listOf(
            HeaderItem("Name"),
            HeaderItem("Last modified"),
            HeaderItem("Created by"),
            HeaderItem("Sharding index")
        )
        val rowList = itemList.sorted().map {
            RowItem(listOf(it.name, it.lastModified, it.createdBy, it.shardingIndex))
        }
        val backTo = projectId != null
        val listViewObject = ListViewObject(title, headerList, rowList, footer, backTo)
        writePageContent(listViewObject)
    }

    private fun writePageContent(listViewObject: ListViewObject) {
        val writer = HttpContextHolder.getResponse().writer
        val headerContent = buildHeaderContent(listViewObject)
        writer.println(
            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Index of ${listViewObject.title}</title>
            </head>
            <body>
                <h1>Index of ${listViewObject.title}</h1>
                <hr/>
                <pre>$headerContent</pre>
                <hr/>
                <pre>
            """.trimIndent()
        )
        writeListContent(listViewObject, writer)
        writer.println(
            """
                </pre>
                <hr/>
                <address style="font-size:small;">${listViewObject.footer}</address>
            </body>
            </html>
            """.trimIndent()
        )
    }

    private fun writeListContent(listViewObject: ListViewObject, writer: PrintWriter) {
        with(listViewObject) {
            if (backTo) {
                writer.println("""<a href="../">../</a>""")
            }
            if (rowList.isEmpty()) {
                writer.print("\nEmpty content.")
            }
            rowList.forEachIndexed { rowIndex, row ->
                row.itemList.forEachIndexed { columnIndex, item ->
                    if (columnIndex == 0) {
                        val escapedItem = StringEscapeUtils.escapeXml(item)
                        writer.print("""<a href="$item">$escapedItem</a>""")
                        writer.print(" ".repeat(headerList[columnIndex].width!! - item.length))
                    } else {
                        writer.print(item.padEnd(headerList[columnIndex].width!!))
                    }
                    writer.print(" ".repeat(gap))
                }
                if (rowIndex != rowList.size -1) {
                    writer.println()
                }
            }
        }
    }

    private fun buildHeaderContent(listViewObject: ListViewObject): String {
        with(listViewObject) {
            val builder = StringBuilder()
            headerList.forEachIndexed { index, header ->
                header.width = computeColumnWidth(header, rowList, index)
                builder.append(header.name.padEnd(header.width!! + gap))
            }
            return builder.toString()
        }
    }

    private fun computeCurrentPath(currentNode: NodeInfo): String {
        val builder = StringBuilder()
        builder.append(NodeUtils.FILE_SEPARATOR)
            .append(currentNode.projectId)
            .append(NodeUtils.FILE_SEPARATOR)
            .append(currentNode.repoName)
            .append(currentNode.fullPath)
        if (!NodeUtils.isRootPath(currentNode.fullPath)) {
            builder.append(NodeUtils.FILE_SEPARATOR)
        }
        return builder.toString()
    }

    private fun computeColumnWidth(header: HeaderItem, rowList: List<RowItem>, index: Int): Int {
        var maxLength = header.name.length
        rowList.forEach {
            if (it.itemList[index].length > maxLength) {
                maxLength = it.itemList[index].length
            }
        }
        return maxLength
    }

    private fun trailingSlash() {
        val url = HttpContextHolder.getRequest().requestURL.toString()
        if (!url.endsWith("/")) {
            HttpContextHolder.getResponse().sendRedirect("$url/")
        }
    }

    companion object {
        private const val gap = 4
        private const val footer = "BlueKing Repository"
    }
}
