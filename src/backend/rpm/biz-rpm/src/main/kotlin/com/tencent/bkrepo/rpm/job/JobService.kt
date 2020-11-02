package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.rpm.REPODATA
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat
import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.pojo.RpmVersion
import com.tencent.bkrepo.rpm.util.GZipUtils.gZip
import com.tencent.bkrepo.rpm.util.GZipUtils.unGzipInputStream
import com.tencent.bkrepo.rpm.util.RpmCollectionUtils.filterRpmCustom
import com.tencent.bkrepo.rpm.util.RpmCollectionUtils.filterRpmFileLists
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toRpmVersion
import com.tencent.bkrepo.rpm.util.XmlStrUtils
import com.tencent.bkrepo.rpm.util.rpm.RpmFormatUtils
import com.tencent.bkrepo.rpm.util.rpm.RpmMetadataUtils
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil.toXml
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmChecksum
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmLocation
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadataChangeLog
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadataFileList
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmPackageChangeLog
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmPackageFileList
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmXmlMetadata
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoData
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoGroup
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoIndex
import com.tencent.bkrepo.rpm.util.xStream.repomd.Repomd
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.Channels

@Component
class JobService {

    @Autowired
    private lateinit var nodeClient: NodeClient

    @Autowired
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
    private lateinit var storageService: StorageService

    /**
     * 仓库下所有repodata目录
     * [repoDataSet] 仓库下repodata目录集合
     */
    fun findRepoDataByRepo(
        repositoryInfo: RepositoryInfo,
        path: String,
        repodataDepth: Int,
        repoDataSet: MutableSet<String>
    ) {
        with(repositoryInfo) {
            val nodeList = nodeClient.list(projectId, name, path).data ?: return
            if (repodataDepth == 0) {
                for (node in nodeList.filter { it.folder }.filter { it.name == REPODATA }) {
                    repoDataSet.add(node.fullPath)
                }
            } else {
                for (node in nodeList.filter { it.folder }) {
                    findRepoDataByRepo(repositoryInfo, node.fullPath, repodataDepth.dec(), repoDataSet)
                }
            }
        }
    }

