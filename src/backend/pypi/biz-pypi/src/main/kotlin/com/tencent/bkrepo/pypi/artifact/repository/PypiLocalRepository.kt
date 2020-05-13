package com.tencent.bkrepo.pypi.artifact.repository

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_MD5MAP
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_SHA256MAP
import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.file.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.*
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.artifact.xml.Value
import com.tencent.bkrepo.pypi.artifact.xml.XmlUtil
import com.tencent.bkrepo.pypi.exception.PypiMigrateReject
import com.tencent.bkrepo.pypi.util.DecompressUtil.getPkgInfo
import com.tencent.bkrepo.pypi.util.FileNameUtil.fileFormat
import com.tencent.bkrepo.pypi.util.JsoupUtil.htmlHrefs
import com.tencent.bkrepo.pypi.util.JsoupUtil.sumTasks
import com.tencent.bkrepo.pypi.pojo.PypiMigrateResponse
import com.tencent.bkrepo.pypi.util.HttpUtil.downloadUrlHttpClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.apache.commons.fileupload.util.Streams
import org.jsoup.nodes.Element
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import java.io.File
import org.springframework.stereotype.Component
import java.lang.Exception
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Component
@Primary
class PypiLocalRepository : LocalRepository(), PypiRepository {

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

    override fun searchNodeList(context: ArtifactSearchContext, xmlString: String): MutableList<Value>? {
        val repository = context.repositoryInfo
        val searchArgs = XmlUtil.getSearchArgs(xmlString)
        val packageName = searchArgs["packageName"]
        val summary = searchArgs["summary"]
        if (packageName != null && summary != null) {
            with(repository) {
                val projectId = Rule.QueryRule("projectId", projectId)
                val repoName = Rule.QueryRule("repoName", name)
                val packageQuery = Rule.QueryRule("metadata.name", packageName, OperationType.MATCH)
                val filetypeAuery = Rule.QueryRule("metadata.filetype", "bdist_wheel")
                val summaryQuery = Rule.QueryRule("metadata.summary", summary, OperationType.MATCH)
                // val versionQuery = Rule.QueryRule("metadata.verison", OperationType.EQ)
                val rule1 = Rule.NestedRule(
                    mutableListOf(repoName, projectId, packageQuery, filetypeAuery),
                    Rule.NestedRule.RelationType.AND
                )
                val rule2 = Rule.NestedRule(mutableListOf(rule1, summaryQuery), Rule.NestedRule.RelationType.OR)

                val queryModel = QueryModel(
                    page = PageLimit(0, 10),
                    sort = Sort(listOf("name"), Sort.Direction.ASC),
                    select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
                    rule = rule1
                )
                val nodeList: List<Map<String, Any>>? = nodeResource.query(queryModel).data?.records
                if (nodeList != null) {
                    return XmlUtil.nodeLis2Values(nodeList)
                }
            }
        }
        return null
    }

