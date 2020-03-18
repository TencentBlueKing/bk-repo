package com.tencent.bkrepo.common.storage.filesystem

import com.google.common.io.ByteStreams
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupFileVisitor
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupResult
import com.tencent.bkrepo.common.storage.util.FileMergeUtils
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

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
        return Files.createFile(filePath).toFile()
    }

    fun store(dir: String, filename: String, inputStream: InputStream, overwrite: Boolean = true): File {
        val filePath = Paths.get(this.root, dir, filename)
        createDirectories(filePath.parent)
        if (overwrite || !Files.exists(filePath)) {
            inputStream.use { Files.copy(it, filePath, StandardCopyOption.REPLACE_EXISTING) }
        }
        return filePath.toFile()
    }

    fun delete(dir: String, filename: String) {
        val filePath = Paths.get(this.root, dir, filename)
        if (Files.isRegularFile(filePath)) Files.delete(filePath)
    }

    fun load(dir: String, filename: String): File? {
        val filePath = Paths.get(this.root, dir, filename)
        return if (Files.isRegularFile(filePath)) filePath.toFile() else null
    }

    fun exist(dir: String, filename: String): Boolean {
        val filePath = Paths.get(this.root, dir, filename)
        return Files.isRegularFile(filePath)
    }

    fun append(dir: String, filename: String, inputStream: InputStream): Long {
        val filePath = Paths.get(this.root, dir, filename)
        if (!Files.isRegularFile(filePath)) {
            throw IllegalArgumentException("Append file does not exist.")
        }
        FileOutputStream(filePath.toFile(), true).use { output ->
            inputStream.use { input -> ByteStreams.copy(input, output) }
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
        val directory = File(this.root, dir)
        FileUtils.deleteDirectory(File(directory, name))
    }

    fun checkDirectory(dir: String): Boolean {
        return Files.isDirectory(Paths.get(this.root, dir))
    }

    fun listFiles(path: String, extension: String): Collection<File> {
        return FileUtils.listFiles(File(this.root, path), arrayOf(extension), false)
    }

    fun mergeFiles(fileList: List<File>, outputFile: File): File {
        FileMergeUtils.mergeFiles(fileList, outputFile)
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

    private fun createDirectories(path: Path) {
        if (!Files.exists(path)) {
            Files.createDirectories(path)
        }
    }

    companion object {
        fun copy(src: File, dest: File) = FileUtils.copyFile(src, dest)
    }
}
