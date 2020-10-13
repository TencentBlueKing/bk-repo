package com.tencent.bkrepo.rpm.artifact.repository

import com.tencent.bkrepo.common.api.constant.StringPool.DASH
import com.tencent.bkrepo.common.api.constant.StringPool.DOT
import com.tencent.bkrepo.common.api.constant.StringPool.SLASH
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.UnsupportedMethodException
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.rpm.REPODATA
import com.tencent.bkrepo.rpm.OTHERS
import com.tencent.bkrepo.rpm.PRIMARY
import com.tencent.bkrepo.rpm.XMLGZ
import com.tencent.bkrepo.rpm.FILELISTS
import com.tencent.bkrepo.rpm.INDEXER
import com.tencent.bkrepo.rpm.NO_INDEXER
import com.tencent.bkrepo.rpm.GZ
import com.tencent.bkrepo.rpm.artifact.SurplusNodeCleaner
import com.tencent.bkrepo.rpm.exception.RpmArtifactFormatNotSupportedException
import com.tencent.bkrepo.rpm.exception.RpmArtifactMetadataResolveException
import com.tencent.bkrepo.rpm.pojo.RpmRepoConf
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat
import com.tencent.bkrepo.rpm.pojo.RpmVersion
import com.tencent.bkrepo.rpm.pojo.RpmUploadResponse
import com.tencent.bkrepo.rpm.pojo.RpmDeleteResponse
import com.tencent.bkrepo.rpm.pojo.ArtifactFormat
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.FULLPATH_SHA256
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.NONE
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat.FULLPATH
import com.tencent.bkrepo.rpm.util.GZipUtils.unGzipInputStream
import com.tencent.bkrepo.rpm.util.GZipUtils.gZip
import com.tencent.bkrepo.rpm.util.XmlStrUtils
import com.tencent.bkrepo.rpm.util.rpm.RpmMetadataUtils
import com.tencent.bkrepo.rpm.util.rpm.RpmFormatUtils
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil.objectToXml
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmXmlMetadata
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadataChangeLog
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadataFileList
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmPackageFileList
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmPackageChangeLog
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmLocation
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmChecksum
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoData
import com.tencent.bkrepo.rpm.util.xStream.repomd.Repomd
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.nio.channels.Channels
import com.tencent.bkrepo.rpm.pojo.ArtifactFormat.RPM
import com.tencent.bkrepo.rpm.pojo.ArtifactFormat.XML
import com.tencent.bkrepo.rpm.util.RpmCollectionUtils.filterRpmCustom
import com.tencent.bkrepo.rpm.util.RpmHeaderUtils.calculatePackages
import com.tencent.bkrepo.rpm.util.RpmHeaderUtils.getRpmBooleanHeader
import com.tencent.bkrepo.rpm.util.RpmStringUtils.toRpmVersion
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toMetadata
import com.tencent.bkrepo.rpm.util.XmlStrUtils.getGroupNodeFullPath
import com.tencent.bkrepo.rpm.util.XmlStrUtils.rpmMetadataToPackageXml
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoGroup
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoIndex
import java.io.File

