package com.tencent.bkrepo.common.artifact.resolve.file.receiver

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetrics
import com.tencent.bkrepo.common.artifact.metrics.TrafficHandler
import com.tencent.bkrepo.common.artifact.exception.ArtifactInputStreamReadException
import com.tencent.bkrepo.common.artifact.stream.DigestCalculateListener
import com.tencent.bkrepo.common.artifact.stream.SourceMarkerInputStream
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import com.tencent.bkrepo.common.storage.config.ReceiveProperties
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.innercos.exception.InnerCosException
import com.tencent.bkrepo.common.storage.innercos.request.DeleteObjectRequest
import com.tencent.bkrepo.common.storage.innercos.request.GetObjectRequest
import com.tencent.bkrepo.common.storage.monitor.Throughput
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * 数据接收器，会将接收到的数据写到COS
 */
class CosArtifactDataReceiver(
    private val storageCredentials: InnerCosCredentials,
    receiveProperties: ReceiveProperties,
    registry: ObservationRegistry,
    requestLimitCheckService: RequestLimitCheckService? = null,
    contentLength: Long,
) : AbsArtifactDataReceiver(receiveProperties, requestLimitCheckService, registry, contentLength) {

    override val listener: DigestCalculateListener by lazy { DigestCalculateListener() }

    override var inMemory: Boolean = false

    private var crc64ecma: String? = null

    /**
     * 上传数据统计
     */
    private val trafficHandler by lazy {
        TrafficHandler(
            ArtifactMetrics.getUploadingCounters(this),
            ArtifactMetrics.getUploadingTimer(this),
            TrafficHandler.TransferType.UPLOAD,
        )
    }

    /**
     * COS Client
     */
    private val client by lazy { clientCache.get(storageCredentials) }

    /**
     * COS分片上传会话
     */
    private val uploadSession: CosClient.MultipartUploadSession by lazy {
        client.createMultipartUploadSession(generateRandomKey(), contentLength, null, false)
    }

    override fun doReceiveChunk(chunk: ByteArray, offset: Int, length: Int) {
        throw UnsupportedOperationException()
    }

    override fun doReceive(b: Int) {
        throw UnsupportedOperationException()
    }

    override fun doReceiveStream(source: InputStream) {
        // 用 SourceMarkerInputStream 包装源流，把源流 read 抛出的 IOException 标记成
        // ArtifactInputStreamReadException，后续可通过类型+cause 链准确识别"源流读取失败"，
        val markedSource = SourceMarkerInputStream(source)
        try {
            val res = RecordableDigestInputStream(markedSource, trafficHandler, listener)
                .use { uploadSession.upload(it) }
            crc64ecma = res.crc64ecma
        } catch (e: InnerCosException) {
            // 命中标记异常说明是源流（通常是客户端连接）读取失败，
            // 抛出最原始的 IOException，便于外层根据异常类型/消息判定日志等级。
            val readEx = ArtifactInputStreamReadException.findIn(e)
            if (readEx != null) {
                throw readEx.cause as? IOException ?: readEx
            }
            throw e
        }
    }

    override fun checkSize() {
        require(uploadSession.completed) { "upload has not been completed yet" }
        require(uploadSession.uploaded == contentLength) {
            "uploaded size[${uploadSession.uploaded}] does not match the content length[$contentLength]"
        }
        require(crc64ecma == listener.getCrc64ecma()) {
            "uploaded crc64[$crc64ecma] does not match the received value[${listener.getCrc64ecma()}]"
        }
    }

    override fun getInputStream(): InputStream {
        checkFinished()
        return client.getObject(GetObjectRequest(uploadSession.key)).inputStream
            ?: throw IllegalArgumentException("failed to get object[${uploadSession.key}] from COS")
    }

    override fun receivedSize(): Long = uploadSession.uploaded

    override fun finish(): Throughput {
        require(uploadSession.completed)
        return super.finish()
    }

    override fun close() {
        if (uploadSession.aborted) {
            logger.info("upload session of obj[${uploadSession.key}] was aborted, skip delete")
        }
        if (uploadSession.completed) {
            client.deleteObject(DeleteObjectRequest((uploadSession.key)))
            logger.info("delete cos obj[${uploadSession.key}] success")
        }
    }

    fun getObjKey() = uploadSession.key

    fun getUploadId() = uploadSession.uploadId

    private fun checkFinished() {
        if (!finished) {
            throw IllegalStateException("receiver was not finished")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CosArtifactDataReceiver::class.java)
        private const val DEFAULT_CLIENT_CACHE_SIZE = 32L

        private val clientCache: LoadingCache<InnerCosCredentials, CosClient> by lazy {
            val cacheLoader = object : CacheLoader<InnerCosCredentials, CosClient>() {
                override fun load(credentials: InnerCosCredentials) = CosClient(credentials)
            }
            CacheBuilder.newBuilder().maximumSize(DEFAULT_CLIENT_CACHE_SIZE).build(cacheLoader)
        }

        private fun generateRandomKey(): String {
            return "uploading_${UUID.randomUUID().toString().replace("-", "")}"
        }
    }
}
