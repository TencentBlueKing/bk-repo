package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.repository.RpmLocalConfiguration
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.rpm.REPODATA
import com.tencent.bkrepo.rpm.pojo.IndexType
import com.tencent.bkrepo.rpm.util.GZipUtils.gZip
import com.tencent.bkrepo.rpm.util.RpmCollectionUtils.filterRpmCustom
import com.tencent.bkrepo.rpm.util.xStream.XStreamUtil.objectToXml
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmChecksum
import com.tencent.bkrepo.rpm.util.xStream.pojo.RpmLocation
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoData
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoGroup
import com.tencent.bkrepo.rpm.util.xStream.repomd.RepoIndex
import com.tencent.bkrepo.rpm.util.xStream.repomd.Repomd
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

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
            storageService.store(xmlRepomdNode.sha256!!, xmlRepodataArtifact, null)
            with(xmlRepomdNode) { logger.info("Success to store $projectId/$repoName/$fullPath") }
            nodeClient.create(xmlRepomdNode)
            logger.info("Success to insert $xmlRepomdNode")
            xmlRepodataArtifact.delete()
        }
    }

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

        val xmlGZFile = xmlInputStream.gZip(indexType.value)
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
        } finally {
            xmlGZFile.delete()
            xmlInputStream.closeQuietly()
        }
    }

    fun store(node: NodeCreateRequest, artifactFile: ArtifactFile) {
        storageService.store(node.sha256!!, artifactFile, null)
        artifactFile.delete()
        with(node) { logger.info("Success to store$projectId/$repoName/$fullPath") }
        nodeClient.create(node)
        logger.info("Success to insert $node")
    }

    fun getLatestIndexNode(repo: RepositoryInfo, repodataPath: String, indexType: IndexType): NodeInfo? {
        val target = "-${indexType.value}.xml.gz"
        return nodeClient.list(
            repo.projectId, repo.name,
            repodataPath,
            includeFolder = false, deep = false
        ).data?.filter { it.name.endsWith(target) }?.sortedByDescending { it.lastModifiedDate }?.get(0)
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JobService::class.java)
    }
}
