package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.rpm.REPODATA
import com.tencent.bkrepo.rpm.artifact.SurplusNodeCleaner
import com.tencent.bkrepo.rpm.exception.RpmVersionNotFoundException
import com.tencent.bkrepo.rpm.util.GZipUtils.gZip
import com.tencent.bkrepo.rpm.util.GZipUtils.unGzipInputStream
import com.tencent.bkrepo.rpm.util.RpmCollectionUtils.filterRpmCustom
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toRpmVersion
import com.tencent.bkrepo.rpm.util.XmlStrUtils
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil.objectToXml
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmChecksum
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmLocation
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoData
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoGroup
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoIndex
import com.tencent.bkrepo.rpm.util.xStream.repomd.Repomd
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

@Component
class FileListJob {

    @Autowired
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
    private lateinit var nodeClient: NodeClient

    @Autowired
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var surplusNodeCleaner: SurplusNodeCleaner

    @Scheduled(cron = "0 0/20 * * * ?")
    @SchedulerLock(name = "FileListJob", lockAtMostFor = "PT15M")
    fun insertFileList() {
        logger.info("rpmInsertFileList start")
        val startMillis = System.currentTimeMillis()
        val repoList = repositoryClient.pageByType(0, 100, "RPM").data?.records

        repoList?.let {
            for (repo in repoList) {
                val rpmConfiguration = repo.configuration as RpmLocalConfiguration
                val repodataDepth = rpmConfiguration.repodataDepth ?: 0
                val targetSet = mutableSetOf<String>()
                findRepoDataByRepo(repo, "/", repodataDepth, targetSet)
                for (repoDataPath in targetSet) {
                    updateFileListsXml(repo, repoDataPath)
                }
            }
        }
        logger.info("rpmInsertFileList done, cost time: ${System.currentTimeMillis() - startMillis} ms")
    }

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

    private fun updateFileListsXml(
        repo: RepositoryInfo,
        repodataPath: String
    ) {
        val target = "-filelists.xml.gz"
        with(repo) {
            // repodata下'-filelists.xml.gz'最新节点。
            val nodeList = nodeClient.list(
                projectId, name,
                repodataPath,
                includeFolder = false, deep = false
            ).data
            val targetNodelist = nodeList?.filter {
                it.name.endsWith(target)
            }?.sortedByDescending {
                it.createdDate
            }

            if (!targetNodelist.isNullOrEmpty()) {
                val latestNode = targetNodelist[0]
                // 从临时目录中遍历索引
                val page = nodeClient.page(
                    projectId, name, 0, 50,
                    "$repodataPath/temp/",
                    includeFolder = false,
                    includeMetadata = true
                ).data ?: return

                val oldFileLists = storageService.load(
                    latestNode.sha256!!,
                    Range.full(latestNode.size),
                    null
                ) ?: return
                logger.info("加载最新的filelists节点：${latestNode.fullPath}, 压缩后大小：${latestNode.size}")
                var newFileLists: File = oldFileLists.use { it.unGzipInputStream() }
                try {
                    val tempFileListsNode = page.records.sortedBy { it.lastModifiedDate }
                    val calculatedList = mutableListOf<NodeInfo>()
                    // 循环写入
                    for (tempFile in tempFileListsNode) {
                        val inputStream = storageService.load(
                            tempFile.sha256!!,
                            Range.full(tempFile.size),
                            null
                        ) ?: return
                        try {
                            newFileLists = if ((tempFile.metadata?.get("repeat")) == "FULLPATH") {
                                logger.info("action:update ${tempFile.fullPath}, size:${tempFile.size}")
                                XmlStrUtils.updateFileLists(
                                    "filelists", newFileLists,
                                    tempFile.fullPath,
                                    inputStream,
                                    tempFile.metadata!!
                                )
                            } else if ((tempFile.metadata?.get("repeat")) == "DELETE") {
                                logger.info("action:delete ${tempFile.fullPath}, size:${tempFile.size}")
                                try {
                                    XmlStrUtils.deletePackage(
                                        "filelists",
                                        newFileLists,
                                        tempFile.metadata!!.toRpmVersion(tempFile.fullPath),
                                        tempFile.fullPath
                                    )
                                } catch (rpmVersionNotFound: RpmVersionNotFoundException) {
                                    logger.info("${tempFile.fullPath} 的filelists 未被更新")
                                    newFileLists
                                }
                            } else {
                                logger.info("action:insert ${tempFile.fullPath}, size:${tempFile.size}")
                                XmlStrUtils.insertFileLists(
                                    "filelists", newFileLists,
                                    inputStream,
                                    false
                                )
                            }
                            logger.info("临时filelists 文件：${newFileLists.absolutePath}，size:${newFileLists.length()}")
                            calculatedList.add(tempFile)
                        } finally {
                            inputStream.closeQuietly()
                            oldFileLists.closeQuietly()
                        }
                    }
                    storeFileListNode(repo, newFileLists, repodataPath)
                    surplusNodeCleaner.deleteTempXml(calculatedList)
                } finally {
                    newFileLists.delete()
                }
            } else {
                // first upload
                storeFileListXmlNode(repo, repodataPath)
                updateFileListsXml(repo, repodataPath)
            }
            flushRepoMdXML(projectId, name, repodataPath)
            // 删除多余索引节点
            GlobalScope.launch {
                targetNodelist?.let { surplusNodeCleaner.deleteSurplusNode(it) }
            }.start()
        }
    }