    /**
     *
     */
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
            // 请求不带包名，返回包名列表.
            if (artifactUri == "/") {
                if (nodeDetail.nodeInfo.folder) {
                    val nodeList = nodeResource.list(projectId, repositoryInfo.name, artifactUri, includeFolder = true, deep = true).data
                            ?: throw com.tencent.bkrepo.common.api.exception.ErrorCodeException(
                                    com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.NODE_NOT_FOUND,
                                    artifactInfo.artifactUri
                            )
                    // 过滤掉'根节点',
                    val htmlContent = buildPackageListContent(
                        artifactInfo.projectId,
                        artifactInfo.repoName,
                        nodeList.filter { it.folder }.filter { it.path == "/" })
                    response.writer.print(htmlContent)
                }
            }
            // 请求中带包名，返回对应包的文件列表。
            else {
                if (nodeDetail.nodeInfo.folder) {
                    val packageNode = nodeResource.list(projectId, repositoryInfo.name, artifactUri, includeFolder = false, deep = true).data
                            ?: throw com.tencent.bkrepo.common.api.exception.ErrorCodeException(
                                    com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode.NODE_NOT_FOUND,
                                    artifactUri
                            )
                    val htmlContent = buildPypiPageContent(buildPackageFilenodeListContent(artifactInfo.projectId, artifactInfo.repoName, packageNode))
                    response.writer.print(htmlContent)
                }
            }
        }
    }

    /**
     * html 页面公用的元素
     * @param listContent 显示的内容
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
     * 对应包中的文件列表
     * @param projectId
     * @param repoName
     * @param nodeList
     */
    private fun buildPackageFilenodeListContent(projectId: String, repoName: String, nodeList: List<NodeInfo>): String {
        val builder = StringBuilder()
        if (nodeList.isEmpty()) {
            builder.append("The directory is empty.")
        }
        for (node in nodeList) {
            val md5 = node.md5
            // 查询的对应的文件节点的metadata
            val metadata = filenodeMetadata(node)
            builder.append("<a data-requires-python=\">=$metadata[\"requires_python\"]\" href=\"/$projectId/$repoName/packages${node.fullPath}#md5=$md5\" rel=\"internal\" >${node.name}</a><br/>")
        }
        return builder.toString()
    }

    /**
     * 所有包列表
     * @param projectId
     * @param repoName
     * @param nodeList
     */
    private fun buildPackageListContent(projectId: String, repoName: String, nodeList: List<NodeInfo>): String {
        val builder = StringBuilder()
        if (nodeList.isEmpty()) {
            builder.append("The directory is empty.")
        }
        for (node in nodeList) {
            builder.append("<a data-requires-python=\">=\" href=\"/$projectId/$repoName/simple/${node.name}\" rel=\"internal\" >${node.name}</a><br/>")
        }
        return builder.toString()
    }

    /**
     * 根据每个文件节点数据去查metadata
     * @param nodeInfo 节点
     */
    fun filenodeMetadata(nodeInfo: NodeInfo): List<Map<String, Any>>? {
        val filenodeList: List<Map<String, Any>>?
        with(nodeInfo) {
            val projectId = Rule.QueryRule("projectId", projectId)
            val repoName = Rule.QueryRule("repoName", repoName)
            val packageQuery = Rule.QueryRule("metadata.name", name, OperationType.EQ)
            val fullPathQuery = Rule.QueryRule("fullPath", fullPath)
            val rule1 = Rule.NestedRule(
                mutableListOf(repoName, projectId, packageQuery, fullPathQuery),
                Rule.NestedRule.RelationType.AND
            )
            val queryModel = QueryModel(
                page = PageLimit(0, 10),
                sort = Sort(listOf("name"), Sort.Direction.ASC),
                select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
                rule = rule1
            )
            filenodeList = nodeResource.query(queryModel).data?.records
        }
        return filenodeList
    }




    @org.springframework.beans.factory.annotation.Value("\${migrate.url}")
    private lateinit var migrateUrl: String

    @org.springframework.beans.factory.annotation.Value("\${limitPackages}")
    private lateinit var limitPackages: String


    private val failSet = mutableSetOf<String>()

    override fun migrate(context: ArtifactMigrateContext): PypiMigrateResponse<String> {
        val verifiedUrl = beforeMigrate()

        var successCount = 0

        var failCount = 0


        val cpuCore = cpuCore()

        val threadPool = ThreadPoolExecutor(cpuCore, cpuCore*3, 20, TimeUnit.SECONDS,
                LinkedBlockingQueue(),
                ThreadFactoryBuilder().setNameFormat("pypiRepo-migrate-thread-%d").build(),
                PypiMigrateReject() )

        //获取所有的包,开始计时
        val start = System.currentTimeMillis()
        verifiedUrl.htmlHrefs().let { simpleHrefs ->
            for (e in simpleHrefs) {
                //每一个包所包含的文件列表
                e.text()?.let { packageName ->
                    "$verifiedUrl/$packageName".htmlHrefs().let { filenodes ->
                        for (filenode in filenodes) {
                            threadPool.submit( Runnable {
                                migrateUpload(context, filenode, verifiedUrl, packageName)?.let { failCount++ }
                            })
                        }
                    }
                }
            }
        }
        val end = System.currentTimeMillis()
        val elapseTimeSeconds = (end-start)/1000
        return PypiMigrateResponse(
                "$migrateUrl migrate result",
                migrateUrl.sumTasks(),
                0,
                failCount,
                elapseTimeSeconds,
                failSet
        )

    }

    fun migrateUpload(context: ArtifactMigrateContext, filenode: Element, verifiedUrl: String, packageName: String): String? {
        return try {
            val filename = filenode.text()
            val hrefValue = filenode.attributes()["href"]
            //获取文件流
            val byteStream = "$verifiedUrl/$packageName/$hrefValue".downloadUrlHttpClient()
            val artifactFile = ArtifactFileFactory.build()
            byteStream?.let {
                Streams.copy(byteStream, artifactFile.getOutputStream(), true)
                val nodeCreateRequest = createMigrateNode(context, artifactFile, packageName, filename)
                nodeCreateRequest?.let {
                    nodeResource.create(nodeCreateRequest)
                    artifactFile.let {
                        storageService.store(nodeCreateRequest.sha256!!,
                                it, context.storageCredentials)
                    }
                }
            }
            null
        } catch (e: Exception) {
            logger.error(e.message)
            logger.warn("$verifiedUrl/$packageName/${filenode.attributes()["href"]}")
            failSet.add("$verifiedUrl/$packageName/${filenode.attributes()["href"]}")
            "fail mark!"
        }
    }

    fun createMigrateNode(context: ArtifactMigrateContext, artifactFile: ArtifactFile, packageName: String, filename: String): NodeCreateRequest? {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        //获取文件版本信息
        val pkgInfo = filename.fileFormat()?.let { artifactFile.getInputStream().getPkgInfo(it) }
        //文件fullPath
        val path = "/$packageName/${pkgInfo?.get("version")}/$filename"
        //TODO  查重
        nodeResource.exist(repositoryInfo.projectId, repositoryInfo.name, path).data?.let {
            if (it) {
                return null
            }
        }

        //TODO 计算sha256
        val sha256 = FileDigestUtils.fileSha256(artifactFile.getInputStream())
        val md5 = FileDigestUtils.fileMd5(artifactFile.getInputStream())
        val pypiArtifactInfo = artifactInfo as PypiArtifactInfo

        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                overwrite = true,
                fullPath = path,
                size = artifactFile.getSize(),
                sha256 = sha256 as String?,
                md5 = md5 as String?,
                operator = context.userId,
                metadata = pypiArtifactInfo.metadata
        )
    }

    /**
     * 检验地址格式
     */
    fun beforeMigrate(): String {
//        val okHttp = HttpClientBuilderFactory.create().build()
//        val request = Request.Builder().get().url(migrateUrl).build()
//        val response = okHttp.newCall(request).execute()
//        if (!(response.message().equals("OK", true))) {
//            return PypiMigrateResponse("$migrateUrl cannot reach")
//        }
        return migrateUrl.removeSuffix("/")
    }

    /**
     * 获取CPU核心数
     */
    fun cpuCore(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(PypiLocalRepository::class.java)
    }
}
