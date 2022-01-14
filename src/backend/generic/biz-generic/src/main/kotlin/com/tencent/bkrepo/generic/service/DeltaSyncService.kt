package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.file.chunk.ChunkedArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.chunk.ChunkedFileOutputStream
import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.common.bksync.BkSync
import com.tencent.bkrepo.common.bksync.ByteArrayBlockInputStream
import com.tencent.bkrepo.common.bksync.FileBlockInputStream
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.config.GenericProperties
import com.tencent.bkrepo.generic.dao.SignFileDao
import com.tencent.bkrepo.generic.model.TSignFile
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import kotlin.system.measureNanoTime

/**
 * 增量同步实现类
 *
 * */
@Service
class DeltaSyncService(
    genericProperties: GenericProperties,
    val storageManager: StorageManager,
    val nodeClient: NodeClient,
    val signFileDao: SignFileDao,
    val repositoryClient: RepositoryClient
) : ArtifactService() {

    private val deltaProperties = genericProperties.delta
    private val blockSize = deltaProperties.blockSize.toBytes().toInt()
    val signFileProjectId = deltaProperties.projectId!!
    val signFileRepoName = deltaProperties.repoName!!
    val signRepo: RepositoryDetail by lazy {
        repositoryClient.getRepoDetail(signFileProjectId, signFileRepoName).data
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, signFileRepoName)
    }

    /**
     * 签名文件
     * */
    fun sign() {
        with(ArtifactContext()) {
            // 查看是否已有sign文件，没有则生成。
            signFileDao.findByDetail(projectId, repoName, artifactInfo.getArtifactFullPath(), blockSize)?.let {
                val artifactInfo = GenericArtifactInfo(it.projectId, it.repoName, it.fullPath)
                val downloadContext = ArtifactDownloadContext(repo = signRepo, artifact = artifactInfo)
                repository.download(downloadContext)
                return
            }
            val node = nodeClient.getNodeDetail(projectId, repoName, artifactInfo.getArtifactFullPath()).data
            if (node == null || node.folder) {
                throw ArtifactNotFoundException(artifactInfo.toString())
            }
            val signFile = generateSignFile(node, storageCredentials)
            val artifactInfo = saveSignFile(node, signFile)
            val downloadContext = ArtifactDownloadContext(repo = signRepo, artifact = artifactInfo)
            repository.download(downloadContext)
        }
    }

    /**
     * 基于旧文件和增量数据进行合并文件
     * @param oldFilePath 旧文件仓库完整路径
     * */
    fun patch(oldFilePath: String) {
        with(ArtifactContext()) {
            val node = nodeClient.getNodeDetail(projectId, repoName, oldFilePath).data
            if (node == null || node.folder) {
                throw ArtifactNotFoundException(artifactInfo.toString())
            }
            val artifactInputStream = storageManager.loadArtifactInputStream(node, storageCredentials)
                ?: throw ArtifactNotFoundException("file[${node.sha256}] not found in ${storageCredentials?.key}")
            val blockInputStream = artifactInputStream.use {
                if (artifactInputStream is FileArtifactInputStream) {
                    FileBlockInputStream(artifactInputStream.file, node.sha256!!)
                } else {
                    val dataOutput = ByteArrayOutputStream()
                    artifactInputStream.copyTo(dataOutput)
                    ByteArrayBlockInputStream(dataOutput.toByteArray(), node.sha256!!)
                }
            }
            blockInputStream.use {
                val file = ArtifactFileFactory.buildBkSync(it, request.inputStream, blockSize)
                val uploadContext = ArtifactUploadContext(file)
                repository.upload(uploadContext)
            }
        }
    }

    /**
     * 保存sign文件到指定仓库
     * @param nodeDetail 节点信息
     * @param file 节点sign文件
     * */
    private fun saveSignFile(nodeDetail: NodeDetail, file: ArtifactFile): ArtifactInfo {
        with(nodeDetail) {
            val signFileFullPath = "$projectId/$repoName/$blockSize/$fullPath$SUFFIX_SIGN"
            val artifactInfo = GenericArtifactInfo(signFileProjectId, signFileRepoName, signFileFullPath)
            nodeClient.getNodeDetail(signFileProjectId, signFileRepoName, signFileFullPath).data ?: let {
                HttpContextHolder.getRequest().setAttribute(ARTIFACT_INFO_KEY, artifactInfo)
                val uploadContext = ArtifactUploadContext(signRepo, file)
                repository.upload(uploadContext)
            }
            val signFile = TSignFile(
                nodeProjectId = projectId,
                nodeRepoName = repoName,
                nodeFullPath = fullPath,
                projectId = signFileProjectId,
                repoName = signFileRepoName,
                fullPath = signFileFullPath,
                blockSize = blockSize,
                createdBy = SecurityUtils.getUserId(),
                createdDate = LocalDateTime.now()
            )
            signFileDao.save(signFile)
            return artifactInfo
        }
    }

    /**
     * 生成节点的sign file
     * */
    private fun generateSignFile(node: NodeDetail, storageCredentials: StorageCredentials?): ChunkedArtifactFile {
        val artifactInputStream = storageManager.loadArtifactInputStream(node, storageCredentials)
            ?: throw ArtifactNotFoundException("file[${node.sha256}] not found in ${storageCredentials?.key}")
        val chunkedArtifactFile = ArtifactFileFactory.buildChunked()
        try {
            val outputStream = ChunkedFileOutputStream(chunkedArtifactFile)
            val nanoTime = measureNanoTime {
                val bkSync = BkSync(blockSize)
                artifactInputStream.use { bkSync.checksum(artifactInputStream, outputStream) }
            }
            val throughput = Throughput(chunkedArtifactFile.getSize(), nanoTime)
            logger.info("Success to generate artifact sign file [${node.fullPath}], $throughput.")
            chunkedArtifactFile.finish()
        } catch (e: Exception) {
            logger.error("Failed to generate artifact sign file [${node.fullPath}]", e)
            chunkedArtifactFile.close()
            throw e
        }
        return chunkedArtifactFile
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeltaSyncService::class.java)
        private const val SUFFIX_SIGN = ".sign"
    }
}
