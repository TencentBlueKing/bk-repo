package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.repository.core.StorageManager
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.rpm.REPODATA
import com.tencent.bkrepo.rpm.artifact.SurplusNodeCleaner
import com.tencent.bkrepo.rpm.exception.RpmConfNotFoundException
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat
import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.pojo.RpmRepoConf
import com.tencent.bkrepo.rpm.pojo.RpmVersion
import com.tencent.bkrepo.rpm.util.GZipUtils.gZip
import com.tencent.bkrepo.rpm.util.GZipUtils.unGzipInputStream
import com.tencent.bkrepo.rpm.util.RpmCollectionUtils.filterRpmCustom
import com.tencent.bkrepo.rpm.util.RpmCollectionUtils.filterRpmFileLists
import com.tencent.bkrepo.rpm.util.RpmConfiguration.toRpmRepoConf
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toRpmVersion
import com.tencent.bkrepo.rpm.util.XmlStrUtils
import com.tencent.bkrepo.rpm.util.rpm.RpmFormatUtils
import com.tencent.bkrepo.rpm.util.rpm.RpmMetadataUtils
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil.objectToXml
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmLocation
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmChecksum
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmXmlMetadata
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadataChangeLog
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadataFileList
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmPackageChangeLog
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmPackageFileList
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
import java.nio.channels.Channels

@Component
class JobService {

    @Autowired
    private lateinit var nodeClient: NodeClient

    @Autowired
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var storageManager: StorageManager

    @Autowired
    private lateinit var surplusNodeCleaner: SurplusNodeCleaner

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
            val nodeList = nodeClient.listNode(projectId, name, path).data ?: return
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

    /**
     * 查询rpm仓库属性
     */
    private fun getRpmRepoConf(project: String, repoName: String): RpmRepoConf {
        val repositoryInfo = repositoryClient.getRepoInfo(project, repoName).data
            ?: throw RpmConfNotFoundException("can not found $project | $repoName conf")
        val rpmConfiguration = repositoryInfo.configuration
        return rpmConfiguration.toRpmRepoConf()
    }

