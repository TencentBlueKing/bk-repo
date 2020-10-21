package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.rpm.pojo.ArtifactRepeat
import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.pojo.RpmVersion
import com.tencent.bkrepo.rpm.util.FileInputStreamUtils.rpmIndex
import com.tencent.bkrepo.rpm.util.GZipUtils.unGzipInputStream
import com.tencent.bkrepo.rpm.util.RpmCollectionUtils.rpmFileListsFilter
import com.tencent.bkrepo.rpm.util.RpmVersionUtils.toRpmVersion
import com.tencent.bkrepo.rpm.util.XmlStrUtils
import com.tencent.bkrepo.rpm.util.rpm.RpmFormatUtils
import com.tencent.bkrepo.rpm.util.rpm.RpmMetadataUtils
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmMetadataChangeLog
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmPackageChangeLog
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.channels.Channels

@Component
class OthersJob {

    @Autowired
    private lateinit var repositoryClient: RepositoryClient

    @Autowired
    private lateinit var nodeClient: NodeClient

    @Autowired
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var jobService: JobService

    @Scheduled(fixedDelay = 1000)
    @SchedulerLock(name = "OthersJob", lockAtMostFor = "PT10M")
    fun checkPrimaryXml() {
        val startMillis = System.currentTimeMillis()
        val repoList = repositoryClient.pageByType(0, 100, "RPM").data?.records

        repoList?.let {
            for (repo in repoList) {
                logger.info("sync other (${repo.projectId}|${repo.name}) start")
                val rpmConfiguration = repo.configuration as RpmLocalConfiguration
                val repodataDepth = rpmConfiguration.repodataDepth ?: 0
                val targetSet = mutableSetOf<String>()
                jobService.findRepoDataByRepo(repo, "/", repodataDepth, targetSet)
                for (repoDataPath in targetSet) {
                    logger.info("sync other (${repo.projectId}|${repo.name}|$repoDataPath) start")
                    syncIndex(repo, repoDataPath, IndexType.OTHERS)
                    logger.info("sync other (${repo.projectId}|${repo.name}|$repoDataPath) done")
                }
                logger.info("sync other (${repo.projectId}|${repo.name}) done")
            }
        }
        logger.info("repositoryUtils done, cost time: ${System.currentTimeMillis() - startMillis} ms")
    }

    private fun syncIndex(
        repo: RepositoryInfo,
        repodataPath: String,
        indexType: IndexType
    ) {
        val indexMarkFolder = "$repodataPath/${indexType.value}/"
        // 查询repodata目录primary 的标记节点
        val nodeList = nodeClient.page(
            projectId = repo.projectId,
            repoName = repo.name,
            page = 0,
            size = 1,
            path = indexMarkFolder,
            includeFolder = false,
            includeMetadata = true,
            deep = true
        ).data?.records ?: return
        // 加载进primary索引
        val latestIndexNode = jobService.getLatestIndexNode(repo, repodataPath, IndexType.OTHERS) ?: return

        storageService.load(
            latestIndexNode.sha256!!,
            Range.full(latestIndexNode.size),
            null
        )?.use {
            for (node in nodeList) {
                val locationStr = node.fullPath.replace("/repodata/${indexType.value}", "")
                val tempFile = it.unGzipInputStream()
                try {
                    val rpmVersion = node.metadata!!.toRpmVersion(node.fullPath)
                    val indexStr = XmlStrUtils.getLocationStr(indexType, rpmVersion, locationStr)
                    val mark = tempFile.rpmIndex(indexStr)
                    if (mark >= 0) {
                        // 确认存在索引后，删除标记节点
                        with(node) {
                            nodeClient.delete(NodeDeleteRequest(projectId, repoName, fullPath, "PrimaryJob"))
                            logger.info("$projectId | $repoName | $fullPath has been delete")
                            return
                        }
                    } else {
                        with(node) { logger.info("$projectId | $repoName | $fullPath will index again") }
                        val repeatStr = node.metadata?.get("repeat") ?: "NONE"
                        val repeat = ArtifactRepeat.valueOf(repeatStr)
                        val rpmNode = nodeClient.detail(node.projectId, node.repoName, locationStr).data ?: return
                        val metadata = rpmNode.metadata
                        val rpmMetadataChangeLog = getRpmMetadataChangeLog(repodataPath, rpmNode)
                        if (rpmMetadataChangeLog != null) {
                            updateIndex(
                                rpmMetadataChangeLog, repeat, repo, repodataPath,
                                metadata.toRpmVersion(rpmNode.fullPath),
                                null
                            )
                        }
                    }
                } finally {
                    tempFile.delete()
                }
            }
        }
    }

    fun getRpmMetadataChangeLog(
        repodataPath: String,
        node: NodeDetail
    ): RpmMetadataChangeLog? {
        // 加载rpm包
        with(node) { logger.info("load $projectId | $repoName | $fullPath index message") }
        storageService.load(
            node.sha256!!,
            Range.full(node.size),
            null
        )?.use { rpmArtifactStream ->
            val rpmFormat = RpmFormatUtils.getRpmFormat(Channels.newChannel(rpmArtifactStream))
            val rpmMetadata = RpmMetadataUtils().interpret(
                rpmFormat,
                node.size,
                rpmArtifactStream.sha1(),
                node.fullPath.removePrefix(repodataPath.removeSuffix("/repodata"))
            )
            rpmArtifactStream.closeQuietly()
            rpmMetadata.rpmFileListsFilter()
            return RpmMetadataChangeLog(
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
        return null
    }

    private fun updateIndex(
        rpmMetadataChangeLog: RpmMetadataChangeLog,
        repeat: ArtifactRepeat,
        repo: RepositoryInfo,
        repodataPath: String,
        rpmVersion: RpmVersion,
        locationStr: String?
    ) {
        // 重新读取最新"-primary.xml.gz" 节点
        val latestPrimaryNode = jobService.getLatestIndexNode(repo, repodataPath, IndexType.OTHERS) ?: return

        val xmlFile = storageService.load(
            latestPrimaryNode.sha256!!,
            Range.full(latestPrimaryNode.size),
            null
        )?.use { rpmArtifactStream ->
            when (repeat) {
                ArtifactRepeat.NONE -> {
                    XmlStrUtils.insertPackage(IndexType.OTHERS, rpmArtifactStream.unGzipInputStream(), rpmMetadataChangeLog, false)
                }
                ArtifactRepeat.DELETE -> {
                    XmlStrUtils.deletePackage(IndexType.OTHERS, rpmArtifactStream.unGzipInputStream(), rpmVersion, locationStr)
                }
                ArtifactRepeat.FULLPATH -> {
                    XmlStrUtils.updatePackage(IndexType.OTHERS, rpmArtifactStream.unGzipInputStream(), rpmMetadataChangeLog)
                }
                ArtifactRepeat.FULLPATH_SHA256 -> {
                    return
                }
            }
        }
        try {
            xmlFile?.let { jobService.storeXmlGZNode(repo, it, repodataPath, IndexType.PRIMARY) }
        } finally {
            xmlFile?.delete()
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(OthersJob::class.java)
    }
}