    fun flushRepoMdXML(project: String, repoName: String, repoDataPath: String) {
        logger.info("flushRepoMdXML, [$project|$repoName|$repoDataPath]")
        val repositoryInfo = repositoryClient.detail(project, repoName).data ?: return
        val rpmRepoConf = repositoryInfo.configuration as RpmLocalConfiguration
        val enabledFileLists = rpmRepoConf.enabledFileLists ?: false
        val groupXmlSet = rpmRepoConf.groupXmlSet ?: mutableSetOf()

        // 查询该请求路径对应的索引目录下所有文件
        val page = nodeClient.page(project, repoName, 1, 1000, repoDataPath, includeMetadata = true)
        val nodeList = (page.data ?: return).records.sortedByDescending { it.lastModifiedDate }
        logger.debug("index file count: ${nodeList.size}")
        val targetIndexList = nodeList.filterRpmCustom(groupXmlSet, enabledFileLists)
        val repoDataList = mutableListOf<RepoIndex>()
        for (index in targetIndexList) {
            repoDataList.add(
                if ((index.name).contains(Regex("-filelists.xml.gz|-others|-primary"))) {
                    RepoData(
                        type = index.metadata?.get("indexType") as String,
                        location = RpmLocation("$REPODATA${StringPool.SLASH}${index.name}"),
                        checksum = RpmChecksum(index.metadata?.get("checksum") as String),
                        size = (index.metadata?.get("size") as String).toLong(),
                        timestamp = index.metadata?.get("timestamp") as String,
                        openChecksum = RpmChecksum(index.metadata?.get("openChecksum") as String),
                        openSize = (index.metadata?.get("openSize") as String).toLong()
                    )
                } else {
                    RepoGroup(
                        type = index.metadata?.get("indexType") as String,
                        location = RpmLocation("$REPODATA${StringPool.SLASH}${index.name}"),
                        checksum = RpmChecksum(index.metadata?.get("checksum") as String),
                        size = (index.metadata?.get("size") as String).toLong(),
                        timestamp = index.metadata?.get("timestamp") as String
                    )
                }
            )
        }

        val repomd = Repomd(repoDataList)
        val xmlRepodataString = repomd.toXml(true)
        ByteArrayInputStream((xmlRepodataString.toByteArray())).use { xmlRepodataInputStream ->
            val xmlRepodataArtifact = ArtifactFileFactory.build(xmlRepodataInputStream)
            // 保存repodata 节点
            val xmlRepomdNode = NodeCreateRequest(
                project,
                repoName,
                "$repoDataPath/repomd.xml",
                false,
                0L,
                true,
                xmlRepodataArtifact.getSize(),
                xmlRepodataArtifact.getFileSha256(),
                xmlRepodataArtifact.getFileMd5()
            )
            storageService.store(xmlRepomdNode.sha256!!, xmlRepodataArtifact, null)
            with(xmlRepomdNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
            nodeClient.create(xmlRepomdNode)
            logger.info("Success to insert $xmlRepomdNode")
            xmlRepodataArtifact.delete()
        }
    }

    /**
     * 初始化索引文件
     */
    fun initIndex(repo: RepositoryInfo, repodataPath: String, indexType: IndexType) {
        val initStr = when (indexType) {
            IndexType.PRIMARY -> {
                """<?xml version="1.0" encoding="UTF-8" ?>
<metadata xmlns="http://linux.duke.edu/metadata/common" xmlns:rpm="http://linux.duke.edu/metadata/rpm" packages="0">
</metadata>"""
            }
            IndexType.FILELISTS -> {
                """<?xml version="1.0" encoding="UTF-8" ?>
<metadata xmlns="http://linux.duke.edu/metadata/filelists" packages="0">
</metadata>"""
            }
            IndexType.OTHERS -> {
                """<?xml version="1.0" encoding="UTF-8" ?>
<metadata xmlns="http://linux.duke.edu/metadata/other" packages="0">
</metadata>"""
            }
        }
        val tempFile = createTempFile("rpmIndex_", "_temp.xml")
        try {
            tempFile.outputStream().write(initStr.toByteArray())
            storeXmlGZNode(repo, tempFile, repodataPath, indexType)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * 保存索引节点
     */
    fun storeXmlGZNode(
        repo: RepositoryInfo,
        xmlFile: File,
        repodataPath: String,
        indexType: IndexType
    ) {
        val target = "-${indexType.value}.xml.gz"
        // 保存节点同时保存节点信息到元数据方便repomd更新。
        val xmlInputStream = FileInputStream(xmlFile)
        val xmlFileSize = xmlFile.length()

        val xmlGZFile = xmlInputStream.gZip(indexType)
        val xmlFileSha1 = xmlInputStream.sha1()
        try {
            val xmlGZFileSha1 = FileInputStream(xmlGZFile).sha1()

            val xmlGZArtifact = ArtifactFileFactory.build(FileInputStream(xmlGZFile))
            val fullPath = "$repodataPath/$xmlGZFileSha1$target"
            val metadata = mutableMapOf(
                "indexType" to indexType.value,
                "checksum" to xmlGZFileSha1,
                "size" to (xmlGZArtifact.getSize().toString()),
                "timestamp" to System.currentTimeMillis().toString(),
                "openChecksum" to xmlFileSha1,
                "openSize" to (xmlFileSize.toString())
            )

            val xmlPrimaryNode = NodeCreateRequest(
                repo.projectId,
                repo.name,
                fullPath,
                false,
                0L,
                true,
                xmlGZArtifact.getSize(),
                xmlGZArtifact.getFileSha256(),
                xmlGZArtifact.getFileMd5(),
                metadata
            )
            store(xmlPrimaryNode, xmlGZArtifact)
            GlobalScope.launch {
                val indexTypeList = getIndexTypeList(repo, repodataPath, indexType)
                deleteSurplusNode(indexTypeList)
            }.start()
        } finally {
            xmlGZFile.delete()
            xmlInputStream.closeQuietly()
        }
    }

    fun deleteSurplusNode(list: List<NodeInfo>) {
        if (list.size > 2) {
            val surplusNodes = list.subList(3, list.size)
            for (node in surplusNodes) {
                nodeClient.delete(NodeDeleteRequest(node.projectId, node.repoName, node.fullPath, node.createdBy))
                logger.info("Success to delete ${node.projectId}/${node.repoName}/${node.fullPath}")
            }
        }
    }

    fun getIndexTypeList(repo: RepositoryInfo, repodataPath: String, indexType: IndexType): List<NodeInfo> {
        val target = "-${indexType.value}.xml.gz"
        val indexList = nodeClient.list(repo.projectId, repo.name, repodataPath).data ?: return mutableListOf()
        return indexList.filter { it.name.endsWith(target) }.sortedByDescending { it.lastModifiedDate }
    }

    fun store(node: NodeCreateRequest, artifactFile: ArtifactFile) {
        storageService.store(node.sha256!!, artifactFile, null)
        artifactFile.delete()
        with(node) { logger.info("Success to store $projectId/$repoName/$fullPath") }
        nodeClient.create(node)
        logger.info("Success to insert $node")
    }

    fun getLatestIndexNode(repo: RepositoryInfo, repodataPath: String, indexType: IndexType): NodeInfo {
        logger.debug("getLatestIndexNode: [${repo.projectId}|${repo.name}|$repodataPath|$indexType]")
        var ruleList = mutableListOf<Rule>(
            Rule.QueryRule("projectId", repo.projectId, OperationType.EQ),
            Rule.QueryRule("repoName", repo.name, OperationType.EQ),
            Rule.QueryRule("path", "${repodataPath.removeSuffix("/")}/", OperationType.EQ),
            Rule.QueryRule("folder", false, OperationType.EQ),
            Rule.QueryRule("name", "*-${indexType.value}.xml.gz", OperationType.MATCH)
        )
        val queryModel = QueryModel(
            page = PageLimit(1, 1),
            sort = Sort(listOf("lastModifiedDate"), Sort.Direction.DESC),
            select = mutableListOf(),
            rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)
        )
        if (logger.isDebugEnabled) {
            logger.debug("queryModel: $queryModel")
        }
        var nodeList = nodeClient.query(queryModel).data!!.records.map { resolveNode(it) }
        if (nodeList.isEmpty()) {
            initIndex(repo, repodataPath, indexType)
            nodeList = nodeClient.query(queryModel).data!!.records.map { resolveNode(it) }
        }
        if (nodeList.isEmpty()) {
            throw ArtifactNotFoundException("latest index node not found: [${repo.projectId}|${repo.name}|$repodataPath|$indexType]")
        }
        return nodeList.first()
    }

    /**
     * [randomAccessFile] 索引文件
     * [rpmMetadata] rpm包的数据，对索引做新增和更新时需要
     * [repeat] 标记对rpm包的动作，比如，新增，更新，删除
     * [repo] 仓库
     * [repodataPath] 仓库索引目录
     * [rpmVersion]
     * [locationStr] 节点路径
     * [indexType] 索引类型
     */
    fun updateIndex(
        randomAccessFile: RandomAccessFile,
        rpmMetadata: RpmXmlMetadata?,
        repeat: ArtifactRepeat,
        repo: RepositoryInfo,
        repodataPath: String,
        rpmVersion: RpmVersion,
        locationStr: String?,
        indexType: IndexType
    ): Int {
        logger.info("updateIndex: [${repo.projectId}|${repo.name}|$repodataPath|$repeat|$rpmVersion|$locationStr|$indexType]")
        when (repeat) {
            ArtifactRepeat.NONE -> {
                logger.info("insert index of [${repo.projectId}|${repo.name}|$repodataPath|${indexType.value}|$rpmVersion'")
                return XmlStrUtils.insertPackageIndex(randomAccessFile, indexType, rpmMetadata!!)
            }
            ArtifactRepeat.DELETE -> {
                logger.info("delete index of [${repo.projectId}|${repo.name}|$repodataPath|${indexType.value}|$rpmVersion'")
                return XmlStrUtils.deletePackageIndex(randomAccessFile, indexType, rpmVersion, locationStr)
            }
            ArtifactRepeat.FULLPATH -> {
                logger.info("delete index of [${repo.projectId}|${repo.name}|$repodataPath|${indexType.value}|$rpmVersion'")
                return XmlStrUtils.updatePackageIndex(randomAccessFile, indexType, rpmMetadata!!)
            }
            ArtifactRepeat.FULLPATH_SHA256 -> {
                logger.info("${repo.projectId}|${repo.name}|$repodataPath|${indexType.value}|$rpmVersion no change, skip")
                return 0
            }
        }
    }

    fun getRpmXmlMetadata(
        repodataPath: String,
        node: NodeDetail,
        indexType: IndexType
    ): RpmXmlMetadata? {
        with(node) { logger.info("load rpm[$projectId|$repoName|$fullPath] index info") }
        storageService.load(node.sha256!!, Range.full(node.size), null)?.use { rpmArtifactStream ->
            val tempRpm = rpmArtifactStream.use { createTempRpmFile(it) }
            try {
                val rpmFormat = FileInputStream(tempRpm).use {
                    RpmFormatUtils.getRpmFormat(Channels.newChannel(it))
                }
                val rpmMetadata = RpmMetadataUtils.interpret(
                    rpmFormat,
                    node.size,
                    tempRpm.sha1(),
                    node.fullPath.removePrefix(repodataPath.removeSuffix("repodata"))
                )
                return when (indexType) {
                    IndexType.PRIMARY -> {
                        rpmMetadata.filterRpmFileLists()
                        rpmMetadata.packages[0].format.changeLogs.clear()
                        rpmMetadata
                    }
                    IndexType.FILELISTS -> {
                        RpmMetadataFileList(
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
                    }
                    IndexType.OTHERS -> {
                        RpmMetadataChangeLog(
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
                    }
                }
            } finally {
                tempRpm.delete()
            }
        }
        return null
    }

    fun createTempRpmFile(rpmArtifactStream: ArtifactInputStream): File {
        val tempRpm = createTempFile("rpm", "temp")
        FileOutputStream(tempRpm).use { fos ->
            val buffer = ByteArray(1 * 1024 * 1024)
            var mark: Int
            while (rpmArtifactStream.read(buffer).also { mark = it } > 0) {
                fos.write(buffer, 0, mark)
            }
        }
        return tempRpm
    }

    private fun resolveNode(mapData: Map<String, Any>): NodeInfo {
        return NodeInfo(
            createdBy = mapData["createdBy"] as String,
            createdDate = mapData["lastModifiedBy"] as String,
            lastModifiedBy = mapData["lastModifiedDate"] as String,
            lastModifiedDate = mapData["lastModifiedDate"] as String,
            folder = mapData["folder"] as Boolean,
            path = mapData["path"] as String,
            name = mapData["name"] as String,
            fullPath = mapData["fullPath"] as String,
            size = mapData["size"].toString().toLong(),
            sha256 = mapData["sha256"] as String,
            md5 = mapData["md5"] as String,
            projectId = mapData["projectId"] as String,
            repoName = mapData["repoName"] as String,
            metadata = if (mapData["metadata"] == null) {
                mapOf()
            } else {
                (mapData["metadata"] as Map<String, Any>).mapValues { it.value.toString() }
            }
        )
    }

    fun listMarkNodes(
        repo: RepositoryInfo,
        repodataPath: String,
        indexType: IndexType,
        limit: Int
    ): Page<NodeInfo> {
        logger.debug("listMarkNodes: [$repo|$repodataPath|$indexType|$limit])")
        val indexMarkFolder = "$repodataPath/${indexType.value}/"
        var ruleList = mutableListOf<Rule>(
            Rule.QueryRule("projectId", repo.projectId, OperationType.EQ),
            Rule.QueryRule("repoName", repo.name, OperationType.EQ),
            Rule.QueryRule("path", indexMarkFolder, OperationType.EQ),
            Rule.QueryRule("folder", false, OperationType.EQ),
            Rule.QueryRule("name", "*.rpm", OperationType.MATCH)
        )
        val queryModel = QueryModel(
            page = PageLimit(1, limit),
            sort = Sort(listOf("lastModifiedDate"), Sort.Direction.ASC),
            select = mutableListOf(),
            rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)
        )
        if (logger.isDebugEnabled) {
            logger.debug("queryModel: $queryModel")
        }
        val resultPage = nodeClient.query(queryModel).data!!
        with(resultPage) { return Page(page, pageSize, count, records.map { resolveNode(it) }) }
    }

    /**
     * 更新索引
     */
    fun batchUpdateIndex(repo: RepositoryInfo, repodataPath: String, indexType: IndexType, maxCount: Int) {
        logger.info("batchUpdateIndex, [${repo.projectId}|${repo.name}|$repodataPath|$indexType]")
        val markNodePage = listMarkNodes(repo, repodataPath, indexType, maxCount)
        if (markNodePage.records.isEmpty()) {
            logger.info("no index file to process")
            return
        }
        logger.info("${markNodePage.records.size} of ${markNodePage.count} ${indexType.name} mark file to process")
        val markNodes = markNodePage.records
        val latestIndexNode = getLatestIndexNode(repo, repodataPath, indexType)
        logger.info("latestIndexNode, fullPath: ${latestIndexNode.fullPath}")
        val unzipedIndexTempFile = storageService.load(latestIndexNode.sha256!!, Range.full(latestIndexNode.size), null)!!.use { it.unGzipInputStream() }
        logger.info("temp index file ${unzipedIndexTempFile.absolutePath}(${HumanReadable.size(unzipedIndexTempFile.length())}) created")
        try {
            val processedMarkNodes = mutableListOf<NodeInfo>()
            var changeCount = 0
            RandomAccessFile(unzipedIndexTempFile, "rw").use { randomAccessFile ->
                markNodes.forEach { markNode ->
                    // rpm包相对标记文件的位置
                    val locationStr = markNode.fullPath.replace("/repodata/${indexType.value}", "")
                    val locationHref = locationStr.removePrefix(repodataPath.removeSuffix("repodata"))
                    logger.info("locationStr: $locationStr, locationHref: $locationHref")
                    with(markNode) { logger.info("process mark node[$projectId|$repoName|$fullPath]") }
                    val repeat = ArtifactRepeat.valueOf(markNode.metadata?.get("repeat") ?: "NONE")
                    if (repeat == ArtifactRepeat.DELETE) {
                        changeCount += updateIndex(randomAccessFile, null, repeat, repo, repodataPath, markNode.metadata!!.toRpmVersion(markNode.fullPath), locationHref, indexType)
                        processedMarkNodes.add(markNode)
                    } else {
                        val rpmNode = nodeClient.detail(markNode.projectId, markNode.repoName, locationStr).data
                        if (rpmNode == null) {
                            with(markNode) { logger.info("rpm node[$projectId|$repoName|$locationStr] no found, skip index") }
                            processedMarkNodes.add(markNode)
                            return@forEach
                        }
                        val metadata = rpmNode.metadata
                        val rpmMetadata = getRpmXmlMetadata(repodataPath, rpmNode, indexType)
                        changeCount += updateIndex(randomAccessFile, rpmMetadata, repeat, repo, repodataPath, metadata.toRpmVersion(rpmNode.fullPath), locationHref, indexType)
                        processedMarkNodes.add(markNode)
                    }
                }

                logger.debug("changeCount: $changeCount")
                if (changeCount != 0) {
                    val start = System.currentTimeMillis()
                    XmlStrUtils.updatePackageCount(randomAccessFile, indexType, changeCount, false)
                    logger.debug("updatePackageCount indexType: $indexType, indexFileSize: ${HumanReadable.size(randomAccessFile.length())}, cost: ${System.currentTimeMillis() - start} ms")
                }
            }


            storeXmlGZNode(repo, unzipedIndexTempFile, repodataPath, indexType)
            flushRepoMdXML(repo.projectId, repo.name, repodataPath)

            deleteNodes(processedMarkNodes)
        } finally {
            unzipedIndexTempFile.delete()
            logger.info("temp index file ${unzipedIndexTempFile.absolutePath} deleted")
        }
    }

    private fun deleteNodes(nodes: List<NodeInfo>) {
        nodes.forEach { nodeInfo ->
            with(nodeInfo) {
                try {
                    nodeClient.delete(NodeDeleteRequest(projectId, repoName, fullPath, "system"))
                    logger.info("node[$projectId|$repoName|$fullPath] deleted")
                } catch (e: Exception) {
                    logger.info("node[$projectId|$repoName|$fullPath] delete exception, ${e.message}")
                }
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JobService::class.java)
    }
}
