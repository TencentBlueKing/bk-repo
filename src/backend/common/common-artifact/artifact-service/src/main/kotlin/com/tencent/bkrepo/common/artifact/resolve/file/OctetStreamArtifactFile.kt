package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_UPLOADED_BYTES_COUNT
import com.tencent.bkrepo.common.artifact.metrics.ARTIFACT_UPLOADED_CONSUME_COUNT
import io.micrometer.core.instrument.Metrics
import org.apache.commons.fileupload.util.Streams
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.measureNanoTime

/**
 * application/octet-stream 流文件
 *
 * @author: carrypan
 * @date: 2019/10/30
 */
class OctetStreamArtifactFile(
    private val source: InputStream,
    private val location: String,
    private val fileSizeThreshold: Int,
    resolveLazily: Boolean = true
) : ArtifactFile {

    private lateinit var filePath: Path
    private lateinit var content: ByteArray
    private var hasInitialized: Boolean = false
    private var isInMemory: Boolean = false
    init {
        if (!resolveLazily) {
            init()
        }
    }

    private fun init() {
        if (!hasInitialized) {
            val nanoTime = measureNanoTime {
                filePath = Files.createTempFile(Paths.get(location), "artifact_", ".upload")
                val file = filePath.toFile()
                val fileOutputStream = FileOutputStream(file)
                val size = Streams.copy(source, fileOutputStream, true)
                if (size <= fileSizeThreshold) {
                    content = file.readBytes()
                    isInMemory = true
                }
                hasInitialized = true
            }
            val size = getSize()
            Metrics.counter(ARTIFACT_UPLOADED_BYTES_COUNT).increment(size.toDouble())
            Metrics.counter(ARTIFACT_UPLOADED_CONSUME_COUNT).increment(nanoTime / 1000.0 / 1000.0)
            logger.info("Receive artifact file, size: ${HumanReadable.bytes(size)}, elapse: ${HumanReadable.time(nanoTime)}, " +
                "average: ${HumanReadable.throughput(size, nanoTime)}.")
        }
    }

    override fun getInputStream(): InputStream {
        return if (isInMemory()) {
            ByteArrayInputStream(content)
        } else {
            BufferedInputStream(Files.newInputStream(filePath))
        }
    }

    override fun getSize(): Long {
        return if (isInMemory()) {
            content.size.toLong()
        } else {
            Files.size(filePath)
        }
    }

    override fun getFile(): File {
        init()
        return filePath.toFile()
    }

    override fun delete() {
        if (hasInitialized) {
            try {
                Files.delete(filePath)
            } catch (e: NoSuchFileException) { //already deleted
            }
        }
    }

    override fun isInMemory(): Boolean {
        init()
        return isInMemory
    }

    private val logger = LoggerFactory.getLogger(OctetStreamArtifactFile::class.java)
}