@Component
class RpmLocalRepository(
    val surplusNodeCleaner: SurplusNodeCleaner
) : LocalRepository() {

    fun rpmNodeCreateRequest(
        context: ArtifactUploadContext,
        metadata: MutableMap<String, String>?
    ): NodeCreateRequest {
        val nodeCreateRequest = super.getNodeCreateRequest(context)
        return nodeCreateRequest.copy(
            metadata = metadata,
            overwrite = true
        )
    }

    fun xmlIndexNodeCreate(
        userId: String,
        repositoryInfo: RepositoryInfo,
        fullPath: String,
        xmlGZArtifact: ArtifactFile,
        metadata: MutableMap<String, String>
    ): NodeCreateRequest {
        val sha256 = xmlGZArtifact.getFileSha256()
        val md5 = xmlGZArtifact.getFileMd5()
        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            overwrite = true,
            fullPath = fullPath,
            size = xmlGZArtifact.getSize(),
            sha256 = sha256,
            md5 = md5,
            operator = userId,
            metadata = metadata
        )
    }

    fun xmlNodeCreate(
        userId: String,
        repositoryInfo: RepositoryInfo,
        fullPath: String,
        xmlGZArtifact: ArtifactFile
    ): NodeCreateRequest {
        val sha256 = xmlGZArtifact.getFileSha256()
        val md5 = xmlGZArtifact.getFileMd5()
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
     * 查询rpm仓库属性
     */
    private fun getRpmRepoConf(context: ArtifactTransferContext): RpmRepoConf {
        val rpmConfiguration = context.repositoryInfo.configuration as RpmLocalConfiguration
        val repodataDepth = rpmConfiguration.repodataDepth ?: 0
        val enabledFileLists = rpmConfiguration.enabledFileLists ?: true
        val groupXmlSet = rpmConfiguration.groupXmlSet ?: mutableSetOf()
        return RpmRepoConf(repodataDepth, enabledFileLists, groupXmlSet)
    }

    /**
     * 检查请求uri地址的层级是否 > 仓库设置的repodata 深度
     * @return true 将会计算rpm包的索引
     * @return false 只提供文件服务器功能，返回提示信息
     */
    private fun checkRequestUri(context: ArtifactUploadContext, repodataDepth: Int): Boolean {
        val artifactUri = context.artifactInfo.artifactUri
            .removePrefix(SLASH).split(SLASH).size
        return artifactUri > repodataDepth
    }

    /**
     * 生成构件索引
     * 只生成primary和other索引。
     */
    private fun indexer(context: ArtifactUploadContext, repeat: ArtifactRepeat, rpmRepoConf: RpmRepoConf) {
        val repodataDepth = rpmRepoConf.repodataDepth
        val repodataUri = XmlStrUtils.splitUriByDepth(context.artifactInfo.artifactUri, repodataDepth)
        val repodataPath = repodataUri.repodataPath

        val artifactFile = context.getArtifactFile()
        val rpmFormat = RpmFormatUtils.getRpmFormat(Channels.newChannel(artifactFile.getInputStream()))

        val sha1Digest = artifactFile.getInputStream().sha1()
        val artifactRelativePath = repodataUri.artifactRelativePath
        val rpmMetadata = RpmMetadataUtils().interpret(
            rpmFormat,
            artifactFile.getSize(),
            sha1Digest,
            artifactRelativePath
        )
        if (rpmRepoConf.enabledFileLists) {
            val rpmMetadataFileList = RpmMetadataFileList(
                listOf(
                    RpmPackageFileList(
                        rpmMetadata.packages[0].checksum.checksum,
                        rpmMetadata.packages[0].name,
                        rpmMetadata.packages[0].version,
                        rpmMetadata.packages[0].format.files
                    )
                ),
                1L
            )
            // 单独存储每个包的filelists.xml
            storeFileListsXml(context, rpmMetadataFileList, repodataPath, repeat)
        }
        //过滤files中的文件
        rpmMetadata.packages[0].format.files = rpmMetadata.packages[0].format.files.filter {
            (it.filePath.contains("bin/") && (it.filePath.endsWith(".sh"))) ||
                    (it.filePath.startsWith("/etc/") && it.filePath.contains("conf")) ||
                    it.filePath == "/usr/lib/sendmail"
        }
        val rpmMetadataChangeLog = RpmMetadataChangeLog(
            listOf(
                RpmPackageChangeLog(
                    rpmMetadata.packages[0].checksum.checksum,
                    rpmMetadata.packages[0].name,
                    rpmMetadata.packages[0].version,
                    rpmMetadata.packages[0].format.changeLogs
                )
            ),
            1L
        )
        // 更新others.xml
        updateIndexXml(context, rpmMetadataChangeLog, repeat, repodataPath, OTHERS)
        rpmMetadata.packages[0].format.changeLogs.clear()
        // 更新primary.xml
        updateIndexXml(context, rpmMetadata, repeat, repodataPath, PRIMARY)
        flushRepoMdXML(context, null)
    }

    private fun storeFileListsXml(
            context: ArtifactUploadContext,
            rpmXmlMetadata: RpmXmlMetadata,
            repodataPath: String,
            repeat: ArtifactRepeat
    ) {
        with(context.artifactInfo) {
            val rpmXml = rpmXmlMetadata.rpmMetadataToPackageXml(FILELISTS)

            val fileName = artifactUri.split("/").last()
            val tempFileName = StringBuilder(fileName.removeSuffix("rpm")).append("xml")
            val rpmXmlFile = ArtifactFileFactory.build(ByteArrayInputStream(rpmXml.toByteArray()))
            val metadata = mutableMapOf(
                    "repeat" to repeat.name
            )
            val xmlXmlFileNode = xmlIndexNodeCreate(
                    context.userId,
                    context.repositoryInfo,
                    "/$repodataPath/$REPODATA/temp/$tempFileName",
                    rpmXmlFile,
                    metadata
            )
            storageService.store(xmlXmlFileNode.sha256!!, rpmXmlFile, context.storageCredentials)
            with(xmlXmlFileNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
            nodeClient.create(xmlXmlFileNode)
            logger.info("Success to insert $xmlXmlFileNode")
        }
    }


    private fun updateIndexXml(
        context: ArtifactUploadContext,
        rpmXmlMetadata: RpmXmlMetadata,
        repeat: ArtifactRepeat,
        repodataPath: String,
        indexType: String
    ) {
        val target = "$DASH$indexType$DOT$XMLGZ"
        val calculatePackage = HeaderUtils.calculatePackages()
        with(context.artifactInfo) {
            // repodata下'-**.xml.gz'最新节点。
            val nodeList = nodeClient.list(
                projectId, repoName,
                "$SLASH${repodataPath}$REPODATA",
                includeFolder = false, deep = false
            ).data
            val targetNodelist = nodeList?.filter {
                it.name.endsWith(target)
            }?.sortedByDescending {
                it.createdDate
            }

            if (!targetNodelist.isNullOrEmpty()) {
                val latestPrimaryNode = targetNodelist[0]
                val inputStream = storageService.load(
                    latestPrimaryNode.sha256!!,
                    Range.full(latestPrimaryNode.size),
                    context.storageCredentials
                ) ?: return
                // 更新primary.xml
                val xmlFile = if (repeat == NONE) {
                    XmlStrUtils.insertPackage(indexType, inputStream.unGzipInputStream(), rpmXmlMetadata, calculatePackage)
                } else {
                    XmlStrUtils.updatePackage(indexType, inputStream.unGzipInputStream(), rpmXmlMetadata, artifactUri)
                }
                try {
                    storeXmlFileNode(indexType, xmlFile, repodataPath, context, target)
                } finally {
                    xmlFile.delete()
                }
            } else {
                // first upload
                storeXmlNode(indexType, rpmXmlMetadata.objectToXml(), repodataPath, context, target)
            }
            // 删除多余索引节点
            GlobalScope.launch {
                targetNodelist?.let { surplusNodeCleaner.deleteSurplusNode(it) }
            }.start()
        }
    }

    private fun deleteIndexXml(
        context: ArtifactRemoveContext,
        rpmVersion: RpmVersion,
        repodataPath: String,
        indexType: String
    ) {
        val target = "$DASH$indexType$DOT$XMLGZ"
        with(context.artifactInfo) {
            // repodata下'-**.xml.gz'最新节点。
            val nodeList = nodeClient.list(
                projectId, repoName,
                "$SLASH${repodataPath}$REPODATA",
                includeFolder = false, deep = false
            ).data
            val location = artifactUri.removePrefix("$SLASH$repodataPath")
            val targetNodelist = nodeList?.filter {
                it.name.endsWith(target)
            }?.sortedByDescending {
                it.createdDate
            }
            val xmlFile = if (!targetNodelist.isNullOrEmpty()) {
                val latestPrimaryNode = targetNodelist[0]
                val inputStream = storageService.load(
                    latestPrimaryNode.sha256!!,
                    Range.full(latestPrimaryNode.size),
                    context.storageCredentials
                ) ?: return
                XmlStrUtils.deletePackage(indexType, inputStream.unGzipInputStream(), rpmVersion, location)
            } else {
                deleteFailed(context, "未找到$indexType.xml.gz 索引文件")
                return
            }
            // 删除多余索引节点
            GlobalScope.launch {
                targetNodelist.let { surplusNodeCleaner.deleteSurplusNode(it) }
            }.start()
            try {
                storeXmlFileNode(indexType, xmlFile, repodataPath, context, target)
            } finally {
                xmlFile.delete()
            }
        }
    }

    private fun storeXmlFileNode(
        indexType: String,
        xmlFile: File,
        repodataPath: String,
        context: ArtifactTransferContext,
        target: String
    ) {

        // 保存节点同时保存节点信息到元数据方便repomd更新。
        val xmlInputStream = FileInputStream(xmlFile)
        val xmlFileSize = xmlFile.length()

        val xmlGZFile = xmlInputStream.gZip(indexType)
        val xmlFileSha1 = xmlInputStream.sha1()
        try {
            val xmlGZFileSha1 = FileInputStream(xmlGZFile).sha1()

            val xmlGZArtifact = ArtifactFileFactory.build(FileInputStream(xmlGZFile))
            val fullPath = "$SLASH${repodataPath}$REPODATA$SLASH$xmlGZFileSha1$target"
            val metadata = mutableMapOf(
                "indexType" to indexType,
                "checksum" to xmlGZFileSha1,
                "size" to (xmlGZArtifact.getSize().toString()),
                "timestamp" to System.currentTimeMillis().toString(),
                "openChecksum" to xmlFileSha1,
                "openSize" to (xmlFileSize.toString())
            )
            val xmlPrimaryNode = xmlIndexNodeCreate(
                context.userId,
                context.repositoryInfo,
                fullPath,
                xmlGZArtifact,
                metadata
            )
            storageService.store(xmlPrimaryNode.sha256!!, xmlGZArtifact, context.storageCredentials)
            with(xmlPrimaryNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
            nodeClient.create(xmlPrimaryNode)
            logger.info("Success to insert $xmlPrimaryNode")
        } finally {
            xmlGZFile.delete()
            xmlInputStream.closeQuietly()
        }
    }

    /**
     * 保存索引节点
     * @param xmlStr ".xml" 索引文件内容
     * @param repodataPath 契合本次请求的repodata_depth 目录路径
     */
    private fun storeXmlNode(
        indexType: String,
        xmlStr: String,
        repodataPath: String,
        context: ArtifactTransferContext,
        target: String
    ) {
        ByteArrayInputStream((xmlStr.toByteArray())).use { xmlInputStream ->
            // 保存节点同时保存节点信息到元数据方便repomd更新。
            val xmlFileSize = xmlStr.toByteArray().size

            val xmlGZFile = xmlStr.toByteArray().gZip(indexType)
            val xmlFileSha1 = xmlInputStream.sha1()
            try {
                val xmlGZFileSha1 = FileInputStream(xmlGZFile).sha1()

                // 先保存primary-xml.gz文件
                val xmlGZArtifact = ArtifactFileFactory.build(FileInputStream(xmlGZFile))
                val fullPath = "$SLASH${repodataPath}$REPODATA$SLASH$xmlGZFileSha1$target"
                val metadata = mutableMapOf(
                    "indexType" to indexType,
                    "checksum" to xmlGZFileSha1,
                    "size" to (xmlGZArtifact.getSize().toString()),
                    "timestamp" to System.currentTimeMillis().toString(),
                    "openChecksum" to xmlFileSha1,
                    "openSize" to (xmlFileSize.toString())
                )
                val xmlPrimaryNode = xmlIndexNodeCreate(
                    context.userId,
                    context.repositoryInfo,
                    fullPath,
                    xmlGZArtifact,
                    metadata
                )
                storageService.store(xmlPrimaryNode.sha256!!, xmlGZArtifact, context.storageCredentials)
                with(xmlPrimaryNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
                nodeClient.create(xmlPrimaryNode)
                logger.info("Success to insert $xmlPrimaryNode")
            } finally {
                xmlGZFile.delete()
            }
        }
    }

    /**
     * 检查上传的构件是否已在仓库中，判断条件：uri && sha256
     * 降低并发对索引文件的影响
     * ArtifactRepeat.FULLPATH_SHA256 存在完全相同构件，不操作索引
     * ArtifactRepeat.FULLPATH 请求路径相同，但内容不同，更新索引
     * ArtifactRepeat.NONE 无重复构件
     */
    private fun checkRepeatArtifact(context: ArtifactUploadContext): ArtifactRepeat {
        val artifactUri = context.artifactInfo.artifactUri
        val artifactSha256 = context.getArtifactFile().getFileSha256()

        return with(context.artifactInfo) {
            val node = nodeClient.detail(projectId, repoName, artifactUri).data
            if (node == null) {
                NONE
            } else {
                if (node.sha256 == artifactSha256) {
                    FULLPATH_SHA256
                } else {
                    FULLPATH
                }
            }
        }
    }

    private fun successUpload(context: ArtifactUploadContext, mark: Boolean, repodataDepth: Int) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=UTF-8"
        with(context.artifactInfo) {
            val description = if (mark) {
                INDEXER
            } else {
                String.format(NO_INDEXER, "$projectId/$repoName", repodataDepth, artifactUri)
            }
            val rpmUploadResponse = RpmUploadResponse(
                projectId, repoName, artifactUri,
                context.getArtifactFile().getFileSha256(), context.getArtifactFile().getFileMd5(), description
            )
            response.writer.print(rpmUploadResponse.toJsonString())
        }
    }

    private fun deleteFailed(context: ArtifactRemoveContext, description: String) {
        val response = HttpContextHolder.getResponse()
        response.contentType = "application/json; charset=UTF-8"
        with(context.artifactInfo) {
            val rpmUploadResponse = RpmDeleteResponse(
                projectId, repoName, artifactUri,
                description
            )
            response.writer.print(rpmUploadResponse.toJsonString())
        }
    }

    private fun getArtifactFormat(context: ArtifactUploadContext): ArtifactFormat {
        val format = context.artifactInfo.artifactUri
            .split(SLASH).last().split(".").last()
        return when (format) {
            "xml" -> XML
            "rpm" -> RPM
            else -> {
                with(context.artifactInfo) { logger.info("$projectId/$repoName/$artifactUri: 格式不被接受") }
                throw RpmArtifactFormatNotSupportedException("rpm not supported `$format` artifact")
            }
        }
    }

    // 保存分组文件
    private fun storeGroupFile(context: ArtifactUploadContext) {
        val xmlByteArray = context.getArtifactFile().getInputStream().readBytes()
        val filename = context.artifactInfo.artifactUri.split("/").last()

        // 保存xml
        val xmlSha1 = context.getArtifactFile().getInputStream().sha1()
        val xmlSha1ArtifactFile = ArtifactFileFactory.build(xmlByteArray.inputStream())
        val metadata = mutableMapOf(
            "indexName" to filename,
            "indexType" to "group",
            "checksum" to xmlSha1,
            "size" to (xmlSha1ArtifactFile.getSize().toString()),
            "timestamp" to System.currentTimeMillis().toString()
        )
        val xmlNode = xmlIndexNodeCreate(
            context.userId,
            context.repositoryInfo,
            getGroupNodeFullPath(context.artifactInfo.artifactUri, xmlSha1),
            xmlSha1ArtifactFile,
            metadata
        )
        storageService.store(xmlNode.sha256!!, xmlSha1ArtifactFile, context.storageCredentials)
        with(xmlNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
        nodeClient.create(xmlNode)
        logger.info("Success to insert $xmlNode")
        xmlSha1ArtifactFile.delete()

        // 保存xml.gz
        val groupGZFile = xmlByteArray.gZip("random")
        try {
            val xmlGZFileSha1 = FileInputStream(groupGZFile).sha1()
            val groupGZArtifactFile = ArtifactFileFactory.build(FileInputStream(groupGZFile))
            val metadataGZ = mutableMapOf(
                "indexName" to "${filename}_gz",
                "indexType" to "group_gz",
                "checksum" to xmlGZFileSha1,
                "size" to (groupGZArtifactFile.getSize().toString()),
                "timestamp" to System.currentTimeMillis().toString()
            )
            val groupGZNode = xmlIndexNodeCreate(
                context.userId,
                context.repositoryInfo,
                getGroupNodeFullPath("${context.artifactInfo.artifactUri}$DOT$GZ", xmlGZFileSha1),
                groupGZArtifactFile,
                metadataGZ
            )
            storageService.store(groupGZNode.sha256!!, groupGZArtifactFile, context.storageCredentials)
            with(groupGZNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
            nodeClient.create(groupGZNode)
            logger.info("Success to insert $groupGZNode")
            groupGZArtifactFile.delete()
        } finally {
            groupGZFile.delete()
        }

        // todo
        // 删除多余节点
        flushRepoMdXML(context, null)
    }

    /**
     * 默认刷新匹配请求路径对应的repodata目录下的`repomd.xml`内容，
     * 当[repoDataPath]不为空时，刷新指定的[repoDataPath]目录下的`repomd.xml`内容
     *
     */
    fun flushRepoMdXML(context: ArtifactTransferContext, repoDataPath: String?) {
        // 查询添加的groups
        val rpmRepoConf = getRpmRepoConf(context)
        val groupXmlSet = rpmRepoConf.groupXmlSet
        val repodataDepth = rpmRepoConf.repodataDepth
        val indexPath = if (repoDataPath == null) {
            val repodataUri = XmlStrUtils.splitUriByDepth(context.artifactInfo.artifactUri, repodataDepth)
            "${repodataUri.repodataPath}$REPODATA"
        } else {
            repoDataPath
        }
        val limit = groupXmlSet.size * 3 + 9

        // 查询该请求路径对应的索引目录下所有文件
        val nodeList = with(context.artifactInfo) {
            (
                nodeClient.page(
                    projectId, repoName, 0, limit,
                    "$SLASH$indexPath$SLASH",
                    includeMetadata = true
                ).data ?: return
                ).records
                .sortedByDescending {
                    it.lastModifiedDate
                }
        }

        val targetIndexList = nodeList.filterRpmCustom(groupXmlSet, rpmRepoConf.enabledFileLists)

        val repoDataList = mutableListOf<RepoIndex>()
        for (index in targetIndexList) {
            repoDataList.add(
                if ((index.name).contains(Regex("-filelists.xml.gz|-others|-primary"))) {
                    RepoData(
                        type = index.metadata?.get("indexType") as String,
                        location = RpmLocation("$REPODATA$SLASH${index.name}"),
                        checksum = RpmChecksum(index.metadata?.get("checksum") as String),
                        size = (index.metadata?.get("size") as String).toLong(),
                        timestamp = index.metadata?.get("timestamp") as String,
                        openChecksum = RpmChecksum(index.metadata?.get("openChecksum") as String),
                        openSize = (index.metadata?.get("openSize") as String).toInt()
                    )
                } else {
                    RepoGroup(
                        type = index.metadata?.get("indexType") as String,
                        location = RpmLocation("$REPODATA$SLASH${index.name}"),
                        checksum = RpmChecksum(index.metadata?.get("checksum") as String),
                        size = (index.metadata?.get("size") as String).toLong(),
                        timestamp = index.metadata?.get("timestamp") as String
                    )
                }
            )
        }

        val repomd = Repomd(
            repoDataList
        )
        val xmlRepodataString = repomd.objectToXml()
        ByteArrayInputStream((xmlRepodataString.toByteArray())).use { xmlRepodataInputStream ->
            val xmlRepodataArtifact = ArtifactFileFactory.build(xmlRepodataInputStream)
            // 保存repodata 节点
            val xmlRepomdNode = xmlNodeCreate(
                context.userId,
                context.repositoryInfo,
                "$SLASH${indexPath}${SLASH}repomd.xml",
                xmlRepodataArtifact
            )
            storageService.store(xmlRepomdNode.sha256!!, xmlRepodataArtifact, context.storageCredentials)
            with(xmlRepomdNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
            nodeClient.create(xmlRepomdNode)
            logger.info("Success to insert $xmlRepomdNode")
            xmlRepodataArtifact.delete()
        }
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun onUpload(context: ArtifactUploadContext) {
        val overwrite = HeaderUtils.getRpmBooleanHeader("X-BKREPO-OVERWRITE")
        if (!overwrite) {
            with(context.artifactInfo) {
                val node = nodeClient.detail(projectId, repoName, artifactUri).data
                if (node != null) {
                    throw ErrorCodeException(ArtifactMessageCode.NODE_EXISTED, artifactUri)
                }
            }
        }
        val artifactFormat = getArtifactFormat(context)
        val rpmRepoConf = getRpmRepoConf(context)
        val mark: Boolean = checkRequestUri(context, rpmRepoConf.repodataDepth)
        val repeat = checkRepeatArtifact(context)
        if (repeat != FULLPATH_SHA256) {
            val nodeCreateRequest = if (mark) {
                when (artifactFormat) {
                    RPM -> {
                        indexer(context, repeat, rpmRepoConf)
                        val rpmVersion = context.artifactInfo.artifactUri.toRpmVersion()
                        rpmNodeCreateRequest(context, rpmVersion.toMetadata())
                    }
                    XML -> {
                        storeGroupFile(context)
                        rpmNodeCreateRequest(context, mutableMapOf())
                    }
                }
            } else { rpmNodeCreateRequest(context, mutableMapOf()) }

            storageService.store(nodeCreateRequest.sha256!!, context.getArtifactFile(), context.storageCredentials)
            with(context.artifactInfo) { logger.info("Success to store $projectId/$repoName/$artifactUri") }
            nodeClient.create(nodeCreateRequest)
            logger.info("Success to insert $nodeCreateRequest")
        }
        successUpload(context, mark, rpmRepoConf.repodataDepth)
    }

    @Transactional(rollbackFor = [Throwable::class])
    override fun remove(context: ArtifactRemoveContext) {
        with(context.artifactInfo) {
            val node = nodeClient.detail(projectId, repoName, artifactUri).data
            if (node == null) {
                deleteFailed(context, "未找到该构件或已经被删除")
                return
            }
            if (node.folder) {
                throw UnsupportedMethodException("Delete folder is forbidden")
            }
            val nodeMetadata = node.metadata
            val rpmVersion = RpmVersion(
                nodeMetadata["name"] ?: throw RpmArtifactMetadataResolveException(
                    "$artifactUri: not found " +
                        "metadata.name value"
                ),
                nodeMetadata["arch"] ?: throw RpmArtifactMetadataResolveException(
                    "$artifactUri: not found " +
                        "metadata.arch value"
                ),
                nodeMetadata["epoch"] ?: throw RpmArtifactMetadataResolveException(
                    "$artifactUri: not found " +
                        "metadata.epoch value"
                ),
                nodeMetadata["ver"] ?: throw RpmArtifactMetadataResolveException(
                    "$artifactUri: not found " +
                        "metadata.ver value"
                ),
                nodeMetadata["rel"] ?: throw RpmArtifactMetadataResolveException(
                    "$artifactUri: not found " +
                        "metadata.rel value"
                )
            )
            val artifactUri = context.artifactInfo.artifactUri
            // 定位对应请求的索引目录
            val rpmRepoConf = getRpmRepoConf(context)
            val repodataDepth = rpmRepoConf.repodataDepth
            val repodataUri = XmlStrUtils.splitUriByDepth(context.artifactInfo.artifactUri, repodataDepth)
            val repodataPath = repodataUri.repodataPath

            // 更新 primary, others
            deleteIndexXml(context, rpmVersion, repodataPath, PRIMARY)
            deleteIndexXml(context, rpmVersion, repodataPath, OTHERS)
            if (rpmRepoConf.enabledFileLists) {
                deleteIndexXml(context, rpmVersion, repodataPath, FILELISTS)
            }
            val nodeDeleteRequest = NodeDeleteRequest(projectId, repoName, artifactUri, context.userId)
            nodeClient.delete(nodeDeleteRequest)
            logger.info("Success to delete node $nodeDeleteRequest")
            flushRepoMdXML(context, null)
        }
    }

    /**
     * 刷新仓库下所有repodata
     */
    fun flushAllRepoData(context: ArtifactTransferContext) {
        // 查询仓库索引层级
        val rpmRepoConf = getRpmRepoConf(context)
        val targetSet = mutableSetOf<String>()
        listAllRepoDataFolder(context, "/", rpmRepoConf.repodataDepth, targetSet)
        for (repoDataPath in targetSet) {
            flushRepoMdXML(context, repoDataPath)
        }
    }

    private fun listAllRepoDataFolder(
        context: ArtifactTransferContext,
        fullPath: String,
        repodataDepth: Int,
        repoDataSet: MutableSet<String>
    ) {
        with(context.artifactInfo) {
            val nodeList = nodeClient.list(projectId, repoName, fullPath).data ?: return
            if (repodataDepth == 0) {
                for (node in nodeList.filter { it.folder }.filter { it.name == REPODATA }) {
                    repoDataSet.add(node.fullPath)
                }
            } else {
                for (node in nodeList.filter { it.folder }) {
                    listAllRepoDataFolder(context, node.fullPath, repodataDepth.dec(), repoDataSet)
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RpmLocalRepository::class.java)
    }
}
