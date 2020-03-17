package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeListViewItem
import com.tencent.bkrepo.repository.pojo.repo.RepoListViewItem
import com.tencent.bkrepo.repository.util.NodeUtils
import org.apache.commons.lang.StringEscapeUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

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
                val pageContent = buildNodePageContent(nodeList, nodeDetail.nodeInfo)
                response.writer.print(pageContent)
            } else {
                val context = ArtifactDownloadContext()
                val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
                repository.download(context)
            }
        }
    }

    private fun buildNodePageContent(nodeList: List<NodeInfo>, currentNode: NodeInfo): String {
        val currentPath = computeCurrentPath(currentNode)
        val nameColumnWidth = computeNameColumnWidth(nodeList.map { it.name })
        val itemList = nodeList.map { NodeListViewItem.from(it) }.sorted()
        val listContent = buildNodeListContent(itemList, currentNode, nameColumnWidth)
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
                <pre>$listContent</pre>
                <hr/>
                <address style="font-size:small;">BlueKing Repository</address>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildNodeListContent(itemList: List<NodeListViewItem>, currentNode: NodeInfo, nameColumnWidth: Int): String {
        val builder = StringBuilder()
        if (!NodeUtils.isRootPath(currentNode.fullPath)) {
            builder.append("""<a href="../">../</a>""")
            builder.append("\n")
        }
        if (itemList.isEmpty()) {
            builder.append("The directory is empty.")
        }
        itemList.forEach { item ->
            buildContent(builder, item.name, item.lastModifiedDate, item.size, nameColumnWidth)
        }
        return builder.toString()
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

    fun listRepoView(projectId: String? = null) {
        trailingSlash()
        val itemList = projectId?.run {
            repositoryService.list(this).map { RepoListViewItem.from(it) }.sorted()
        } ?: run {
            projectService.list().map { RepoListViewItem.from(it) }
        }
        val title = if (projectId == null) "Project" else "Repository"
        val pageContent = buildRepoPageContent(itemList, title)
        HttpContextHolder.getResponse().writer.print(pageContent)
    }

    private fun buildRepoPageContent(itemList: List<RepoListViewItem>, title: String): String {
        val nameColumnWidth = computeNameColumnWidth(itemList.map { it.name })
        val listContent = buildRepoListContent(itemList, nameColumnWidth, title)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Index of $title</title>
            </head>
            <body>
                <h1>Index of $title</h1>
                <pre>${"Name".padEnd(nameColumnWidth)}Last modified       createdBy</pre>
                <hr/>
                <pre>$listContent</pre>
                <hr/>
                <address style="font-size:small;">BlueKing Repository</address>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildRepoListContent(itemList: List<RepoListViewItem>, nameColumnWidth: Int, title: String): String {
        val builder = StringBuilder()
        if (itemList.isEmpty()) {
            builder.append("No ${title.toLowerCase()} existed.")
        }
        itemList.forEach { item ->
            buildContent(builder, item.name, item.lastModifiedDate, item.createdBy, nameColumnWidth)
        }
        return builder.toString()
    }

    private fun buildContent(builder: StringBuilder, name: String, lastModifiedDate: String, thirdPart: String, nameColumnWidth: Int) {
        builder.append("""<a href="$name">${StringEscapeUtils.escapeXml(name)}</a>""")
        builder.append(" ".repeat(nameColumnWidth - name.length))
        builder.append(lastModifiedDate)
        builder.append("    ")
        builder.append(thirdPart)
        builder.append("\n")
    }

    private fun computeNameColumnWidth(nameList: List<String>): Int {
        val maxNameLength = nameList.maxBy { it.length }?.length ?: 0
        return maxNameLength + 4
    }

    private fun trailingSlash() {
        val url = HttpContextHolder.getRequest().requestURL.toString()
        if (!url.endsWith("/")) {
            HttpContextHolder.getResponse().sendRedirect("$url/")
        }
    }

}
