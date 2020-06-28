package com.tencent.bkrepo.rpm.artifact.repository

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.hash.md5
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.hash.sha256
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.rpm.INDEXER
import com.tencent.bkrepo.rpm.NO_INDEXER
import com.tencent.bkrepo.rpm.artifact.SurplusNodeCleaner
import com.tencent.bkrepo.rpm.pojo.RpmUploadResponse
import com.tencent.bkrepo.rpm.util.GZipUtil.UnGzipInputStream
import com.tencent.bkrepo.rpm.util.GZipUtil.gZip
import com.tencent.bkrepo.rpm.util.redline.model.RpmMetadataWithOldStream
import com.tencent.bkrepo.rpm.util.redline.model.RpmRepoMd
import com.tencent.bkrepo.rpm.util.rpm.RpmFormatInterpreter
import com.tencent.bkrepo.rpm.util.rpm.RpmFormatReader
import groovy.lang.Script
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.FileInputStream
import java.lang.StringBuilder

@Component
class RpmLocalRepository : LocalRepository() {

    @Autowired
    private lateinit var xmlScript: Script

    @Autowired
    lateinit var surplusNodeCleaner: SurplusNodeCleaner

    fun rpmNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val artifactFile = context.getArtifactFile()
        val sha256 = artifactFile.getInputStream().sha256()
        val md5 = artifactFile.getInputStream().md5()

        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                overwrite = true,
                fullPath = artifactInfo.artifactUri,
                size = artifactFile.getSize(),
                sha256 = sha256,
                md5 = md5,
                operator = context.userId
        )
    }

    fun xmlPrimaryNodeCreate(userId: String, repositoryInfo: RepositoryInfo, fullPath: String, xmlGZArtifact: ArtifactFile): NodeCreateRequest {
        val sha256 = xmlGZArtifact.getInputStream().sha256()
        val md5 = xmlGZArtifact.getInputStream().md5()
        return NodeCreateRequest(
                projectId = repositoryInfo.projectId,
                repoName = repositoryInfo.name,
                folder = false,
                overwrite = true,
                fullPath = fullPath,
                size = xmlGZArtifact.getSize(),
                sha256 = sha256,
                md5 = md5,
                operator = userId
        )
    }

    /**
     * 查询仓库设置的repodata 深度，默认为0
     */
    private fun searchRpmRepoDataDepth(context: ArtifactUploadContext): Int {
        (context.repositoryInfo.configuration as RpmLocalConfiguration).repodataDepth?.let { return it }
        return 0
    }

    /**
     * 检查请求uri地址的层级是否 > 仓库设置的repodata 深度
     * @return true 将会计算rpm包的索引
     * @return false 只提供文件服务器功能，返回提示信息
     */
    private fun checkRequestUri(context: ArtifactUploadContext): Boolean {
        val repodataDepth = (context.repositoryInfo.configuration as RpmLocalConfiguration).repodataDepth
        val artifactUri = context.artifactInfo.artifactUri.removePrefix("/").split("/").size
        repodataDepth?.let { return artifactUri > repodataDepth }
        return false
    }

    /**
     * 按照仓库设置的repodata 深度分割请求参数
     * @return map["repodataPath"] 契合本次请求的repodata_depth 目录路径
     * @return map["artifactRelativePath"] 构件相对于索引文件的保存路径
     */
    private fun splitUriByDepth(uri: String, depth: Int): Map<String, String> {
        val uriList = uri.removePrefix("/").split("/")
        val repodataPath = StringBuilder()
        for (i in 0 until depth) {
            repodataPath.append(uriList[i]).append("/")
        }
        val artifactRelativePath = uri.removePrefix("/").split(repodataPath.toString())[1]
        return mapOf("repodataPath" to repodataPath.toString(),
                "artifactRelativePath" to artifactRelativePath)
    }

    /**
     * 生成构件索引
     */
    private fun indexer(context: ArtifactUploadContext) {

        val repodataDepth = searchRpmRepoDataDepth(context)
        val uriMap = splitUriByDepth(context.artifactInfo.artifactUri, repodataDepth)
        val repodataPath = uriMap["repodataPath"]

        val artifactFile = context.getArtifactFile()
        val rpmFormat = RpmFormatReader.wrapStreamAndRead(artifactFile.getInputStream())
        val rpmMetadata = RpmFormatInterpreter().interpret(rpmFormat)
        rpmMetadata.size = artifactFile.getSize()

        // rpm 包额外计算的信息
        artifactFile.getInputStream().sha1().let { rpmMetadata.sha1Digest = it }
        val artifactRelativePath = uriMap["artifactRelativePath"]

        artifactRelativePath?.let { rpmMetadata.artifactRelativePath = it }

        with(context.repositoryInfo) {
            // repodata下'-primary.xml.gz'最新节点。
            val nodeList = nodeResource.list(projectId, name, "/${repodataPath}repodata", includeFolder = false, deep = false).data
            val primaryNodelist = nodeList?.filter { it.name.endsWith("-primary.xml.gz") }?.sortedByDescending { it.createdDate }

            val targetXmlString = if (!primaryNodelist.isNullOrEmpty()) {
                val latestPrimaryNode = primaryNodelist[0]
                storageService.load(latestPrimaryNode.sha256!!, Range.ofFull(latestPrimaryNode.size), context.storageCredentials)?.UnGzipInputStream().use { inputStream ->
                    val rpmMetadataWithOldStream = inputStream?.let { RpmMetadataWithOldStream(rpmMetadata, it) }
                    // 更新primary.xml
                    xmlScript.invokeMethod("updataPrimaryXml", rpmMetadataWithOldStream) as String
                }
            } else {
                // first upload
                xmlScript.invokeMethod("xmlPackageInit", rpmMetadata) as String
            }
            storeXmlNode(targetXmlString, repodataPath!!, context)
            // 删除多余索引节点
            GlobalScope.launch {
                primaryNodelist?.let { surplusNodeCleaner.deleteSurplusNode(it) }
            }.start()
        }
    }

    /**
     * 保存索引节点
     */
    private fun storeXmlNode(xmlStr: String, repodataPath: String, context: ArtifactUploadContext) {
        (xmlStr.toByteArray()).let { ByteInputStream(it, it.size) }.use { xmlInputStream ->
            // 处理xml节点

            // xml.gz文件sha1
            val xmlGZFile = xmlInputStream.bytes.gZip()
            val xmlGZFileSha1 = FileInputStream(xmlGZFile).sha1()

            // 先保存primary-xml.gz文件
            val xmlGZArtifact = ArtifactFileFactory.build(FileInputStream(xmlGZFile))
            val fullPath = "/${repodataPath}repodata/$xmlGZFileSha1-primary.xml.gz"
            val xmlPrimaryNode = xmlPrimaryNodeCreate(context.userId,
                    context.repositoryInfo,
                    fullPath,
                    xmlGZArtifact)
            storageService.store(xmlPrimaryNode.sha256!!, xmlGZArtifact, context.storageCredentials)
            nodeResource.create(xmlPrimaryNode)
            xmlGZFile.delete()

            // 更新repomd.xml
            // xml文件sha1
            val xmlFileSha1 = xmlInputStream.sha1()
            storeRepomdNode(xmlGZFileSha1, xmlGZArtifact, xmlFileSha1, repodataPath, context)
        }
    }

    /**
     * 更新repomd.xml
     */
    private fun storeRepomdNode(xmlGZFileSha1: String, xmlGZArtifact: ArtifactFile, xmlFileSha1: String, repodataPath: String, context: ArtifactUploadContext) {
        val repoMdPath = "repodata/$xmlGZFileSha1-primary.xml.gz"
        val rpmRepoMd = RpmRepoMd(
                type = "primary",
                location = repoMdPath,
                size = xmlGZArtifact.getSize(),
                lastModified = System.currentTimeMillis().toString(),
                xmlFileSha1 = xmlFileSha1,
                xmlGZFileSha1 = xmlGZFileSha1
        )

        val xmlRepodataString = xmlScript.invokeMethod("wrapperRepomd", listOf(rpmRepoMd)) as String
        (xmlRepodataString.toByteArray()).let { ByteInputStream(it, it.size) }.use { xmlRepodataInputStream ->
            val xmlRepodataArtifact = ArtifactFileFactory.build(xmlRepodataInputStream)
            // 保存repodata 节点
            val xmlRepomdNode = xmlPrimaryNodeCreate(context.userId,
                    context.repositoryInfo,
                    "/${repodataPath}repodata/repomd.xml",
                    xmlRepodataArtifact)
            storageService.store(xmlRepomdNode.sha256!!, xmlRepodataArtifact, context.storageCredentials)
            nodeResource.create(xmlRepomdNode)
        }
    }

    /**
     * 检查上传的构件是否已在仓库中，判断条件：uri && sha256
     * 降低并发对索引文件的影响
     * @return false 有重复构件，只保存构件
     * @return true 无重复构件
     */
    private fun checkRepeatArtifact(context: ArtifactUploadContext): Boolean {
        val artifactUri = context.artifactInfo.artifactUri
        val artifactSha256 = context.getArtifactFile().getFileSha256()

        return with(context.artifactInfo) {
            val projectQuery = Rule.QueryRule("projectId", projectId)
            val repositoryQuery = Rule.QueryRule("repoName", repoName)
            val sha256Query = Rule.QueryRule("sha256", artifactSha256)
            val fullPathQuery = Rule.QueryRule("fullPath", artifactUri)

            val queryRule = Rule.NestedRule(mutableListOf(projectQuery, repositoryQuery, sha256Query, fullPathQuery),
                    Rule.NestedRule.RelationType.AND)
            val queryModel = QueryModel(
                    page = PageLimit(0, 10),
                    sort = Sort(listOf("name"), Sort.Direction.ASC),
                    select = mutableListOf("projectId", "repoName", "fullPath"),
                    rule = queryRule
            )
            val nodeList = nodeResource.query(queryModel).data?.records
            nodeList.isNullOrEmpty()
        }
    }

    private fun successUpload(context: ArtifactUploadContext, mark: Boolean) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=UTF-8"
        with(context.artifactInfo) {
            val description = if (mark) INDEXER else String.format(NO_INDEXER, "$projectId/$repoName", searchRpmRepoDataDepth(context), artifactUri)
            val rpmUploadResponse = RpmUploadResponse(projectId, repoName, artifactUri, context.getArtifactFile().getFileSha256(), context.getArtifactFile().getFileMd5(), description)
            response.writer.print(rpmUploadResponse.toJsonString())
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        val mark: Boolean = checkRequestUri(context)
        // 检查请求路径是否契合仓库repodataDepth 深度设置
        if (mark && checkRepeatArtifact(context)) {
            indexer(context)
        }
        // 保存rpm 包
        val nodeCreateRequest = rpmNodeCreateRequest(context)
        storageService.store(nodeCreateRequest.sha256!!, context.getArtifactFile(), context.storageCredentials)
        nodeResource.create(nodeCreateRequest)
        successUpload(context, mark)
    }
}
