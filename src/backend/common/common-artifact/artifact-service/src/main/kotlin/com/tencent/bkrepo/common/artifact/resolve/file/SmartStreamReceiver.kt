package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.exception.ArtifactReceiveException
import com.tencent.bkrepo.common.artifact.stream.StreamReceiveListener
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import com.tencent.bkrepo.common.storage.monitor.Throughput
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureNanoTime

class SmartStreamReceiver(
    private val fileSizeThreshold: Int,
    private val filename: String,
    private var path: Path,
    private val enableTransfer: Boolean
) : StorageHealthMonitor.Observer {
    private val contentBytes = ByteArrayOutputStream(DEFAULT_BUFFER_SIZE)
    private var outputStream: OutputStream = contentBytes
    private var hasTransferred: Boolean = false
    private var fallBackPath: Path? = null

    var isInMemory: Boolean = true
    var totalSize: Long = 0
    var fallback: Boolean = false

    fun receive(source: InputStream, listener: StreamReceiveListener): Throughput {
        try {
            var bytesCopied: Long = 0
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val nanoTime = measureNanoTime {
                var bytes = source.read(buffer)
                while (bytes >= 0) {
                    checkFallback()
                    outputStream.write(buffer, 0, bytes)
                    listener.data(buffer, 0, bytes)
                    bytesCopied += bytes
                    checkThreshold(bytesCopied)
                    bytes = source.read(buffer)
                }
            }
            totalSize = bytesCopied
            listener.finished()
            return Throughput(bytesCopied, nanoTime)
        } catch (exception: IOException) {
            cleanTempFile()
            val message = exception.message.orEmpty()
            if (message.contains("Remote peer closed connection") ||
                message.contains("Connection reset by peer")
            ) {
                throw ArtifactReceiveException(message)
            } else {
                throw exception
            }
        } finally {
            try {
                source.close()
            } catch (ignored: Exception) {
            }
            try {
                outputStream.close()
            } catch (ignored: Exception) {
            }
        }
    }

    fun getCachedByteArray(): ByteArray = contentBytes.toByteArray()

    fun getFilePath(): Path = path.resolve(filename)

    @Synchronized
    fun flushToFile(closeStream: Boolean = true) {
        if (isInMemory) {
            val filePath = path.resolve(filename).apply { Files.createFile(this) }
            val fileOutputStream = Files.newOutputStream(filePath)
            contentBytes.writeTo(fileOutputStream)
            outputStream = fileOutputStream
            isInMemory = false

            if (closeStream) {
                try {
                    outputStream.close()
                } catch (e: Exception) {
                }
            }
        }
    }

    override fun unhealthy(fallbackPath: Path?, reason: String?) {
        if (!fallback) {
            fallBackPath = fallbackPath
            fallback = true
            logger.warn("Path[$path] is unhealthy, fallback to use [$fallBackPath], reason: $reason")
        }
    }

    private fun checkFallback() {
        if (fallback && !hasTransferred) {
            if (!enableTransfer) {
                if (fallBackPath != null && fallBackPath != path) {
                    val originalPath = path
                    path = fallBackPath!!
                    // transfer date
                    if (!isInMemory) {
                        val originalFile = originalPath.resolve(filename)
                        val filePath = path.resolve(filename).apply { Files.createFile(this) }
                        originalFile.toFile().inputStream().use { input ->
                            filePath.toFile().outputStream().use { output -> input.copyTo(output) }
                        }
                        Files.deleteIfExists(originalFile)
                        logger.info("Success to transfer data from [$originalPath] to [$path]")
                    }
                } else {
                    logger.info("Fallback path is null or equals to primary path, ignore transfer data")
                }
            }
            hasTransferred = true
        }
    }

    private fun checkThreshold(bytesCopied: Long) {
        if (isInMemory && bytesCopied > fileSizeThreshold) {
            flushToFile(false)
        }
    }

    private fun cleanTempFile() {
        if (!isInMemory) {
            try {
                outputStream.close()
                Files.deleteIfExists(path.resolve(filename))
            } catch (ignored: Exception) {
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SmartStreamReceiver::class.java)
    }
}