    fun flushRepoMdXML(project: String, repoName: String, repoDataPath: String) {
        val rpmRepoConf = getRpmRepoConf(project, repoName)
        val enabledFileLists = rpmRepoConf.enabledFileLists
        val groupXmlSet = rpmRepoConf.groupXmlSet
        val limit = groupXmlSet.size * 3 + 9

        // 查询该请求路径对应的索引目录下所有文件
        val nodePage = nodeClient.page(
            project, repoName, 0, limit,
            repoDataPath,
            includeMetadata = true
        ).data ?: return
        val nodeList = nodePage.records.sortedByDescending { it.lastModifiedDate }

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

        val repomd = Repomd(
            repoDataList
        )
        val xmlRepodataString = repomd.objectToXml()
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
            store(xmlRepomdNode, xmlRepodataArtifact)
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
                surplusNodeCleaner.deleteSurplusNode(indexTypeList)
            }.start()
        } finally {
            xmlGZFile.delete()
            xmlInputStream.closeQuietly()
        }
    }

    fun getIndexTypeList(repo: RepositoryInfo, repodataPath: String, indexType: IndexType): List<NodeInfo> {
        val target = "-${indexType.value}.xml.gz"
        val indexList = nodeClient.listNode(repo.projectId, repo.name, repodataPath).data ?: return mutableListOf()
        return indexList.filter { it.name.endsWith(target) }.sortedByDescending { it.lastModifiedDate }
    }

    fun store(node: NodeCreateRequest, artifactFile: ArtifactFile) {
        storageManager.storeArtifactFile(node, artifactFile, null)
        artifactFile.delete()
        with(node) { logger.info("Success to store$projectId/$repoName/$fullPath") }
        logger.info("Success to insert $node")
    }

    fun getLatestIndexNode(repo: RepositoryInfo, repodataPath: String, indexType: IndexType): NodeInfo? {
        val target = "-${indexType.value}.xml.gz"
        val nodeList = nodeClient.listNode(
            repo.projectId, repo.name,
            repodataPath,
            includeFolder = false, deep = false
        ).data
        val nodeInfo = nodeList?.filter { it.name.endsWith(target) }?.maxBy { it.lastModifiedDate }
        return if (nodeInfo == null) {
            initIndex(repo, repodataPath, indexType)
            // todo 考虑服务异常等导致的死循环
            getLatestIndexNode(repo, repodataPath, indexType)
        } else {
            nodeInfo
        }
    }

    /**
     * 初始化索引
     */
    fun initIndex(repo: RepositoryInfo, repodataPath: String, indexType: IndexType) {
        val initStr = when (indexType) {
            IndexType.PRIMARY -> {
                "<?xml version=\"1.0\"?>\n" +
                    "<metadata xmlns=\"http://linux.duke.edu/metadata/common\" xmlns:rpm=\"http://linux.duke" +
                    ".edu/metadata/rpm\" packages=\"0\">\n" +
                    "</metadata>"
            }
            IndexType.FILELISTS -> {
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                    "<metadata xmlns=\"http://linux.duke.edu/metadata/filelists\" packages=\"0\">\n" +
                    "</metadata>"
            }
            IndexType.OTHERS -> {
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                    "<metadata xmlns=\"http://linux.duke.edu/metadata/other\" packages=\"0\">\n" +
                    "</metadata>"
            }
        }
        val initIndexGZFile = ByteArrayInputStream(initStr.toByteArray()).gZip(indexType)
        try {
            storeXmlGZNode(repo, initIndexGZFile, repodataPath, indexType)
        } finally {
            initIndexGZFile.delete()
        }
    }

    /**
     * [rpmMetadata] rpm包的数据，对索引做新增和更新时需要
     * [repeat] 标记对rpm包的动作，比如，新增，更新，删除
     * [repo] 仓库
     * [repodataPath] 仓库索引目录
     * [rpmVersion]
     * [locationStr] 节点路径
     * [indexType] 索引类型
     */
    fun updateIndex(
        rpmMetadata: RpmXmlMetadata?,
        repeat: ArtifactRepeat,
        repo: RepositoryInfo,
        repodataPath: String,
        rpmVersion: RpmVersion,
        locationStr: String?,
        indexType: IndexType
    ) {
        // 重新读取最新"-indexType.xml.gz" 节点
        val latestIndexNode = getLatestIndexNode(repo, repodataPath, indexType) ?: return

        val xmlFile = storageService.load(
            latestIndexNode.sha256!!,
            Range.full(latestIndexNode.size),
            null
        )?.use { rpmArtifactStream ->
            when (repeat) {
                ArtifactRepeat.NONE -> {
                    logger.info(
                        "${repo.projectId} | ${repo.name} | $repodataPath/${indexType.value} will insert " +
                            "$rpmVersion"
                    )
                    XmlStrUtils.insertPackage(indexType, rpmArtifactStream.unGzipInputStream(), rpmMetadata!!, false)
                }
                ArtifactRepeat.DELETE -> {
                    logger.info(
                        "${repo.projectId} | ${repo.name} | $repodataPath/${indexType.value} will  delete " +
                            "$rpmVersion"
                    )
                    XmlStrUtils.deletePackage(indexType, rpmArtifactStream.unGzipInputStream(), rpmVersion, locationStr)
                }
                ArtifactRepeat.FULLPATH -> {
                    logger.info(
                        "${repo.projectId} | ${repo.name} | $repodataPath/${indexType.value} will update " +
                            "$rpmVersion"
                    )
                    XmlStrUtils.updatePackage(indexType, rpmArtifactStream.unGzipInputStream(), rpmMetadata!!)
                }
                ArtifactRepeat.FULLPATH_SHA256 -> {
                    return
                }
            }
        }
        try {
            xmlFile?.let { storeXmlGZNode(repo, it, repodataPath, indexType) }
            flushRepoMdXML(repo.projectId, repo.name, repodataPath)
        } finally {
            xmlFile?.delete()
        }
    }

    fun getRpmXmlMetadata(
        repodataPath: String,
        node: NodeDetail,
        indexType: IndexType
    ): RpmXmlMetadata? {
        // 加载rpm包
        with(node) { logger.info("load $projectId | $repoName | $fullPath index message") }
        storageService.load(
            node.sha256!!,
            Range.full(node.size),
            null
        )?.use { rpmArtifactStream ->
            with(node) { logger.info("store $projectId | $repoName | $fullPath to local") }
            val tempRpm = rpmArtifactStream.use { createTempRpm(it) }
            try {
                with(node) { logger.info("store $projectId | $repoName | $fullPath success, start read index message") }
                val rpmFormat = FileInputStream(tempRpm).use {
                    RpmFormatUtils.getRpmFormat(Channels.newChannel(it))
                }
                val rpmMetadata = RpmMetadataUtils().interpret(
                    rpmFormat,
                    node.size,
                    tempRpm.sha1(),
                    node.fullPath.removePrefix(repodataPath.removeSuffix("repodata"))
                )
                with(node) {
                    logger.info(
                        "read $projectId | $repoName | $fullPath index message success, start filter " +
                            "indexType message"
                    )
                }
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

    fun createTempRpm(rpmArtifactStream: ArtifactInputStream): File {
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

    fun getMarkNode(
        repo: RepositoryInfo,
        repodataPath: String,
        indexType: IndexType
    ): NodeInfo? {
        val indexMarkFolder = "$repodataPath/${indexType.value}/"
        // 查询repodata/indexType 的标记节点 ,每次只处理一个节点
        val page = nodeClient.page(
            projectId = repo.projectId,
            repoName = repo.name,
            pageNumber = 0,
            pageSize = 1,
            path = indexMarkFolder,
            includeFolder = false,
            includeMetadata = true,
            deep = true
        ).data?.records
        return if (!page.isNullOrEmpty()) page.first() else null
    }

    /**
     * 同步索引
     */
    fun syncIndex(
        repo: RepositoryInfo,
        repodataPath: String,
        indexType: IndexType
    ) {
        val nodeInfo = getMarkNode(repo, repodataPath, indexType)
        nodeInfo?.let {
            with(nodeInfo) { logger.info("Found latest mark node $projectId | $repo | $fullPath") }
            val latestPrimaryNode = getLatestIndexNode(repo, repodataPath, indexType) ?: return
            storageService.load(
                latestPrimaryNode.sha256!!,
                Range.full(latestPrimaryNode.size),
                null
            )?.use {
                // rpm包相对标记文件的位置
                val locationStr = nodeInfo.fullPath.replace("/repodata/${indexType.value}", "")
                val tempFile = it.unGzipInputStream()
                try {
                    val locationHref = locationStr.removePrefix(repodataPath.removeSuffix("repodata"))
                    with(nodeInfo) { logger.info("$projectId | $repoName | $fullPath will index") }
                    val repeatStr = (nodeInfo.metadata?.get("repeat") ?: "NONE") as String
                    val repeat = ArtifactRepeat.valueOf(repeatStr)
                    // 删除索引不需要读取rpm包数据
                    if (repeat == ArtifactRepeat.DELETE) {
                        updateIndex(
                            null, repeat, repo, repodataPath,
                            nodeInfo.metadata!!.toRpmVersion(nodeInfo.fullPath),
                            locationHref,
                            indexType
                        )
                    } else {
                        val rpmNode = nodeClient.getNodeDetail(nodeInfo.projectId, nodeInfo.repoName, locationStr).data ?: return
                        with(rpmNode) { logger.info("Start read index message form $projectId | $repoName | $fullPath") }
                        val metadata = rpmNode.metadata
                        val rpmMetadata = getRpmXmlMetadata(repodataPath, rpmNode, indexType)
                        updateIndex(
                            rpmMetadata, repeat, repo, repodataPath,
                            metadata.toRpmVersion(rpmNode.fullPath),
                            locationHref,
                            indexType
                        )
                    }
                    with(nodeInfo) {
                        nodeClient.deleteNode(NodeDeleteRequest(projectId, repoName, fullPath, "jobService"))
                        logger.info("$projectId | $repoName | $fullPath has been delete")
                    }
                } finally {
                    tempFile.delete()
                }
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JobService::class.java)
    }
}
