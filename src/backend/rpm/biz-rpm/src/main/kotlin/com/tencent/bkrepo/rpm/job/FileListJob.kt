package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.common.api.util.HumanReadable
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
import com.tencent.bkrepo.rpm.artifact.SurplusNodeCleaner
import com.tencent.bkrepo.rpm.exception.RpmVersionNotFoundException
import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.util.GZipUtils.gZip
import com.tencent.bkrepo.rpm.util.GZipUtils.unGzipInputStream
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toRpmVersion
import com.tencent.bkrepo.rpm.util.XmlStrUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch
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

    @Autowired
    private lateinit var jobService: JobService

    @Scheduled(cron = "0 0/2 * * * ?")
    @SchedulerLock(name = "FileListJob", lockAtMostFor = "PT60M")
    fun insertFileList() {
        logger.info("rpmInsertFileList start")
        val startMillis = System.currentTimeMillis()
        val repoList = repositoryClient.pageByType(0, 100, "RPM").data?.records

        repoList?.let {
            for (repo in repoList) {
                logger.info("updateRpmFileLists(${repo.projectId}|${repo.name}) start")
                val rpmConfiguration = repo.configuration as RpmLocalConfiguration
                val repodataDepth = rpmConfiguration.repodataDepth ?: 0
                val targetSet = mutableSetOf<String>()
                jobService.findRepoDataByRepo(repo, "/", repodataDepth, targetSet)
                for (repoDataPath in targetSet) {
                    logger.info("updateRpmFileLists(${repo.projectId}|${repo.name}|$repoDataPath) start")
                    insertFileListsXml(repo, repoDataPath)
                    logger.info("updateRpmFileLists(${repo.projectId}|${repo.name}|$repoDataPath) done")
                }
                logger.info("updateRpmFileLists(${repo.projectId}|${repo.name}) done")
            }
        }
        logger.info("rpmInsertFileList done, cost time: ${System.currentTimeMillis() - startMillis} ms")
    }

    private fun insertFileListsXml(
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
                    projectId, name, 0, BATCH_SIZE,
                    "$repodataPath/temp/",
                    includeFolder = false,
                    includeMetadata = true
                ).data ?: return
                if (page.records.isEmpty()) {
                    logger.info("no temp file to process")
                    return
                }
                logger.info("${page.records.size} temp file to process")
                val oldFileListsFile = storageService.load(
                    latestNode.sha256!!,
                    Range.full(latestNode.size),
                    null
                ) ?: return
                val stopWatch = StopWatch()
                stopWatch.start("unzipOriginFileListsFile")
                var newFileListsFile: File = oldFileListsFile.use { it.unGzipInputStream() }
                stopWatch.stop()
                logger.info("originFileSize: ${HumanReadable.size(latestNode.size)}|${HumanReadable.size(newFileListsFile.length())}")
                try {
                    val tempFileListsNode = page.records.sortedBy { it.lastModifiedDate }
                    val calculatedList = mutableListOf<NodeInfo>()
                    // 循环写入
                    stopWatch.start("updateFiles")
                    for (tempFile in tempFileListsNode) {
                        val inputStream = storageService.load(
                            tempFile.sha256!!,
                            Range.full(tempFile.size),
                            null
                        ) ?: continue
                        try {
                            newFileListsFile = if ((tempFile.metadata?.get("repeat")) == "FULLPATH") {
                                logger.debug("update ${tempFile.fullPath}")
                                XmlStrUtils.updateFileLists(
                                    newFileListsFile,
                                    tempFile.fullPath,
                                    inputStream,
                                    tempFile.metadata!!
                                )
                            } else if ((tempFile.metadata?.get("repeat")) == "DELETE") {
                                logger.debug("delete ${tempFile.fullPath}")
                                try {
                                    XmlStrUtils.deletePackage(
                                        IndexType.FILELISTS,
                                        newFileListsFile,
                                        tempFile.metadata!!.toRpmVersion(tempFile.fullPath),
                                        tempFile.fullPath
                                    )
                                } catch (rpmVersionNotFound: RpmVersionNotFoundException) {
                                    logger.info("${tempFile.fullPath} 的filelists 未被更新")
                                    newFileListsFile
                                }
                            } else {
                                logger.debug("insert ${tempFile.fullPath}")
                                XmlStrUtils.insertFileLists(
                                    IndexType.FILELISTS, newFileListsFile,
                                    inputStream,
                                    false
                                )
                            }
                            calculatedList.add(tempFile)
                        } finally {
                            inputStream.closeQuietly()
                            oldFileListsFile.closeQuietly()
                        }
                    }
                    stopWatch.stop()
                    stopWatch.start("storeFile")
                    jobService.storeXmlGZNode(repo, newFileListsFile, repodataPath, IndexType.FILELISTS)
                    stopWatch.stop()
                    stopWatch.start("deleteTempXml")
                    surplusNodeCleaner.deleteTempXml(calculatedList)
                    stopWatch.stop()
                    if (logger.isDebugEnabled) {
                        logger.debug("updateFileLists stat: $stopWatch")
                    }
                } finally {
                    newFileListsFile.delete()
                }
            } else {
                // first upload
                storeFileListXmlNode(repo, repodataPath)
                insertFileListsXml(repo, repodataPath)
            }
            jobService.flushRepoMdXML(projectId, name, repodataPath)
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
                jobService.store(xmlGZNode, xmlGZArtifact)
            } finally {
                xmlGZFile.delete()
            }
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FileListJob::class.java)
        private const val BATCH_SIZE = 100
    }
}
