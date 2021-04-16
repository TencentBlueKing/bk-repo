package com.tencent.bkrepo.migrate.job

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.migrate.BKARTIFACT
import com.tencent.bkrepo.migrate.BKREPO
import com.tencent.bkrepo.migrate.SYNCREPO
import com.tencent.bkrepo.migrate.MIGRATE_OPERATOR
import com.tencent.bkrepo.migrate.conf.NexusConf
import com.tencent.bkrepo.migrate.pojo.MavenSyncInfo
import com.tencent.bkrepo.migrate.pojo.MavenArtifact
import com.tencent.bkrepo.migrate.pojo.NexusAssetPojo
import com.tencent.bkrepo.migrate.pojo.TempMavenInfo
import com.tencent.bkrepo.migrate.service.NexusService
import com.tencent.bkrepo.migrate.util.HttpDownUtils.downloadUrlHttpClient
import com.tencent.bkrepo.migrate.util.shell.ShellUtils
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.RuntimeException
import java.time.LocalDateTime
import java.util.Base64

@Component
class MvnJobService(
    private val nodeClient: NodeClient,
    private val storageManager: StorageManager,
    private val repositoryClient: RepositoryClient,
    private val nexusService: NexusService
) {

    @Autowired
    lateinit var nexusConf: NexusConf

    fun requireAuth(): Boolean {
        return nexusConf.auth
    }

    fun getAuth(): String? {
        if (!requireAuth()) return null
        val username = nexusConf.username
            ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "nexus.user must not be null")
        val password = nexusConf.password
            ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_MISSING, "nexus.password must not be null")
        return Base64.getMimeEncoder().encodeToString("$username:$password".toByteArray())
    }

    fun syncMavenArtifact(mavenSyncInfo: MavenSyncInfo) {
        val sw = StopWatch("Maven sync job")
        sw.start()
        // 获取待保存节点
        val mavenArtifactList = mavenSyncInfo.artifactList
        mavenArtifactList.add(
            MavenArtifact(
                groupId = mavenSyncInfo.groupId,
                artifactId = mavenSyncInfo.artifactId,
                type = mavenSyncInfo.packaging,
                version = mavenSyncInfo.version
            )
        )
        // 目标仓库
        val repoName = mavenSyncInfo.repositoryName
        checkTargetRepo(repoName)
        for (mavenArtifact in mavenArtifactList) {
            val version = mavenArtifact.version ?: mavenSyncInfo.version
            val assetList = nexusService.searchAssets(repoName, mavenArtifact, version) ?: continue
            if (assetList.isEmpty()) {
                logger.warn("$mavenArtifact can not found in nexus, skip")
                continue
            }
            val nexusAsset = assetList.first()
            if (checkNodeExist(nexusAsset)) {
                logger.info("$nexusAsset is exists skip")
                continue
            }
            // 保存文件到本地目录
            val tempFile = storeLocal(nexusAsset)
            Thread.sleep(100)
            try {
                storeBkNode(nexusAsset, tempFile, mavenSyncInfo, mavenArtifact)
                // 制品mvn deploy
                Thread.sleep(100)
                deploy(nexusAsset, tempFile, mavenArtifact, mavenSyncInfo.version)
            } catch (e: Exception) {
                throw e
            } finally {
                tempFile.delete()
                logger.info("Delete temp local file: ${tempFile.absolutePath}")
            }
        }
        sw.stop()
        logger.info("$sw")
    }

    /**
     * 执行mvn deploy 并检查是否上传成功,上传成功删除节点
     */
    private fun deploy(nexusAsset: NexusAssetPojo, file: File, mavenArtifact: MavenArtifact, requestVersion: String) {
        val version = mavenArtifact.version ?: requestVersion
        val mvnCliStr = nexusService.mvnCliStr(
            TempMavenInfo(
                extension = mavenArtifact.type,
                groupId = mavenArtifact.groupId,
                artifactId = mavenArtifact.artifactId,
                version = version,
                jarFile = file,
                repository = nexusAsset.repository
            )
        )
        val result = execShell(mvnCliStr, nexusAsset)
        logger.info("mvn deploy: ${nexusAsset.repository}/${nexusAsset.path} $result")
        if (result) {
            // 上传成功删除节点
            nodeClient.deleteNode(
                NodeDeleteRequest(
                    projectId = BKREPO,
                    repoName = SYNCREPO,
                    fullPath = "$BKARTIFACT/${nexusAsset.path.removePrefix("/")}",
                    operator = MIGRATE_OPERATOR
                )
            )
        }
    }

    private fun execShell(mvnCliStr: String, nexusAsset: NexusAssetPojo): Boolean {
        for (i in 1..4) {
            try {
                ShellUtils.runShell(mvnCliStr)
            } catch (e: Exception) {
                logger.error("Exec shell failed: $mvnCliStr", e)
            }
            if (checkNodeExist(nexusAsset)) return true
            logger.error("Deploy failed: $mvnCliStr , Will retry: $i")
            if (i == 4) return false
            Thread.sleep(i * 1000L)
        }
        return false
    }

    /**
     * 保存制品
     */
    fun storeBkNode(
        nexusAsset: NexusAssetPojo,
        file: File,
        mavenSyncInfo: MavenSyncInfo,
        mavenArtifact: MavenArtifact
    ) {
        val artifactFile = ArtifactFileFactory.build(file.inputStream())
        val md5 = artifactFile.getFileMd5()
        if (nexusAsset.checksum.md5 != md5) throw RuntimeException("File: ${nexusAsset.downloadUrl} download failed!")
        val metadata = mutableMapOf<String, String>()
        metadata["repositoryName"] = mavenSyncInfo.repositoryName
        metadata["groupId"] = mavenArtifact.groupId
        metadata["artifactId"] = mavenArtifact.artifactId
        metadata["version"] = mavenArtifact.version ?: mavenSyncInfo.version
        metadata["type"] = mavenArtifact.type
        val nodeCreateRequest = NodeCreateRequest(
            projectId = BKREPO,
            // todo check repoName isExists
            repoName = SYNCREPO,
            fullPath = "$BKARTIFACT/${nexusAsset.path.removePrefix("/")}",
            folder = false,
            expires = 0L,
            overwrite = true,
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            md5 = md5,
            metadata = metadata,
            operator = MIGRATE_OPERATOR,
            createdBy = MIGRATE_OPERATOR,
            createdDate = LocalDateTime.now(),
            lastModifiedDate = LocalDateTime.now(),
            lastModifiedBy = MIGRATE_OPERATOR
        )
        store(nodeCreateRequest, artifactFile)
    }

    @Throws(IOException::class)
    fun storeLocal(nexusAsset: NexusAssetPojo): File {
        val jarName = nexusAsset.path.split("/").last()
        val tempFilePath = nexusConf.localTemp.removeSuffix("/")
        val tempFile = File("$tempFilePath/$jarName")
        FileOutputStream(tempFile).use { tempFileOutputStream ->
            val byte = ByteArray(1024)
            var mark: Int
            // 下载jar包
            nexusAsset.downloadUrl.downloadUrlHttpClient(getAuth()).use { ins ->
                while (ins.read(byte).also { mark = it } > 0) {
                    tempFileOutputStream.write(byte, 0, mark)
                }
                tempFileOutputStream.close()
            }
        }
        logger.info("${nexusAsset.nick()} success store local: ${tempFile.absolutePath}")
        return tempFile
    }

    /**
     * 检查该制品在bkrepo中是否已存在
     */
    fun checkNodeExist(nexusAsset: NexusAssetPojo): Boolean {
        val nodeDetail = nodeClient.getNodeDetail(
            BKREPO, nexusAsset.repository,
            "/${nexusAsset.path.removePrefix("/")}"
        ).data
        return if (nodeDetail == null) {
            false
        } else {
            if (nodeDetail.md5 == nexusAsset.checksum.md5) {
                true
            } else {
                logger.error(
                    "Checksum failed: " +
                            "nodeDetail.md5--${nodeDetail.md5} ; nexusAsset.checksum.md5--${nexusAsset.checksum.md5}"
                )
                false
            }
        }
    }

    /**
     * 检查同步目标仓库是否存在，不存在则创建仓库
     */
    fun checkTargetRepo(repoName: String) {
        repositoryClient.getRepoInfo(BKREPO, repoName).data
            ?: repositoryClient.createRepo(
                RepoCreateRequest(
                    projectId = BKREPO,
                    name = repoName,
                    type = RepositoryType.MAVEN,
                    category = RepositoryCategory.COMPOSITE,
                    public = false,
                    description = "create by migrate server",
                    configuration = null,
                    storageCredentialsKey = null,
                    operator = MIGRATE_OPERATOR
                )
            )
    }

    private fun store(node: NodeCreateRequest, artifactFile: ArtifactFile) {
        storageManager.storeArtifactFile(node, artifactFile, null)
        artifactFile.delete()
        with(node) { logger.info("Success to store $projectId/$repoName$fullPath") }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(MvnJobService::class.java)
    }
}
