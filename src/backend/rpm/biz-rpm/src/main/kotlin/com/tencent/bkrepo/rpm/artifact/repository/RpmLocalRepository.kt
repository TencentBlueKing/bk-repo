package com.tencent.bkrepo.rpm.artifact.repository

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_SHA256
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
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.FULLPATH_SHA256
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.NONE
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.FULLPATH
import com.tencent.bkrepo.rpm.pojo.RpmUploadResponse
import com.tencent.bkrepo.rpm.util.GZipUtil.unGzipInputStream
import com.tencent.bkrepo.rpm.util.GZipUtil.gZip
import com.tencent.bkrepo.rpm.util.XmlStrUtil
import com.tencent.bkrepo.rpm.util.redline.model.RpmMetadataWithOldStream
import com.tencent.bkrepo.rpm.util.rpm.RpmMetadataUtil
import com.tencent.bkrepo.rpm.util.rpm.RpmFormatUtil
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil.objectToXml
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmChecksum
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmLocation
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoData
import com.tencent.bkrepo.rpm.util.xStream.repomd.Repomd
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.FileInputStream
import java.nio.channels.Channels

@Component
class RpmLocalRepository(
    val surplusNodeCleaner: SurplusNodeCleaner
) : LocalRepository() {

    fun rpmNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val nodeCreateRequest = super.getNodeCreateRequest(context)
        return nodeCreateRequest.copy(overwrite = true)
    }

    fun xmlPrimaryNodeCreate(
        userId: String,
        repositoryInfo: RepositoryInfo,
        fullPath: String,
        xmlGZArtifact: ArtifactFile
    ): NodeCreateRequest {
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
        val artifactUri = context.artifactInfo.artifactUri
            .removePrefix("/").split("/").size
        repodataDepth?.let { return artifactUri > repodataDepth }
        return false
    }

    /**
     * 生成构件索引
     */
    private fun indexer(context: ArtifactUploadContext, repeat: ArtifactRepeat) {

        val repodataDepth = searchRpmRepoDataDepth(context)
        val uriMap = XmlStrUtil.splitUriByDepth(context.artifactInfo.artifactUri, repodataDepth)
        val repodataPath = uriMap.repodataPath

        val artifactFile = context.getArtifactFile()
        val rpmFormat = RpmFormatUtil.getRpmFormat(Channels.newChannel(artifactFile.getInputStream()))

        val sha1Digest = artifactFile.getInputStream().sha1()
        val artifactRelativePath = uriMap.artifactRelativePath
        val rpmMetadata = RpmMetadataUtil().interpret(
            rpmFormat,
            artifactFile.getSize(),
            sha1Digest,
            artifactRelativePath
        )

        with(context.repositoryInfo) {
            // repodata下'-primary.xml.gz'最新节点。
            val nodeList = nodeResource.list(
                projectId, name,
                "/${repodataPath}repodata",
                includeFolder = false, deep = false
            ).data
            val primaryNodelist = nodeList?.filter {
                it.name.endsWith("-primary.xml.gz")
            }?.sortedByDescending {
                it.createdDate
            }

            val targetXmlString = if (!primaryNodelist.isNullOrEmpty()) {
                val latestPrimaryNode = primaryNodelist[0]
                val inputStream = storageService.load(
                    latestPrimaryNode.sha256!!,
                    Range.full(latestPrimaryNode.size),
                    context.storageCredentials
                ) ?: return
                val rpmMetadataWithOldStream = RpmMetadataWithOldStream(rpmMetadata, inputStream.unGzipInputStream())
                // 更新primary.xml
                if (repeat == NONE) {
                    XmlStrUtil.insertPackage(rpmMetadataWithOldStream)
                } else {
                    XmlStrUtil.updatePackage(rpmMetadataWithOldStream)
                }
            } else {
                // first upload
                rpmMetadata.objectToXml()
            }
            storeXmlNode(targetXmlString, repodataPath, context)
            // 删除多余索引节点
            GlobalScope.launch {
                primaryNodelist?.let { surplusNodeCleaner.deleteSurplusNode(it) }
            }.start()
        }
    }

    /**
     * 保存索引节点
     * @param xmlStr "-primary.xml" 索引文件内容
     * @param repodataPath 契合本次请求的repodata_depth 目录路径
     */
    private fun storeXmlNode(xmlStr: String, repodataPath: String, context: ArtifactUploadContext) {
        (xmlStr.toByteArray()).let { ByteInputStream(it, it.size) }.use { xmlInputStream ->
            // 处理xml节点
            val xmlFileSize = xmlInputStream.bytes.size
            // xml.gz文件sha1
            val xmlGZFile = xmlInputStream.bytes.gZip()
            val xmlGZFileSha1 = FileInputStream(xmlGZFile).sha1()

            // 先保存primary-xml.gz文件
            val xmlGZArtifact = ArtifactFileFactory.build(FileInputStream(xmlGZFile))
            val fullPath = "/${repodataPath}repodata/$xmlGZFileSha1-primary.xml.gz"
            val xmlPrimaryNode = xmlPrimaryNodeCreate(
                context.userId,
                context.repositoryInfo,
                fullPath,
                xmlGZArtifact
            )
            storageService.store(xmlPrimaryNode.sha256!!, xmlGZArtifact, context.storageCredentials)
            nodeResource.create(xmlPrimaryNode)
            xmlGZFile.delete()

            // 更新repomd.xml
            // xml文件sha1
            val xmlFileSha1 = xmlInputStream.sha1()
            storeRepomdNode(xmlFileSize, xmlGZFileSha1, xmlGZArtifact, xmlFileSha1, repodataPath, context)
        }
    }

    /**
     * 更新repomd.xml
     */
    private fun storeRepomdNode(
        xmlFileSize: Int,
        xmlGZFileSha1: String,
        xmlGZArtifact: ArtifactFile,
        xmlFileSha1: String,
        repodataPath: String,
        context: ArtifactUploadContext
    ) {
        val repoMdPath = "repodata/$xmlGZFileSha1-primary.xml.gz"
        val repomd = Repomd(
            listOf(
                RepoData(
                    type = "primary",
                    location = RpmLocation(repoMdPath),
                    checksum = RpmChecksum(xmlGZFileSha1),
                    size = xmlGZArtifact.getSize(),
                    timestamp = System.currentTimeMillis().toString(),
                    openChecksum = RpmChecksum(xmlFileSha1),
                    openSize = xmlFileSize
                )
            )
        )
        val xmlRepodataString = repomd.objectToXml()
        (xmlRepodataString.toByteArray()).let { ByteInputStream(it, it.size) }.use { xmlRepodataInputStream ->
            val xmlRepodataArtifact = ArtifactFileFactory.build(xmlRepodataInputStream)
            // 保存repodata 节点
            val xmlRepomdNode = xmlPrimaryNodeCreate(
                context.userId,
                context.repositoryInfo,
                "/${repodataPath}repodata/repomd.xml",
                xmlRepodataArtifact
            )
            storageService.store(xmlRepomdNode.sha256!!, xmlRepodataArtifact, context.storageCredentials)
            nodeResource.create(xmlRepomdNode)
        }
    }

    /**
     * 检查上传的构件是否已在仓库中，判断条件：uri && sha256
     * 降低并发对索引文件的影响
     * @return ArtifactRepeat.FULLPATH_SHA256 存在完全相同构件，不操作索引
     * @return ArtifactRepeat.FULLPATH 请求路径相同，但内容不同，更新索引
     * @return ArtifactRepeat.NONE 无重复构件
     */
    private fun checkRepeatArtifact(context: ArtifactUploadContext): ArtifactRepeat {
        val artifactUri = context.artifactInfo.artifactUri
        val artifactSha256 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA256] as String

        return with(context.artifactInfo) {
            val projectQuery = Rule.QueryRule("projectId", projectId)
            val repositoryQuery = Rule.QueryRule("repoName", repoName)
            val fullPathQuery = Rule.QueryRule("fullPath", artifactUri)

            val queryRule = Rule.NestedRule(
                mutableListOf(projectQuery, repositoryQuery, fullPathQuery),
                Rule.NestedRule.RelationType.AND
            )
            val queryModel = QueryModel(
                page = PageLimit(0, 10),
                sort = Sort(listOf("name"), Sort.Direction.ASC),
                select = mutableListOf("projectId", "repoName", "fullPath", "sha256"),
                rule = queryRule
            )
            val nodeList = nodeResource.query(queryModel).data?.records
            if (nodeList.isNullOrEmpty()) {
                NONE
            } else {
                // 上传时重名构件默认是覆盖操作，所以只会存在一个重名构件。
                if (nodeList.first()["sha256"] == artifactSha256) {
                    FULLPATH_SHA256
                } else {
                    FULLPATH
                }
            }
        }
    }

    private fun successUpload(context: ArtifactUploadContext, mark: Boolean) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=UTF-8"
        with(context.artifactInfo) {
            val description = if (mark) {
                INDEXER
            } else {
                String.format(NO_INDEXER, "$projectId/$repoName", searchRpmRepoDataDepth(context), artifactUri)
            }
            val rpmUploadResponse = RpmUploadResponse(
                projectId, repoName, artifactUri,
                context.getArtifactFile().getFileSha256(), context.getArtifactFile().getFileMd5(), description
            )
            response.writer.print(rpmUploadResponse.toJsonString())
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun onUpload(context: ArtifactUploadContext) {
        // 检查请求路径是否契合仓库repodataDepth 深度设置
        val mark: Boolean = checkRequestUri(context)
        val repeat = checkRepeatArtifact(context)
        if (mark && (repeat != FULLPATH_SHA256)) { indexer(context, repeat) }
        // 保存rpm 包
        val nodeCreateRequest = rpmNodeCreateRequest(context)
        storageService.store(nodeCreateRequest.sha256!!, context.getArtifactFile(), context.storageCredentials)
        nodeResource.create(nodeCreateRequest)
        successUpload(context, mark)
    }
}
