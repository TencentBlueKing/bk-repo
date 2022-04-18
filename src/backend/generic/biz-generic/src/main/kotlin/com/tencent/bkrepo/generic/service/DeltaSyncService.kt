package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.stream.CompositeOutputStream
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.file.chunk.ChunkedFileOutputStream
import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.common.bksync.BkSync
import com.tencent.bkrepo.common.bksync.BlockInputStream
import com.tencent.bkrepo.common.bksync.ByteArrayBlockInputStream
import com.tencent.bkrepo.common.bksync.ChecksumIndex
import com.tencent.bkrepo.common.bksync.FileBlockInputStream
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.Throughput
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.config.GenericProperties
import com.tencent.bkrepo.generic.dao.SignFileDao
import com.tencent.bkrepo.generic.model.TSignFile
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
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
    val repositoryClient: RepositoryClient,
    val storageProperties: StorageProperties,
    val taskExecutor: ThreadPoolTaskScheduler
) : ArtifactService() {

    private val deltaProperties = genericProperties.delta
    private val blockSize = deltaProperties.blockSize.toBytes().toInt()
    private val patchTimeout = deltaProperties.patchTimeout.toMillis()
    val signFileProjectId = deltaProperties.projectId!!
    val signFileRepoName = deltaProperties.repoName!!
    val signRepo: RepositoryDetail by lazy {
        repositoryClient.getRepoDetail(signFileProjectId, signFileRepoName).data
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, signFileRepoName)
    }
    private val fileSizeThreshold = storageProperties.receive.fileSizeThreshold.toBytes()

    /**
     * 签名文件
     * */
    fun sign() {
        with(ArtifactContext()) {
            val node = nodeClient.getNodeDetail(projectId, repoName, artifactInfo.getArtifactFullPath()).data
            if (node == null || node.folder) {
                throw NodeNotFoundException(artifactInfo.getArtifactFullPath())
            }
            // 查看是否已有sign文件，没有则生成。
            val sha256 = node.sha256!!
            signFileDao.findByDetail(sha256, blockSize)?.let {
                val artifactInfo = GenericArtifactInfo(it.projectId, it.repoName, it.fullPath)
                val downloadContext = ArtifactDownloadContext(repo = signRepo, artifact = artifactInfo)
                repository.download(downloadContext)
                return
            }
            // 计算出需要返回的大小
            val length = ceil(node.size.toDouble() / blockSize) * ChecksumIndex.CHECKSUM_SIZE
            response.setContentLength(length.toInt())
            val chunkedArtifactFile = ArtifactFileFactory.buildChunked()
            val chunkedFileOutputStream = ChunkedFileOutputStream(chunkedArtifactFile)
            val outputStream = CompositeOutputStream(chunkedFileOutputStream, response.outputStream).buffered()
            outputStream.use {
                val artifactInputStream = storageManager.loadArtifactInputStream(node, storageCredentials)
                    ?: throw ArtifactNotFoundException("file[$sha256] not found in ${storageCredentials?.key}")
                val nanoTime = measureNanoTime {
                    val bkSync = BkSync(blockSize)
                    artifactInputStream.buffered().use { bkSync.checksum(artifactInputStream, outputStream) }
                }
                val throughput = Throughput(chunkedArtifactFile.getSize(), nanoTime)
                logger.info("Success to generate artifact sign file [${node.fullPath}], $throughput.")
                outputStream.flush()
                chunkedArtifactFile.finish()
                saveSignFile(node, chunkedArtifactFile)
            }
        }
    }

    /**
     * 基于旧文件和增量数据进行合并文件
     * @param oldFilePath 旧文件仓库完整路径
     * */
    fun patch(oldFilePath: String, deltaFile: ArtifactFile): SseEmitter {
        with(ArtifactContext()) {
            val node = nodeClient.getNodeDetail(projectId, repoName, oldFilePath).data
            if (node == null || node.folder) {
                throw NodeNotFoundException(artifactInfo.getArtifactFullPath())
            }
            val hasCompleted = AtomicBoolean(false)
            val contentLength = request.contentLengthLong
            val emitter = SseEmitter(patchTimeout)
            val reportAction: (inputStream: CounterInputStream) -> Unit = {
                if (!hasCompleted.get()) {
                    val process = String.format("%.2f", (it.count.toFloat() / contentLength) * 100)
                    val msg = "Current process $process%."
                    val event = SseEmitter.event().name(PATCH_EVENT_TYPE_INFO).data(msg, MediaType.TEXT_PLAIN)
                    emitter.send(event)
                    logger.info(msg)
                } else {
                    logger.info("Patch has already completed.")
                }
            }
            val counterInputStream = CounterInputStream(deltaFile.getInputStream())
            val ackFuture = taskExecutor.scheduleAtFixedRate({ reportAction(counterInputStream) }, HEART_BEAT)
            val requestAttributes = RequestContextHolder.getRequestAttributes()
            taskExecutor.execute {
                try {
                    RequestContextHolder.setRequestAttributes(requestAttributes)
                    doPatch(node, storageCredentials, counterInputStream, emitter)
                    emitter.complete()
                } catch (e: Exception) {
                    emitter.completeWithError(e)
                } finally {
                    hasCompleted.set(true)
                    ackFuture.cancel(true)
                    RequestContextHolder.resetRequestAttributes()
                }
            }
            return emitter
        }
    }

    private fun doPatch(
        node: NodeDetail,
        storageCredentials: StorageCredentials?,
        deltaInputStream: InputStream,
        emitter: SseEmitter
    ) {
        val blockInputStream = getBlockInputStream(node, storageCredentials)
        blockInputStream.use {
            val file = ArtifactFileFactory.buildBkSync(it, deltaInputStream, blockSize)
            val uploadContext = ArtifactUploadContext(file)
            with(uploadContext) {
                val request = NodeCreateRequest(
                    projectId = repositoryDetail.projectId,
                    repoName = repositoryDetail.name,
                    folder = false,
                    fullPath = artifactInfo.getArtifactFullPath(),
                    size = file.getSize(),
                    sha256 = getArtifactSha256(),
                    md5 = getArtifactMd5(),
                    operator = userId,
                    overwrite = true
                )
                val nodeDetail = storageManager.storeArtifactFile(request, file, storageCredentials)
                val event = SseEmitter.event().name(PATCH_EVENT_TYPE_DATA)
                    .data(nodeDetail, MediaType.APPLICATION_JSON)
                emitter.send(event)
            }
        }
    }

    /**
     * 保存sign文件到指定仓库
     * @param nodeDetail 节点信息
     * @param file 节点sign文件
     * */
    private fun saveSignFile(nodeDetail: NodeDetail, file: ArtifactFile) {
        with(nodeDetail) {
            val signFileFullPath = "$projectId/$repoName/$blockSize/$fullPath$SUFFIX_SIGN"
            val artifactInfo = GenericArtifactInfo(signFileProjectId, signFileRepoName, signFileFullPath)
            val uploadContext = ArtifactUploadContext(signRepo, file, artifactInfo)
            uploadSignFile(uploadContext)
            val signFile = TSignFile(
                srcSha256 = sha256!!,
                projectId = signFileProjectId,
                repoName = signFileRepoName,
                fullPath = signFileFullPath,
                blockSize = blockSize,
                createdBy = SecurityUtils.getUserId(),
                createdDate = LocalDateTime.now()
            )
            signFileDao.save(signFile)
            logger.info("Success to save sign file[$signFileFullPath].")
        }
    }

    private fun getBlockInputStream(node: NodeDetail, storageCredentials: StorageCredentials?): BlockInputStream {
        val artifactInputStream = storageManager.loadArtifactInputStream(node, storageCredentials)
            ?: throw ArtifactNotFoundException("file[${node.sha256}] not found in ${storageCredentials?.key}")
        artifactInputStream.use {
            // 小于文件内存大小，则使用内存
            val name = node.sha256!!
            if (node.size <= fileSizeThreshold) {
                val dataOutput = ByteArrayOutputStream()
                artifactInputStream.copyTo(dataOutput)
                return ByteArrayBlockInputStream(dataOutput.toByteArray(), name)
            }
            // 本地cache
            if (artifactInputStream is FileArtifactInputStream) {
                return FileBlockInputStream(artifactInputStream.file, name)
            }
            // 远端网络流
            val file = ArtifactFileFactory.build(artifactInputStream, node.size).getFile()!!
            return FileBlockInputStream(file, name)
        }
    }

    private fun uploadSignFile(uploadContext: ArtifactUploadContext) {
        with(uploadContext) {
            val artifactFile = getArtifactFile()
            val request = NodeCreateRequest(
                projectId = repositoryDetail.projectId,
                repoName = repositoryDetail.name,
                folder = false,
                fullPath = artifactInfo.getArtifactFullPath(),
                size = artifactFile.getSize(),
                sha256 = getArtifactSha256(),
                md5 = getArtifactMd5(),
                operator = userId,
                overwrite = true
            )
            storageManager.storeArtifactFile(request, artifactFile, storageCredentials)
        }
    }

    private class CounterInputStream(
        val inputStream: InputStream
    ) : InputStream() {
        var count = 0L

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            return inputStream.read(b, off, len).apply {
                if (this > 0) {
                    count += this
                }
            }
        }

        override fun read(): Int {
            return inputStream.read().apply {
                if (this > 0) {
                    count += this
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeltaSyncService::class.java)
        private const val SUFFIX_SIGN = ".sign"
        private const val PATCH_EVENT_TYPE_INFO = "INFO"
        private const val PATCH_EVENT_TYPE_DATA = "DATA"

        // patch 回复心跳时间，保持连接存活
        private const val HEART_BEAT = 1000L
    }
}
