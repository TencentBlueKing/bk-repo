package com.tencent.bkrepo.common.artifact.resolve.file.stream

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.event.ArtifactReceivedEvent
import com.tencent.bkrepo.common.artifact.hash.sha1
import com.tencent.bkrepo.common.artifact.resolve.file.receiver.CosArtifactDataReceiver
import com.tencent.bkrepo.common.ratelimiter.service.RequestLimitCheckService
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.InnerCosCredentials
import io.micrometer.observation.ObservationRegistry
import java.io.File
import java.io.InputStream

/**
 * 支持流式接收数据并存储于COS的ArtifactFile
 */
class CosStreamArtifactFile(
    private val source: InputStream,
    private val storageProperties: StorageProperties,
    private val storageCredentials: InnerCosCredentials,
    private val contentLength: Long,
    private val requestLimitCheckService: RequestLimitCheckService? = null,
    private val registry: ObservationRegistry
) : ArtifactFile {

    /**
     * 是否初始化
     */
    private var initialized: Boolean = false

    /**
     * 文件sha1值
     */
    private var sha1: String? = null

    private val receiver = CosArtifactDataReceiver(
        storageCredentials = storageCredentials,
        receiveProperties = storageProperties.receive,
        registry = registry,
        requestLimitCheckService = requestLimitCheckService,
        contentLength = contentLength
    )

    init {
        if (!storageProperties.receive.resolveLazily) {
            init()
        }
    }

    override fun getInputStream(): InputStream {
        init()
        return receiver.getInputStream()
    }

    override fun getSize(): Long {
        init()
        return receiver.receivedSize()
    }

    override fun isInMemory(): Boolean {
        init()
        return receiver.inMemory
    }

    override fun getFile(): File? {
        throw UnsupportedOperationException()
    }

    override fun flushToFile(): File {
        throw UnsupportedOperationException()
    }

    override fun delete() {
        if (initialized && !isInMemory()) {
            receiver.close()
        }
    }

    override fun hasInitialized(): Boolean {
        return initialized
    }

    override fun isFallback() = false

    override fun isInLocalDisk() = false

    override fun getFileMd5(): String {
        init()
        return receiver.listener.getMd5()
    }

    override fun getFileSha1(): String {
        init()
        return sha1 ?: getInputStream().sha1().apply { sha1 = this }
    }

    override fun getFileSha256(): String {
        init()
        return receiver.listener.getSha256()
    }

    override fun getFileCrc64ecma(): String {
        init()
        return receiver.listener.getCrc64ecma()
    }

    private fun init() {
        if (initialized) {
            return
        }

        receiver.receiveStream(source)
        val throughput = receiver.finish()
        initialized = true
        SpringContextUtils.publishEvent(ArtifactReceivedEvent(this, throughput, storageCredentials))
    }
}
