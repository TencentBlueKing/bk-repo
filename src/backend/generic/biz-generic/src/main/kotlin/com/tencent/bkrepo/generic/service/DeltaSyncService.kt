package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.common.artifact.util.http.IOExceptionUtils
import com.tencent.bkrepo.common.bksync.BlockInputStream
import com.tencent.bkrepo.common.bksync.ByteArrayBlockInputStream
import com.tencent.bkrepo.common.bksync.FileBlockInputStream
import com.tencent.bkrepo.common.redis.RedisOperation
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.generic.artifact.GenericArtifactInfo
import com.tencent.bkrepo.generic.artifact.GenericLocalRepository
import com.tencent.bkrepo.generic.config.GenericProperties
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.generic.constant.HEADER_EXPIRES
import com.tencent.bkrepo.generic.constant.HEADER_MD5
import com.tencent.bkrepo.generic.constant.HEADER_OVERWRITE
import com.tencent.bkrepo.generic.constant.HEADER_SHA256
import com.tencent.bkrepo.generic.dao.SignFileDao
import com.tencent.bkrepo.generic.enum.GenericAction
import com.tencent.bkrepo.generic.model.TSignFile
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

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
    storageProperties: StorageProperties,
    val taskExecutor: ThreadPoolTaskScheduler,
    private val redisOperation: RedisOperation
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
    fun downloadSignFile() {
        with(ArtifactContext()) {
            val node = nodeClient.getNodeDetail(projectId, repoName, artifactInfo.getArtifactFullPath()).data
            if (node == null || node.folder) {
                throw NodeNotFoundException(artifactInfo.getArtifactFullPath())
            }
            // 查看是否已有sign文件，没有则生成。
            val md5 = node.md5!!
            val signNode = signFileDao.findByDetail(projectId, repoName, md5, blockSize)
                ?: throw NotFoundException(GenericMessageCode.SIGN_FILE_NOT_FOUND, md5)
            if (request.method == HttpMethod.HEAD.name) {
                return
            }
            val artifactInfo = GenericArtifactInfo(signNode.projectId, signNode.repoName, signNode.fullPath)
            val downloadContext = ArtifactDownloadContext(repo = signRepo, artifact = artifactInfo)
            repository.download(downloadContext)
        }
    }

    fun uploadSignFile(file: ArtifactFile, artifactInfo: GenericArtifactInfo, md5: String) {
        with(artifactInfo) {
            val node = nodeClient.getNodeDetail(projectId, repoName, artifactInfo.getArtifactFullPath()).data
            if (node == null || node.folder) {
                throw NodeNotFoundException(artifactInfo.getArtifactFullPath())
            }
            if (md5 != node.md5) {
                throw ErrorCodeException(GenericMessageCode.NODE_DATA_HAS_CHANGED)
            }
            signFileDao.findByDetail(projectId, repoName, md5, blockSize) ?: saveSignFile(node, file)
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
            val overwrite = HeaderUtils.getBooleanHeader(HEADER_OVERWRITE)
            if (!overwrite) {
                nodeClient.getNodeDetail(projectId, repoName, artifactInfo.getArtifactFullPath()).data?.let {
                    throw ErrorCodeException(
                        ArtifactMessageCode.NODE_EXISTED, artifactInfo.getArtifactName()
                    )
                }
            }
            val counterInputStream = CounterInputStream(deltaFile.getInputStream())
            val blockInputStream = getBlockInputStream(node, storageCredentials)
            val emitter = SseEmitter(patchTimeout)
            val patchContext = buildPatchContext(counterInputStream, emitter, this, blockInputStream)
            emitter.onCompletion { patchContext.hasCompleted.set(true) }
            val reportAction = { reportProcess(patchContext) }
            val ackFuture = taskExecutor.scheduleWithFixedDelay(reportAction, HEART_BEAT_INTERVAL)
            taskExecutor.execute {
                try {
                    doPatch(patchContext)
                } finally {
                    blockInputStream.close()
                    counterInputStream.close()
                    ackFuture.cancel(true)
                }
            }
            return emitter
        }
    }

    fun whiteList(): List<String> {
        return deltaProperties.whiteList
    }

    fun recordSpeed(ip: String, action: GenericAction, speed: Int) {
        val key = "$SPEED_KEY_PREFIX$ip:$action"
        val expiredInSecond = deltaProperties.speedTestExpired.seconds
        redisOperation.set(key, speed.toString(), expiredInSecond)
    }

    fun getSpeed(ip: String, action: GenericAction): Int {
        val key = "$SPEED_KEY_PREFIX$ip:$action"
        return redisOperation.get(key)?.toInt() ?: -1
    }

    /**
     * 执行patch
     * @param patchContext patch上下文
     * */
    private fun doPatch(patchContext: PatchContext) {
        with(patchContext) {
            try {
                verifyCheckSum(uploadMd5, uploadSha256, file)
                val nodeCreateRequest =
                    buildPatchNewNodeCreateRequest(
                        repositoryDetail,
                        artifactInfo,
                        userId,
                        file,
                        expires,
                        overwrite,
                        metadata
                    )
                val nodeDetail = storageManager
                    .storeArtifactFile(nodeCreateRequest, file, repositoryDetail.storageCredentials)
                val event = SseEmitter.event().name(PATCH_EVENT_TYPE_DATA)
                    .data(nodeDetail, MediaType.APPLICATION_JSON)
                emitter.send(event)
                emitter.complete()
            } catch (e: ErrorCodeException) {
                val msg = SseEmitter.event()
                    .name(PATCH_EVENT_TYPE_ERROR)
                    .data(e.message.orEmpty())
                emitter.send(msg)
                emitter.complete()
            } catch (e: Exception) {
                emitter.completeWithError(e)
            }
        }
    }

    /**
     * 上报进度
     * */
    private fun reportProcess(
        patchContext: PatchContext
    ) {
        with(patchContext) {
            try {
                if (clientError.get()) {
                    logger.info("Client has error,ignore report process.")
                    return
                }
                if (!hasCompleted.get()) {
                    val process = String.format("%.2f", (counterInputStream.count.toFloat() / contentLength) * 100)
                    val msg = "Current process $process%."
                    val event = SseEmitter.event().name(PATCH_EVENT_TYPE_INFO).data(msg, MediaType.TEXT_PLAIN)
                    emitter.send(event)
                    logger.info(msg)
                } else {
                    logger.info("Patch has already completed.")
                }
            } catch (e: IOException) {
                if (IOExceptionUtils.isClientBroken(e)) {
                    clientError.set(true)
                    return
                }
                logger.error("Send sse event failed.", e)
            }
        }
    }

    /**
     * 验证校验和
     * */
    private fun verifyCheckSum(md5: String?, sha256: String?, file: ArtifactFile) {
        val calculatedSha256 = file.getFileSha256()
        val calculatedMd5 = file.getFileMd5()
        if (sha256 != null && !calculatedSha256.equals(sha256, true)) {
            throw ErrorCodeException(ArtifactMessageCode.DIGEST_CHECK_FAILED, "sha256")
        }
        if (md5 != null && !calculatedMd5.equals(md5, true)) {
            throw ErrorCodeException(ArtifactMessageCode.DIGEST_CHECK_FAILED, "md5")
        }
    }

    /**
     * 构建合并节点请求
     * */
    private fun buildPatchNewNodeCreateRequest(
        repositoryDetail: RepositoryDetail,
        artifactInfo: ArtifactInfo,
        userId: String,
        file: ArtifactFile,
        expires: Long,
        overwrite: Boolean,
        metadata: Map<String, Any>
    ): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = repositoryDetail.projectId,
            repoName = repositoryDetail.name,
            folder = false,
            fullPath = artifactInfo.getArtifactFullPath(),
            size = file.getSize(),
            sha256 = file.getFileSha256(),
            md5 = file.getFileMd5(),
            operator = userId,
            expires = expires,
            overwrite = overwrite,
            metadata = metadata
        )
    }

    /**
     * 构建Patch上下文
     * @param emitter sse发送器
     * @param context 当前上下文
     * @param blockInputStream 增量同步使用的块输入流
     * */
    private fun buildPatchContext(
        counterInputStream: CounterInputStream,
        emitter: SseEmitter,
        context: ArtifactContext,
        blockInputStream: BlockInputStream
    ): PatchContext {
        with(context) {
            val repository = ArtifactContextHolder.getRepository(RepositoryCategory.LOCAL) as GenericLocalRepository
            val file = ArtifactFileFactory.buildBkSync(blockInputStream, counterInputStream, blockSize)
            return PatchContext(
                uploadSha256 = HeaderUtils.getHeader(HEADER_SHA256),
                uploadMd5 = HeaderUtils.getHeader(HEADER_MD5),
                expires = HeaderUtils.getLongHeader(HEADER_EXPIRES),
                metadata = repository.resolveMetadata(request),
                contentLength = request.contentLengthLong,
                emitter = emitter,
                counterInputStream = counterInputStream,
                overwrite = HeaderUtils.getBooleanHeader(HEADER_OVERWRITE),
                artifactInfo = artifactInfo,
                repositoryDetail = repositoryDetail,
                file = file,
                blockInputStream = blockInputStream,
                userId = SecurityUtils.getUserId()
            )
        }
    }

    /**
     * 构建sign文件节点请求
     * */
    private fun buildSignFileNodeCreateRequest(
        repositoryDetail: RepositoryDetail,
        artifactInfo: ArtifactInfo,
        userId: String,
        file: ArtifactFile
    ): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = repositoryDetail.projectId,
            repoName = repositoryDetail.name,
            folder = false,
            fullPath = artifactInfo.getArtifactFullPath(),
            size = file.getSize(),
            sha256 = file.getFileSha256(),
            md5 = file.getFileMd5(),
            operator = userId,
            overwrite = true
        )
    }

    /**
     * 保存sign文件到指定仓库
     * @param md5 校验和md5
     * @param file 节点sign文件
     * */
    private fun saveSignFile(node: NodeDetail, file: ArtifactFile) {
        with(node) {
            val md5 = node.md5!!
            val signFileFullPath = "$projectId/$repoName/$blockSize/$md5$SUFFIX_SIGN"
            val artifactInfo = GenericArtifactInfo(signFileProjectId, signFileRepoName, signFileFullPath)
            val nodeCreateRequest =
                buildSignFileNodeCreateRequest(signRepo, artifactInfo, SecurityUtils.getUserId(), file)
            try {
                storageManager.storeArtifactFile(nodeCreateRequest, file, signRepo.storageCredentials)
                val signFile = TSignFile(
                    srcProjectId = projectId,
                    srcRepoName = repoName,
                    srcMd5 = md5,
                    projectId = signFileProjectId,
                    repoName = signFileRepoName,
                    fullPath = signFileFullPath,
                    blockSize = blockSize,
                    createdBy = SecurityUtils.getUserId(),
                    createdDate = LocalDateTime.now()
                )
                signFileDao.save(signFile)
                logger.info("Success to save sign file[$signFileFullPath].")
            } catch (ignore: DuplicateKeyException) {
                // 说明文件已存在，可以忽略
            }
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

        override fun close() {
            inputStream.close()
        }
    }

    private data class PatchContext(
        val hasCompleted: AtomicBoolean = AtomicBoolean(false),
        val clientError: AtomicBoolean = AtomicBoolean(false),
        val contentLength: Long,
        val counterInputStream: CounterInputStream,
        val emitter: SseEmitter,
        val uploadSha256: String?,
        val uploadMd5: String?,
        val expires: Long,
        val metadata: Map<String, String>,
        val repositoryDetail: RepositoryDetail,
        val userId: String,
        val artifactInfo: ArtifactInfo,
        val file: ArtifactFile,
        val overwrite: Boolean,
        val blockInputStream: BlockInputStream
    )

    companion object {
        private val logger = LoggerFactory.getLogger(DeltaSyncService::class.java)
        private const val SUFFIX_SIGN = ".sign"
        private const val PATCH_EVENT_TYPE_INFO = "INFO"
        private const val PATCH_EVENT_TYPE_ERROR = "ERROR"
        private const val PATCH_EVENT_TYPE_DATA = "DATA"

        // 3s patch 回复心跳时间，保持连接存活
        private const val HEART_BEAT_INTERVAL = 3000L
        private const val SPEED_KEY_PREFIX = "delta:speed:"
    }
}
