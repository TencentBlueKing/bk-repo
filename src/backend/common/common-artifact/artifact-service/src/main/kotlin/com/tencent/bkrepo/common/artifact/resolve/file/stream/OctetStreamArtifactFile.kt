package com.tencent.bkrepo.common.artifact.resolve.file.stream

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactFile.Companion.generateRandomName
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_UPLOADED_BYTES_COUNT
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_UPLOADED_CONSUME_COUNT
import com.tencent.bkrepo.common.artifact.resolve.file.SmartStreamReceiver
import com.tencent.bkrepo.common.artifact.resolve.file.UploadConfigElement
import com.tencent.bkrepo.common.artifact.stream.DigestCalculateListener
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import io.micrometer.core.instrument.Metrics
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException

open class OctetStreamArtifactFile(
    private val source: InputStream,
    private val monitor: StorageHealthMonitor,
    config: UploadConfigElement
) : ArtifactFile {

    private var hasInitialized: Boolean = false
    private val listener = DigestCalculateListener()
    private val receiver = SmartStreamReceiver(config.fileSizeThreshold, generateRandomName(), monitor.getPrimaryPath(), monitor.monitorConfig.enableTransfer)

    init {
        if (!config.isResolveLazily()) {
            init()
        }
    }

    override fun getInputStream(): InputStream {
        init()
        return if (!isInMemory()) {
            Files.newInputStream(receiver.getFilePath())
        } else {
            ByteArrayInputStream(receiver.getCachedByteArray())
        }
    }

    override fun getSize(): Long {
        init()
        return receiver.totalSize
    }

    override fun isInMemory(): Boolean {
        init()
        return receiver.isInMemory
    }

    override fun getFile(): File? {
        init()
        return if (!isInMemory()) {
            receiver.getFilePath().toFile()
        } else null
    }

    override fun flushToFile(): File {
        init()
        if (isInMemory()) {
            receiver.flushToFile()
        }
        return receiver.getFilePath().toFile()
    }

    override fun isFallback(): Boolean {
        init()
        return receiver.fallback
    }

    override fun getFileMd5(): String {
        init()
        return listener.md5
    }

    override fun getFileSha256(): String {
        init()
        return listener.sha256
    }

    override fun delete() {
        if (hasInitialized && !isInMemory()) {
            try {
                Files.deleteIfExists(receiver.getFilePath())
            } catch (e: NoSuchFileException) { // already deleted
            }
        }
    }

    override fun hasInitialized(): Boolean {
        return hasInitialized
    }

    fun init() {
        if (!hasInitialized) {
            try {
                monitor.add(receiver)
                if (!monitor.health.get()) {
                    receiver.unhealthy(monitor.getFallbackPath(), monitor.reason)
                }
                val throughput = receiver.receive(source, listener)
                hasInitialized = true

                Metrics.counter(ARTIFACT_UPLOADED_BYTES_COUNT).increment(throughput.bytes.toDouble())
                Metrics.counter(ARTIFACT_UPLOADED_CONSUME_COUNT).increment(throughput.duration.toMillis().toDouble())
                logger.info("Receive artifact file, $throughput.")
            } finally {
                monitor.remove(receiver)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OctetStreamArtifactFile::class.java)
    }
}