    private fun storeFileListXmlNode(
        repo: RepositoryInfo,
        repodataPath: String
    ) {
        val target = "-filelists.xml.gz"
        val xmlStr = "<?xml version=\"1.0\"?>\n" +
            "<metadata xmlns=\"http://linux.duke.edu/metadata/filelists\" packages=\"0\">\n" +
            "</metadata>"
        val indexType = "filelists"
        ByteArrayInputStream((xmlStr.toByteArray())).use { xmlInputStream ->
            // 保存节点同时保存节点信息到元数据方便repomd更新。
            val xmlFileSize = xmlStr.toByteArray().size

            val xmlGZFile = xmlStr.toByteArray().gZip(indexType)
            val xmlFileSha1 = xmlInputStream.sha1()
            try {
                val xmlGZFileSha1 = FileInputStream(xmlGZFile).sha1()

                val xmlGZArtifact = ArtifactFileFactory.build(FileInputStream(xmlGZFile))
                val fullPath = "$repodataPath/$xmlGZFileSha1$target"
                val metadata = mutableMapOf(
                    "indexType" to indexType,
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
                storageService.store(xmlPrimaryNode.sha256!!, xmlGZArtifact, null)
                with(xmlPrimaryNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
                nodeClient.create(xmlPrimaryNode)
                logger.info("Success to insert $xmlPrimaryNode")
            } finally {
                xmlGZFile.delete()
            }
        }
    }

    private fun storeFileListNode(
        repo: RepositoryInfo,
        xmlFile: File,
        repodataPath: String
    ) {
        val indexType = "filelists"
        val target = "-filelists.xml.gz"
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
                "indexType" to indexType,
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
            storageService.store(xmlPrimaryNode.sha256!!, xmlGZArtifact, null)
            with(xmlPrimaryNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
            nodeClient.create(xmlPrimaryNode)
            logger.info("Success to insert $xmlPrimaryNode")
            xmlGZArtifact.delete()
        } finally {
            xmlGZFile.delete()
            xmlInputStream.closeQuietly()
        }
    }

    private fun flushRepoMdXML(project: String, repoName: String, repoDataPath: String) {
        val repositoryInfo = repositoryClient.detail(project, repoName).data ?: return
        val rpmRepoConf = repositoryInfo.configuration as RpmLocalConfiguration
        val enabledFileLists = rpmRepoConf.enabledFileLists ?: true
        val groupXmlSet = rpmRepoConf.groupXmlSet ?: mutableSetOf()
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
                        openSize = (index.metadata?.get("openSize") as String).toInt()
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
            storageService.store(xmlRepomdNode.sha256!!, xmlRepodataArtifact, null)
            with(xmlRepomdNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
            nodeClient.create(xmlRepomdNode)
            logger.info("Success to insert $xmlRepomdNode")
            xmlRepodataArtifact.delete()
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FileListJob::class.java)
    }
}
