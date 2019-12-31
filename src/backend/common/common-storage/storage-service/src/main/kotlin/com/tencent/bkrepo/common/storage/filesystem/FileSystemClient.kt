package com.tencent.bkrepo.common.storage.filesystem

import com.google.common.io.ByteStreams
import com.tencent.bkrepo.common.storage.util.FileMergeUtils
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 本地文件存储客户端
 *
 * @author: carrypan
 * @date: 2019-09-18
 */
class FileSystemClient(private val root: String) {

    init {
        File(root).mkdirs()
    }

    fun touch(path: String, filename: String): File {
        val directory = File(this.root, path)
        val file = File(directory, filename)
        FileUtils.touch(file)
        return file
    }

    fun store(path: String, filename: String, inputStream: InputStream, overwrite: Boolean = true): File {
        val directory = File(this.root, path)
        directory.mkdirs()
        val file = File(directory, filename)
        if (overwrite || !file.exists()) {
            file.outputStream().use { output ->
                inputStream.use { input -> ByteStreams.copy(input, output) }
            }
        }
        return file
    }

    fun delete(path: String, filename: String) {
        val directory = File(this.root, path)
        val file = File(directory, filename)
        if (file.isFile) {
            file.delete()
        }
    }

    fun load(path: String, filename: String): File? {
        val directory = File(this.root, path)
        val file = File(directory, filename)
        return if (file.isFile) file else null
    }

    fun exist(path: String, filename: String): Boolean {
        val directory = File(this.root, path)
        val file = File(directory, filename)
        return file.isFile
    }

    fun append(path: String, filename: String, inputStream: InputStream) {
        val directory = File(this.root, path)
        val file = File(directory, filename)
        if (!file.isFile) {
            throw IllegalArgumentException("Append file does not exist.")
        }
        FileOutputStream(file, true).use { output ->
            inputStream.use { input -> ByteStreams.copy(input, output) }
        }
    }

    fun createDirectory(path: String, name: String) {
        val directory = File(this.root, path)
        File(directory, name).mkdirs()
    }

    fun deleteDirectory(path: String, name: String) {
        val directory = File(this.root, path)
        FileUtils.deleteDirectory(File(directory, name))
    }

    fun checkDirectory(path: String): Boolean {
        return File(this.root, path).isDirectory
    }

    fun listFiles(path: String, extension: String): Collection<File> {
        val directory = File(this.root, path)
        return FileUtils.listFiles(directory, arrayOf(extension), false)
    }

    fun mergeFiles(fileList: List<File>, outputFile: File): File {
        FileMergeUtils.mergeFiles(fileList, outputFile)
        return outputFile
    }
}
