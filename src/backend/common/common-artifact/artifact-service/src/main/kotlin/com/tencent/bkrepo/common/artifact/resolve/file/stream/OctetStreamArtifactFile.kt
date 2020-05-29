package com.tencent.bkrepo.common.artifact.resolve.file.stream

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactFile.Companion.generatePath
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_UPLOADED_BYTES_COUNT
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_UPLOADED_CONSUME_COUNT
import io.micrometer.core.instrument.Metrics
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import kotlin.system.measureNanoTime

class OctetStreamArtifactFile(
    private val source: InputStream,
    fileSizeThreshold: Int,
    location: String,
    resolveLazily: Boolean = true
) : ArtifactFile {

    private val filePath = generatePath(Paths.get(location))
    private val outputStream = ThresholdOutputStream(fileSizeThreshold, filePath)
    private var hasInitialized: Boolean = false

    init {
        if (!resolveLazily) {
            init()
        }
    }

    override fun getInputStream(): InputStream {
        init()
        return if (!isInMemory()) {
            BufferedInputStream(Files.newInputStream(filePath))
        } else {
            ByteArrayInputStream(outputStream.getCachedByteArray())
        }
    }

    override fun getSize(): Long {
        init()
        return outputStream.totalBytes
    }

    override fun isInMemory(): Boolean {
        init()
        return outputStream.isInMemory()
    }

    override fun getFile(): File? {
        init()
        return if (!isInMemory()) {
            filePath.toFile()
        } else null
    }

    override fun flushToFile(): File {
        init()
        if (isInMemory()) {
            Files.createFile(filePath)
            val fileOutputStream = FileOutputStream(filePath.toFile())
            fileOutputStream.write(outputStream.getCachedByteArray())
        }
        return filePath.toFile()
    }

    override fun delete() {
        if (hasInitialized && !isInMemory()) {
            try {
                Files.deleteIfExists(filePath)
            } catch (e: NoSuchFileException) { // already deleted
            }
        }
    }

    private fun init() {
        if (!hasInitialized) {
            val nanoTime = measureNanoTime {
                source.copyTo(outputStream)
                hasInitialized = true
            }
            val size = outputStream.totalBytes
            Metrics.counter(ARTIFACT_UPLOADED_BYTES_COUNT).increment(size.toDouble())
            Metrics.counter(ARTIFACT_UPLOADED_CONSUME_COUNT).increment(nanoTime / 1000.0 / 1000.0)
            logger.info("Receive artifact file, size: ${HumanReadable.size(size)}, elapse: ${HumanReadable.time(nanoTime)}, " +
                "average: ${HumanReadable.throughput(size, nanoTime)}.")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OctetStreamArtifactFile::class.java)
    }
}
