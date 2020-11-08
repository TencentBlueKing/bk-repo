package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
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
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toRpmVersion
import com.tencent.bkrepo.rpm.util.XmlStrUtils
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmChecksum
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmLocation
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
import java.io.RandomAccessFile

@Component
class JobService {

    @Autowired
    private lateinit var nodeClient: NodeClient

    @Autowired
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
    private lateinit var storageService: StorageService

    /**
     * 查询仓库下所有repodata目录
     */
    fun findRepodataDirs(repo: RepositoryInfo): List<String> {
        val ruleList = mutableListOf<Rule>(
            Rule.QueryRule("projectId", repo.projectId, OperationType.EQ),
            Rule.QueryRule("repoName", repo.name, OperationType.EQ),
            Rule.QueryRule("folder", true, OperationType.EQ),
            Rule.QueryRule("name", "repodata", OperationType.EQ)
        )
        val queryModel = QueryModel(
            page = PageLimit(1, MAX_REPO_PAGE_SIE),
            sort = Sort(listOf("lastModifiedDate"), Sort.Direction.DESC),
            select = mutableListOf("fullPath"),
            rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)
        )
        if (logger.isDebugEnabled) {
            logger.debug("queryRepodata: $queryModel")
        }
        val page = nodeClient.query(queryModel).data!!
        return page.records.map { it["fullPath"] as String }
    }

    /**
     * 合并索引和group 字段
     */
    fun getTargetIndexList(groupXmlSet: MutableSet<String>, enabledFileLists: Boolean): MutableList<String> {
        val doubleSet = mutableSetOf<String>()
        for (str in groupXmlSet) {
            doubleSet.add(str)
            doubleSet.add("$str.gz")
        }
        val groupAndIndex = mutableListOf<String>()
        if (enabledFileLists) groupAndIndex.add("${IndexType.FILELISTS.value}.xml.gz")
        groupAndIndex.add("${IndexType.PRIMARY.value}.xml.gz")
        groupAndIndex.add("${IndexType.OTHERS.value}.xml.gz")
        groupAndIndex.addAll(doubleSet)
        return groupAndIndex
    }

    fun findIndexXml(repo: RepositoryInfo, repoDataPath: String): List<NodeInfo> {
        val repositoryInfo = repositoryClient.detail(repo.projectId, repo.name).data ?: return mutableListOf()
        val rpmRepoConf = repositoryInfo.configuration as RpmLocalConfiguration
        val enabledFileLists = rpmRepoConf.enabledFileLists ?: false
        val groupXmlSet = rpmRepoConf.groupXmlSet ?: mutableSetOf<String>()
        val groupAndIndex = getTargetIndexList(groupXmlSet, enabledFileLists)
        val targetIndexList = mutableListOf<NodeInfo>()

        for (index in groupAndIndex) {
            getLatestIndexNode(repo, repoDataPath, index)?.let { targetIndexList.add(it) }
        }
        return targetIndexList
    }

    fun flushRepoMdXML(repo: RepositoryInfo, repoDataPath: String) {
        val targetIndexList = findIndexXml(repo, repoDataPath)
        val repoDataList = mutableListOf<RepoIndex>()
        val regex = Regex("-filelists.xml.gz|-others|-primary")
        for (index in targetIndexList) {
            repoDataList.add(
                if ((index.name).contains(regex)) {
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
        ByteArrayInputStream((repomd.toXml().toByteArray())).use { xmlRepodataInputStream ->
            val xmlRepodataArtifact = ArtifactFileFactory.build(xmlRepodataInputStream)
            try {
                // 保存repodata 节点
                val xmlRepomdNode = NodeCreateRequest(
                    repo.projectId,
                    repo.name,
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
            } finally {
                xmlRepodataArtifact.delete()
            }
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
        val xmlGZFile = xmlFile.gZip()
        try {
            val xmlFileSha1 = xmlFile.sha1()
            val xmlGZFileSha1 = xmlGZFile.sha1()
            val xmlGZArtifact = ArtifactFileFactory.build(FileInputStream(xmlGZFile))
            val fullPath = "$repodataPath/$xmlGZFileSha1-${indexType.value}.xml.gz"
            // 保存节点同时保存节点信息到元数据方便repomd更新。
            val metadata = mutableMapOf(
                "indexType" to indexType.value,
                "checksum" to xmlGZFileSha1,
                "size" to (xmlGZArtifact.getSize().toString()),
                "timestamp" to System.currentTimeMillis().toString(),
                "openChecksum" to xmlFileSha1,
                "openSize" to (xmlFile.length().toString())
            )

            val xmlGZNode = NodeCreateRequest(
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
            store(xmlGZNode, xmlGZArtifact)
            GlobalScope.launch {
                val indexTypeList = getIndexTypeList(repo, repodataPath, indexType)
                deleteSurplusNode(indexTypeList)
            }.start()
        } finally {
            xmlGZFile.delete()
        }
    }

    fun deleteSurplusNode(list: List<NodeInfo>) {
        if (list.size > 2) {
            val surplusNodes = list.subList(2, list.size)
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

    /**
     * 获取最新的索引或分组文件。
     */
    fun getLatestIndexNode(repo: RepositoryInfo, repodataPath: String, nameSuffix: String): NodeInfo? {
        logger.debug("getLatestIndexNode: [${repo.projectId}|${repo.name}|$repodataPath|$nameSuffix]")
        val ruleList = mutableListOf<Rule>(
            Rule.QueryRule("projectId", repo.projectId),
            Rule.QueryRule("repoName", repo.name),
            Rule.QueryRule("path", "${repodataPath.removeSuffix("/")}/"),
            Rule.QueryRule("folder", false, OperationType.EQ),
            Rule.QueryRule("name", "*-$nameSuffix", OperationType.MATCH)
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
        val regex = Regex(
            "${IndexType.PRIMARY.value}.xml.gz" +
                "|${IndexType.OTHERS.value}.xml.gz" +
                "|${IndexType.FILELISTS.value}.xml.gz"
        )
        // 如果是索引文件则执行
        if (nameSuffix.matches(regex)) {
            val indexType = IndexType.valueOf(nameSuffix.removeSuffix(".xml.gz").toUpperCase())
            if (nodeList.isEmpty()) {
                initIndex(repo, repodataPath, indexType)
                nodeList = nodeClient.query(queryModel).data!!.records.map { resolveNode(it) }
            }
            if (nodeList.isEmpty()) {
                throw ArtifactNotFoundException("latest index node not found: [${repo.projectId}|${repo.name}|$repodataPath|$indexType]")
            }
        } else {
            if (nodeList.isEmpty()) {
                return null
            }
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
        markNodeInfo: NodeInfo,
        repeat: ArtifactRepeat,
        repo: RepositoryInfo,
        repodataPath: String,
        locationStr: String?,
        indexType: IndexType
    ): Int {
        logger.info("updateIndex: [${repo.projectId}|${repo.name}|$repodataPath|$repeat|$locationStr|$indexType]")
        when (repeat) {
            ArtifactRepeat.NONE -> {
                logger.info("insert index of [${repo.projectId}|${repo.name}|${markNodeInfo.fullPath}")
                return XmlStrUtils.insertPackageIndex(randomAccessFile, resolveIndexXml(markNodeInfo))
            }
            ArtifactRepeat.DELETE -> {
                logger.info("delete index of [${repo.projectId}|${repo.name}|${markNodeInfo.fullPath}]")
                val rpmVersion = markNodeInfo.metadata!!.toRpmVersion(markNodeInfo.fullPath)
                val locationStr = getLocationStr(indexType, rpmVersion, locationStr)
                return XmlStrUtils.deletePackageIndex(randomAccessFile, indexType, locationStr)
            }
            ArtifactRepeat.FULLPATH -> {
                logger.info("replace index of [${repo.projectId}|${repo.name}|${markNodeInfo.fullPath}]")
                val rpmVersion = markNodeInfo.metadata!!.toRpmVersion(markNodeInfo.fullPath)
                val locationStr = getLocationStr(indexType, rpmVersion, locationStr)
                return XmlStrUtils.updatePackageIndex(randomAccessFile, indexType, locationStr, resolveIndexXml(markNodeInfo))
            }
            ArtifactRepeat.FULLPATH_SHA256 -> {
                logger.info("skip index of [${repo.projectId}|${repo.name}|${markNodeInfo.fullPath}]")
                return 0
            }
        }
    }

    private fun getLocationStr(
        indexType: IndexType,
        rpmVersion: RpmVersion,
        location: String?
    ): String {
        return when (indexType) {
            IndexType.OTHERS, IndexType.FILELISTS -> {
                with(rpmVersion) {
                    """name="$name">
    <version epoch="$epoch" ver="$ver" rel="$rel"/>"""
                }
            }
            IndexType.PRIMARY -> {
                """<location href="$location"/>"""
            }
        }
    }

    private fun resolveIndexXml(indexNodeInfo: NodeInfo): ByteArray {
        // TODO 校验XML合法性
        storageService.load(indexNodeInfo.sha256!!, Range.full(indexNodeInfo.size), null).use { inputStream ->
            return inputStream!!.readBytes()
        }
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
        val ruleList = mutableListOf<Rule>(
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
        val latestIndexNode = getLatestIndexNode(repo, repodataPath, "${indexType.value}.xml.gz")!!
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
                    logger.debug("locationStr: $locationStr, locationHref: $locationHref")
                    with(markNode) { logger.info("process mark node[$projectId|$repoName|$fullPath]") }
                    val repeat = ArtifactRepeat.valueOf(markNode.metadata?.get("repeat") ?: "FULLPATH_SHA256")
                    if (repeat == ArtifactRepeat.DELETE) {
                        changeCount += updateIndex(randomAccessFile, markNode, repeat, repo, repodataPath, locationHref, indexType)
                        processedMarkNodes.add(markNode)
                    } else {
                        val rpmNode = nodeClient.detail(markNode.projectId, markNode.repoName, locationStr).data
                        if (rpmNode == null) {
                            with(markNode) { logger.info("rpm node[$projectId|$repoName|$locationStr] no found, skip index") }
                            processedMarkNodes.add(markNode)
                            return@forEach
                        }
                        changeCount += updateIndex(randomAccessFile, markNode, repeat, repo, repodataPath, locationHref, indexType)
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
            flushRepoMdXML(repo, repodataPath)
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
        private const val MAX_REPO_PAGE_SIE = 1000
        private val logger: Logger = LoggerFactory.getLogger(JobService::class.java)
    }
}
