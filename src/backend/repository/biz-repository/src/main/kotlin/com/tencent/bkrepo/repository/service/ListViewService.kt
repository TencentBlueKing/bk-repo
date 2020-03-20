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
import com.tencent.bkrepo.repository.pojo.project.ProjectListViewItem
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
            response.contentType = HTML_CONTENT_TYPE
            if (nodeDetail.nodeInfo.folder) {
                trailingSlash()
                val nodeList = nodeService.list(artifactInfo.projectId, artifactInfo.repoName, artifactUri, includeFolder = true, deep = false)
                val currentPath = computeCurrentPath(nodeDetail.nodeInfo)
                val headerList = listOf(
                    HeaderItem("Name"),
                    HeaderItem("Created by"),
                    HeaderItem("Last modified"),
                    HeaderItem("Size")
                )
                val itemList = nodeList.map { NodeListViewItem.from(it) }.sorted()
                val rowList = itemList.map { RowItem(listOf(it.name, it.createdBy, it.lastModified,  it.size)) }
                writePageContent(ListViewObject(currentPath, headerList, rowList, FOOTER, true))
            } else {
                val context = ArtifactDownloadContext()
                val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
                repository.download(context)
            }
        }
    }

    fun listRepoView(projectId: String) {
        trailingSlash()
        val itemList = repositoryService.list(projectId).map { RepoListViewItem.from(it) }
        val title = "Repository[$projectId]"
        val headerList = listOf(
            HeaderItem("Name"),
            HeaderItem("Created by"),
            HeaderItem("Last modified"),
            HeaderItem("Category"),
            HeaderItem("Type"),
            HeaderItem("Public")
        )
        val rowList = itemList.sorted().map {
            RowItem(listOf(it.name, it.createdBy, it.lastModified,  it.category, it.type, it.public))
        }
        val listViewObject = ListViewObject(title, headerList, rowList, FOOTER, true)
        writePageContent(listViewObject)
    }

    fun listProjectView() {
        trailingSlash()
        val itemList = projectService.list().map { ProjectListViewItem.from(it) }
        val headerList = listOf(
            HeaderItem("Name"),
            HeaderItem("Created by"),
            HeaderItem("Last modified"),
            HeaderItem("Sharding index")
        )
        val rowList = itemList.sorted().map {
            RowItem(listOf(it.name, it.createdBy, it.lastModified, it.shardingIndex))
        }
        val listViewObject = ListViewObject("Project", headerList, rowList, FOOTER, false)
        writePageContent(listViewObject)
    }

    private fun writePageContent(listViewObject: ListViewObject) {
        with(listViewObject) {
            val writer = HttpContextHolder.getResponse().writer
            val headerContent = buildHeaderContent(this)
            writer.println(FIRST_PART.format(title, title, headerContent).trimIndent())
            writeListContent(this, writer)
            writer.println(LAST_PART.trimIndent())
        }

    }

    private fun writeListContent(listViewObject: ListViewObject, writer: PrintWriter) {
        with(listViewObject) {
            if (backTo) {
                writer.println(BACK_TO)
            }
            if (rowList.isEmpty()) {
                writer.print(EMPTY_CONTENT)
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
                    writer.print(" ".repeat(GAP))
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
                builder.append(header.name.padEnd(header.width!! + GAP))
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
        private const val HTML_CONTENT_TYPE = "text/html; charset=UTF-8"
        private const val GAP = 4
        private const val FOOTER = "BlueKing Repository"
        private const val BACK_TO = """<a href="../">../</a>"""
        private const val EMPTY_CONTENT = "\nEmpty content."
        private const val FIRST_PART = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Index of %s</title>
            </head>
            <body>
                <h1>Index of %s</h1>
                <hr/>
                <pre>%s</pre>
                <hr/>
                <pre>
        """

        private const val LAST_PART = """
                </pre>
                <hr/>
                <address style="font-size:small;">$FOOTER</address>
            </body>
            </html>
        """
    }
}
