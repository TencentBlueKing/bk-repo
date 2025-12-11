package com.tencent.bkrepo.common.artifact.resolve.file.receiver

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetrics
import com.tencent.bkrepo.common.artifact.metrics.TrafficHandler
import com.tencent.bkrepo.common.artifact.stream.DigestCalculateListener
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
        try {
            val res = RecordableDigestInputStream(source, trafficHandler, listener).use { uploadSession.upload(it) }
            crc64ecma = res.crc64ecma
        } catch (e: InnerCosException) {
            val cause = e.cause
            val stackStraceOfReadExp = "com.tencent.bkrepo.common.artifact.stream.DelegateInputStream.read"
            if (cause?.stackTrace?.any { it.toString().startsWith(stackStraceOfReadExp) } == true) {
                // 可能由于客户端断开连接导致报错，此时需要抛出cause，用于外层判断是否为客户端错误确认日志等级
                throw cause
            } else {
                throw e
            }
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
        require(uploadSession.completed || uploadSession.aborted)
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
            return "${UUID.randomUUID().toString().replace("-", "")}.uploading"
        }
    }
}
