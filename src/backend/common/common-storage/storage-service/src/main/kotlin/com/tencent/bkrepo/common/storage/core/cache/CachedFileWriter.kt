package com.tencent.bkrepo.common.storage.core.cache

import com.tencent.bkrepo.common.artifact.stream.StreamReadListener
import com.tencent.bkrepo.common.artifact.stream.closeQuietly
import com.tencent.bkrepo.common.artifact.stream.releaseQuietly
import com.tencent.bkrepo.common.storage.util.createFile
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

class CachedFileWriter(
    private val cachePath: Path,
    private val filename: String,
    tempPath: Path
): StreamReadListener {

    private val lockFilePath = tempPath.resolve(filename.plus(LOCK_SUFFIX))
    private val outputStream: FileOutputStream
    private val channel: FileChannel
    private var lock: FileLock? = null

    init {
        Files.createDirectories(tempPath)
        channel = FileChannel.open(lockFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        outputStream = lockFilePath.toFile().outputStream()
        try {
            lock = channel.tryLock()
            assert(lock != null)
        } catch (ignored: Exception) {
            outputStream.closeQuietly()
            channel.closeQuietly()
            lock = null
        }
    }

    override fun data(i: Int) {
        if (lock != null) {
            outputStream.write(i)
        }
    }

    override fun data(buffer: ByteArray, length: Int) {
        if (lock != null) {
            outputStream.write(buffer, 0, length)
        }
    }

    override fun close() {
        if (lock != null) {
            outputStream.flush()
            outputStream.closeQuietly()
            channel.closeQuietly()
            val cacheFilePath = cachePath.resolve(filename).apply { createFile() }
            Files.move(lockFilePath, cacheFilePath, StandardCopyOption.REPLACE_EXISTING)
            lock?.releaseQuietly()
            lock = null
        }
    }

    companion object {
        private const val LOCK_SUFFIX = ".lock"
    }
}