package com.tencent.bkrepo.generic.service

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.util.IpUtils
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJson
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.bksync.ArtifactBlockChannel
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
import com.tencent.bkrepo.common.bksync.BlockChannel
import com.tencent.bkrepo.common.bksync.FileBlockChannel
import com.tencent.bkrepo.common.bksync.transfer.http.BkSyncMetrics
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.redis.RedisOperation
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.otel.util.AsyncUtils.trace
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.okhttp.HttpClientBuilderFactory
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
import com.tencent.bkrepo.generic.pojo.bkbase.QueryRequest
import com.tencent.bkrepo.generic.pojo.bkbase.QueryResponse
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.text.similarity.LevenshteinDistance
import org.apache.pulsar.shade.org.eclipse.util.UrlEncoded
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.InputStream
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 增量同步实现类
 *
 * */
@Service
class DeltaSyncService(
    genericProperties: GenericProperties,
    val storageManager: StorageManager,
    val nodeService: NodeService,
    val signFileDao: SignFileDao,
    val repositoryService: RepositoryService,
    private val redisOperation: RedisOperation,
) : ArtifactService() {

    private val deltaProperties = genericProperties.delta
    private val bkBaseProperties = genericProperties.bkBase
    private val blockSize: Int
        get() = deltaProperties.blockSize.toBytes().toInt()
    private val patchTimeout: Long
        get() = deltaProperties.patchTimeout.toMillis()
    val signFileProjectId = deltaProperties.projectId
    val signFileRepoName = deltaProperties.repoName
    val signRepo: RepositoryDetail by lazy {
        repositoryService.getRepoDetail(signFileProjectId, signFileRepoName)
            ?: throw ErrorCodeException(ArtifactMessageCode.REPOSITORY_NOT_FOUND, signFileRepoName)
    }
    private val httpClient = HttpClientBuilderFactory.create().build()

    /**
     * 签名文件
     * */
    fun downloadSignFile(queryMd5: String? = null) {
        with(ArtifactContext()) {
            val md5 = queryMd5 ?: getMd5FromNode(this)
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
        saveSignFile(artifactInfo, file, md5)
    }

    /**
     * 基于旧文件和增量数据进行合并文件
     * @param oldFilePath 旧文件仓库完整路径
     * */
    fun patch(oldFilePath: String, deltaFile: ArtifactFile): SseEmitter {
        with(ArtifactContext()) {
            val node = nodeService.getNodeDetail(
                ArtifactInfo(
                    projectId,
                    repoName,
                    UrlEncoded.decodeString(oldFilePath, 0, oldFilePath.length, Charsets.UTF_8)
                ),
            )
            if (node == null || node.folder) {
                throw NodeNotFoundException(artifactInfo.getArtifactFullPath())
            }
            val overwrite = HeaderUtils.getBooleanHeader(HEADER_OVERWRITE)
            if (!overwrite) {
                nodeService.getNodeDetail(artifactInfo)?.let {
                    throw ErrorCodeException(
                        ArtifactMessageCode.NODE_EXISTED,
                        artifactInfo.getArtifactName(),
                    )
                }
            }
            val counterInputStream = CounterInputStream(deltaFile.getInputStream())
            val emitter = SseEmitter(patchTimeout)
            val blockChannel = getBlockChannel(node, storageCredentials)
            try {
                val patchContext = buildPatchContext(counterInputStream, emitter, this, blockChannel)
                val reportAction = Runnable { reportProcess(patchContext) }
                val heartBeatFuture = heartBeatExecutor.scheduleWithFixedDelay(
                    reportAction,
                    HEART_BEAT_INITIAL_DELAY,
                    HEART_BEAT_INTERVAL,
                    TimeUnit.MILLISECONDS,
                )
                patchContext.heartBeatFuture = heartBeatFuture
                asyncExecutePatchTask(patchContext, heartBeatFuture)
            } catch (e: Exception) {
                // 由于是异步使用，所以是在异步中进行关闭。但是如果出现异常，未进入到异步流程，则需要手动关闭。
                counterInputStream.close()
                blockChannel.close()
                throw e
            }
            return emitter
        }
    }

    /**
     * 异步执行合并任务
     * */
    private fun asyncExecutePatchTask(
        patchContext: PatchContext,
        heartBeatFuture: ScheduledFuture<*>,
    ) {
        with(patchContext) {
            patchExecutor.execute {
                try {
                    doPatch(patchContext, heartBeatFuture)
                } finally {
                    blockChannel.close()
                    counterInputStream.close()
                }
            }
        }
    }

    fun isInWhiteList(clientIp: String, taskId: String?, fileType: String?): Boolean {
        if (!deltaProperties.enable) {
            return false
        }
        deltaProperties.ipBlackList.forEach {
            if (IpUtils.isInRange(clientIp, it)) {
                return false
            }
        }
        if (taskId == null || fileType == null) {
            return true
        }
        val key = "$BLACK_LIST_PREFIX${taskId}${StringPool.COLON}$fileType"
        return redisOperation.get(key)?.toBoolean() ?: true
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

    fun recordMetrics(ip: String, metrics: BkSyncMetrics) {
        val runnable = Runnable {
            metrics.ip = ip
            // 增量上传成功的才需要记录历史上传速度，以便计算节省时间
            if (metrics.networkSpeed < deltaProperties.allowUseMaxBandwidth && metrics.genericUploadTime == 0L) {
                metrics.historyGenericUploadSpeed = getHistoryUploadSpeed(metrics)
            }
            if (metrics.historyGenericUploadSpeed > 0) {
                forbidNegativeSituation(metrics)
            }
            logger.info(metrics.toJsonString().replace(System.lineSeparator(), ""))
        }.trace()
        metricsExecutor.execute(runnable)
    }

    /**
     * 获取历史上传速度
     * 1. 从数据平台查询到相同taskId、文件类型的普通上传记录
     * 2. 筛选出最近一次构建的记录
     * 3. 计算文件名的编辑距离，取距离最小的记录
     * 4. 计算上传速度
     */
    private fun getHistoryUploadSpeed(metrics: BkSyncMetrics): Double {
        if (bkBaseProperties.domain.isBlank()) {
            return 0.0
        }
        val sql = "SELECT buildId,fileName,fileSize,genericUploadTime FROM ${bkBaseProperties.table} " +
            "WHERE dtEventTimeStamp >= 0 AND dtEventTimeStamp <= ${System.currentTimeMillis()} " +
            "AND networkSpeed < ${deltaProperties.allowUseMaxBandwidth} " +
            "AND genericUploadTime > 0 " +
            "AND taskId = '${metrics.taskId}' " +
            "AND fileType = '${metrics.fileType}' " +
            "AND buildId != '${metrics.buildId}' " +
            "ORDER BY dtEventTimeStamp DESC LIMIT 100"
        val url = UrlFormatter.format(bkBaseProperties.domain, "/prod/v3/dataquery/query/")
        val query = QueryRequest(token = bkBaseProperties.token, sql = sql)
        val authHeader = toJson(mapOf(
            "bk_app_code" to bkBaseProperties.appCode,
            "bk_app_secret" to bkBaseProperties.appSecret
        ))
        val requestBody = query.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .header("X-Bkapi-Authorization", authHeader)
            .post(requestBody).build()
        return queryHistorySpeed(request, sql, metrics)
    }

    private fun queryHistorySpeed(
        request: Request,
        sql: String,
        metrics: BkSyncMetrics,
        retryTime: Int = 3,
    ): Double {
        if (retryTime == 0) {
            return 0.0
        }
        try {
            httpClient.newCall(request).execute().use { response ->
                val queryResponse = response.body!!.string().readJsonString<QueryResponse>()
                if (!response.isSuccessful) {
                    logger.warn("sql[$sql] query failed: ${queryResponse.code}")
                    return queryHistorySpeed(request, sql, metrics, retryTime - 1)
                }
                val dataList = queryResponse.data.list
                if (dataList.isEmpty()) {
                    return 0.0
                }
                val latestBuildId = dataList.first()[BkSyncMetrics::buildId.name]
                val latestBuildData = dataList.filter { it[BkSyncMetrics::buildId.name] == latestBuildId }
                val editDistance = LevenshteinDistance()
                var minEditDistance: Int = Int.MAX_VALUE
                var minIndex: Int = -1
                latestBuildData.forEachIndexed { index, map ->
                    val distance = editDistance.apply(metrics.fileName, map[BkSyncMetrics::fileName.name].toString())
                    if (distance < minEditDistance) {
                        minEditDistance = distance
                        minIndex = index
                    }
                }
                val data = latestBuildData[minIndex]
                val fileSize = data[BkSyncMetrics::fileSize.name].toString().toLong()
                val uploadTime = data[BkSyncMetrics::genericUploadTime.name].toString().toDouble()
                return fileSize.div(uploadTime)
            }
        } catch (e: Exception) {
            logger.warn(
                "get [${metrics.projectId}/${metrics.pipelineId}/${metrics.buildId}/${metrics.fileName}]" +
                    "history upload speed failed",
                e,
            )
            return queryHistorySpeed(request, sql, metrics, retryTime - 1)
        }
    }

    /**
     * 加速比率为负反馈时，根据taskId和fileType禁止使用增量上传
     */
    private fun forbidNegativeSituation(metrics: BkSyncMetrics) {
        with(metrics) {
            val deltaUploadSpeed = fileSize / (diffTime + patchTime)
            val accelerateRatio = (deltaUploadSpeed / historyGenericUploadSpeed) - 1
            if (accelerateRatio < 0) {
                val key = "$BLACK_LIST_PREFIX${taskId}${StringPool.COLON}$fileType"
                redisOperation.set(key, false.toString(), deltaProperties.blackListExpired.seconds)
            }
        }
    }

    /**
     * 根据上下文中的节点信息获取节点的md5
     * */
    private fun getMd5FromNode(context: ArtifactContext): String {
        with(context) {
            val node = nodeService.getNodeDetail(artifactInfo)
            if (node == null || node.folder) {
                throw NodeNotFoundException(artifactInfo.getArtifactFullPath())
            }
            return node.md5!!
        }
    }

    /**
     * 执行patch
     * @param patchContext patch上下文
     * */
    private fun doPatch(patchContext: PatchContext, heartBeatFuture: ScheduledFuture<*>) {
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
                        metadata,
                    )
                val nodeDetail = storageManager
                    .storeArtifactFile(nodeCreateRequest, file, repositoryDetail.storageCredentials)
                val event = SseEmitter.event().name(PATCH_EVENT_TYPE_DATA)
                    .data(nodeDetail, MediaType.APPLICATION_JSON)
                emitter.send(event)
                heartBeatFuture.cancel(false)
                emitter.complete()
            } catch (e: ErrorCodeException) {
                // 客户端异常
                sendError(e.message.orEmpty(), emitter)
                heartBeatFuture.cancel(false)
                emitter.complete()
            } catch (e: Exception) {
                // 服务端异常
                sendError(e.message.orEmpty(), emitter)
                heartBeatFuture.cancel(false)
                emitter.completeWithError(e)
                logger.error("Patch artifact[$artifactInfo] failed.", e)
            }
        }
    }

    /**
     * sse发送错误信息
     * @param errorMsg 错误信息
     * @param sseEmitter sse发送器
     * */
    private fun sendError(errorMsg: String, sseEmitter: SseEmitter) {
        val msg = SseEmitter.event()
            .name(PATCH_EVENT_TYPE_ERROR)
            .data(errorMsg)
        sseEmitter.send(msg)
    }

    /**
     * 上报进度
     * */
    private fun reportProcess(
        patchContext: PatchContext,
    ) {
        with(patchContext) {
            try {
                val process = String.format("%.2f", (counterInputStream.count.toFloat() / contentLength) * 100)
                val msg = "Current process $process%."
                val event = SseEmitter.event().name(PATCH_EVENT_TYPE_INFO).data(msg, MediaType.TEXT_PLAIN)
                emitter.send(event)
                logger.info(msg)
            } catch (e: Exception) {
                heartBeatFuture?.cancel(false)
                emitter.completeWithError(e)
                logger.warn("Send heartbeat failed: ${e.message}")
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
        metadata: List<MetadataModel>,
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
            nodeMetadata = metadata,
        )
    }

    /**
     * 构建Patch上下文
     * @param emitter sse发送器
     * @param context 当前上下文
     * @param blockChannel 增量同步使用的块输入流
     * */
    private fun buildPatchContext(
        counterInputStream: CounterInputStream,
        emitter: SseEmitter,
        context: ArtifactContext,
        blockChannel: BlockChannel,
    ): PatchContext {
        with(context) {
            val repository = ArtifactContextHolder.getRepository(RepositoryCategory.LOCAL) as GenericLocalRepository
            val file = ArtifactFileFactory.buildBkSync(blockChannel, counterInputStream, blockSize)
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
                blockChannel = blockChannel,
                userId = SecurityUtils.getUserId(),
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
        file: ArtifactFile,
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
            overwrite = true,
        )
    }

    /**
     * 保存sign文件到指定仓库
     * @param artifactInfo 构件信息
     * @param md5 校验和md5
     * @param file 节点sign文件
     * */
    private fun saveSignFile(artifactInfo: GenericArtifactInfo, file: ArtifactFile, md5: String) {
        with(artifactInfo) {
            val signFileFullPath = "$projectId/$repoName/$blockSize/$md5$SUFFIX_SIGN"
            val signFileArtifactInfo = GenericArtifactInfo(signFileProjectId, signFileRepoName, signFileFullPath)
            val nodeCreateRequest =
                buildSignFileNodeCreateRequest(signRepo, signFileArtifactInfo, SecurityUtils.getUserId(), file)
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
                    createdDate = LocalDateTime.now(),
                )
                signFileDao.save(signFile)
                logger.info("Success to save sign file[$signFileFullPath].")
            } catch (ignore: DuplicateKeyException) {
                // 说明文件已存在，可以忽略
            }
        }
    }

    private fun getBlockChannel(node: NodeDetail, storageCredentials: StorageCredentials?): BlockChannel {
        // Cos分片下载可能会造成超时
        val artifactInputStream = storageManager.loadArtifactInputStream(node, storageCredentials)
            ?: throw ArtifactNotFoundException("file[${node.sha256}] not found in ${storageCredentials?.key}")
        val name = node.sha256!!
        // 本地cache
        if (artifactInputStream is FileArtifactInputStream) {
            artifactInputStream.close()
            return FileBlockChannel(artifactInputStream.file, name)
        }
        // 远端网络流
        val artifactFile = ArtifactFileFactory.build(artifactInputStream, node.size)
        return ArtifactBlockChannel(artifactFile, name)
    }

    private class CounterInputStream(
        val inputStream: InputStream,
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
        val contentLength: Long,
        val counterInputStream: CounterInputStream,
        val emitter: SseEmitter,
        val uploadSha256: String?,
        val uploadMd5: String?,
        val expires: Long,
        val metadata: List<MetadataModel>,
        val repositoryDetail: RepositoryDetail,
        val userId: String,
        val artifactInfo: ArtifactInfo,
        val file: ArtifactFile,
        val overwrite: Boolean,
        val blockChannel: BlockChannel,
        var heartBeatFuture: ScheduledFuture<*>? = null,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(DeltaSyncService::class.java)
        private const val SUFFIX_SIGN = ".sign"
        private const val PATCH_EVENT_TYPE_INFO = "INFO"
        private const val PATCH_EVENT_TYPE_ERROR = "ERROR"
        private const val PATCH_EVENT_TYPE_DATA = "DATA"

        // 3s patch 回复心跳时间，保持连接存活
        private const val HEART_BEAT_INTERVAL = 3000L
        private const val HEART_BEAT_INITIAL_DELAY = 1000L
        private const val SPEED_KEY_PREFIX = "delta:speed:"
        private const val BLACK_LIST_PREFIX = "delta:blacklist:"
        private val heartBeatExecutor = ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            ThreadFactoryBuilder().setNameFormat("BkSync-heart-beat-%d").build(),
        )
        private val patchExecutor = ThreadPoolExecutor(
            200,
            200,
            60,
            TimeUnit.SECONDS,
            LinkedBlockingQueue<Runnable>(8192),
            ThreadFactoryBuilder().setNameFormat("BkSync-patch-%d").build(),
        )
        private val metricsExecutor = ThreadPoolExecutor(
            4,
            20,
            60,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(1024),
            ThreadFactoryBuilder().setNameFormat("BkSync-metrics-%d").build(),
        )
    }
}
