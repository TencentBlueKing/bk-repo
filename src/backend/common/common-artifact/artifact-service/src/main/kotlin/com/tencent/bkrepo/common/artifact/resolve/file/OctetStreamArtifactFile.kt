package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
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
            val executionSeconds = measureNanoTime {
                filePath = Files.createTempFile(Paths.get(location), "artifact_", ".upload")
                val file = filePath.toFile()
                val fileOutputStream = FileOutputStream(file)
                Streams.copy(source, fileOutputStream, true)
                if (file.length() <= fileSizeThreshold) {
                    content = file.readBytes()
                    isInMemory = true
                }
                hasInitialized = true
            } / 1000.0 / 1000.0
            val kilobytes = getSize() / 1024.0
            logger.info("Receive ${kilobytes}KB, elapse: ${executionSeconds}s, average: ${kilobytes/executionSeconds}KB/s.")
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
