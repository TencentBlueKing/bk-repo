package com.tencent.bkrepo.common.storage.filesystem

import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupFileVisitor
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupResult
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 本地文件存储客户端
 *
 * @author: carrypan
 * @date: 2019-09-18
 */
class FileSystemClient(private val root: String) {

    init {
        Files.createDirectories(Paths.get(root))
    }

    fun touch(dir: String, filename: String): File {
        val filePath = Paths.get(this.root, dir, filename)
        createDirectories(filePath.parent)
        if (!Files.exists(filePath)) {
            try {
                Files.createFile(filePath)
            } catch (exception: java.nio.file.FileAlreadyExistsException) {
                // ignore
            }
        }
        return filePath.toFile()
    }

    fun store(dir: String, filename: String, inputStream: InputStream, size: Long, overwrite: Boolean = false): File {
        val filePath = Paths.get(this.root, dir, filename)
        createDirectories(filePath.parent)
        if (overwrite) {
            Files.deleteIfExists(filePath)
        }
        val file = filePath.toFile()
        if (!Files.exists(filePath)) {
            file.createNewFile()
            FileLockExecutor.executeInLock(inputStream) { input ->
                FileLockExecutor.executeInLock(file) { output ->
                    transfer(input, output, size)
                }
            }
        }
        return file
    }

    fun delete(dir: String, filename: String) {
        val filePath = Paths.get(this.root, dir, filename)
        if (Files.isRegularFile(filePath)) {
            FileLockExecutor.executeInLock(filePath.toFile()) {
                Files.delete(filePath)
            }
        } else {
            throw IllegalArgumentException("[$filePath] is not a regular file.")
        }
    }

    fun load(dir: String, filename: String): File? {
        val filePath = Paths.get(this.root, dir, filename)
        return if (Files.isRegularFile(filePath)) filePath.toFile() else null
    }

    fun exist(dir: String, filename: String): Boolean {
        val filePath = Paths.get(this.root, dir, filename)
        return Files.isRegularFile(filePath)
    }

    fun append(dir: String, filename: String, inputStream: InputStream, size: Long): Long {
        val filePath = Paths.get(this.root, dir, filename)
        if (!Files.isRegularFile(filePath)) {
            throw IllegalArgumentException("[$filePath] is not a regular file.")
        }
        val file = filePath.toFile()
        FileLockExecutor.executeInLock(inputStream) { input ->
            FileLockExecutor.executeInLock(file) { output ->
                transfer(input, output, size, true)
            }
        }
        return Files.size(filePath)
    }

    fun createDirectory(dir: String, name: String) {
        val dirPath = Paths.get(this.root, dir, name)
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath)
        }
    }

    fun deleteDirectory(dir: String, name: String) {
        val filePath = Paths.get(this.root, dir, name)
        if (Files.isDirectory(filePath)) {
            Files.delete(filePath)
        } else {
            throw IllegalArgumentException("[$filePath] is not a directory.")
        }
    }

    fun checkDirectory(dir: String): Boolean {
        return Files.isDirectory(Paths.get(this.root, dir))
    }

    fun listFiles(path: String, extension: String): Collection<File> {
        return FileUtils.listFiles(File(this.root, path), arrayOf(extension), false)
    }

    fun mergeFiles(fileList: List<File>, outputFile: File): File {
        if (!outputFile.exists()) {
            if (!outputFile.createNewFile()) {
                throw IOException("Failed to create file [$outputFile]!")
            }
        }

        FileLockExecutor.executeInLock(outputFile) { output ->
            fileList.forEach { file ->
                FileLockExecutor.executeInLock(file.inputStream()) { input ->
                    transfer(input, output, file.length(), true)
                }
            }
        }
        return outputFile
    }

    /**
     * 清理文件
     */
    fun cleanUp(expireDays: Int): CleanupResult {
        return if (expireDays <= 0) {
            CleanupResult()
        } else {
            val rootPath = Paths.get(root)
            val visitor = CleanupFileVisitor(rootPath, expireDays)
            Files.walkFileTree(rootPath, visitor)
            visitor.cleanupResult
        }
    }

    fun transfer(input: ReadableByteChannel, output: FileChannel, size: Long, append: Boolean = false) {
        val startPosition: Long = if (append) output.size() else 0L
        var bytesCopied: Long
        var totalCopied = 0L
        var count: Long
        while (totalCopied < size) {
            val remain = size - totalCopied
            count = if (remain > FILE_COPY_BUFFER_SIZE) FILE_COPY_BUFFER_SIZE else remain
            bytesCopied = output.transferFrom(input, startPosition + totalCopied, count)
            if (bytesCopied == 0L) { // can happen if file is truncated after caching the size
                break
            }
            totalCopied += bytesCopied
        }
        if (totalCopied != size) {
            throw IOException("Failed to copy full contents. Expected length: $size, Actual: $totalCopied")
        }
    }

    private fun createDirectories(path: Path) {
        if (!Files.exists(path)) {
            Files.createDirectories(path)
        }
    }

    companion object {
        /**
         * OpenJdk中FileChannelImpl.java限定了单次传输大小:
         * private static final long MAPPED_TRANSFER_SIZE = 8L*1024L*1024L;
         *
         * 防止不同jdk版本的不同实现，这里限定一下大小
         */
        private const val FILE_COPY_BUFFER_SIZE = 64 * 1024 * 1024L
    }
}
