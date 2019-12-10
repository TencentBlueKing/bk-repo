package com.tencent.bkrepo.common.storage.local

import com.google.common.io.ByteStreams
import com.tencent.bkrepo.common.storage.exception.StorageException
import com.tencent.bkrepo.common.storage.schedule.CleanupResult
import com.tencent.bkrepo.common.storage.util.FileMergeUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import org.apache.commons.io.FileUtils

/**
 * 本地文件存储客户端
 *
 * @author: carrypan
 * @date: 2019-09-18
 */
class LocalStorageClient(private val directory: String) {

    private val tempDirectory: String

    init {
        File(directory).mkdirs()
        tempDirectory = "$directory/temp"
        File(tempDirectory).mkdirs()
    }

    fun touch(path: String, filename: String): File {
        val subDirectory = File(directory, path)
        subDirectory.mkdirs()
        return File(subDirectory, filename)
    }

    fun store(path: String, filename: String, inputStream: InputStream, overwrite: Boolean = true): File {
        val subDirectory = File(directory, path)
        subDirectory.mkdirs()
        val file = File(subDirectory, filename)
        if (overwrite || !file.exists()) {
            file.outputStream().use { ByteStreams.copy(inputStream, it) }
        }
        return file
    }

    fun delete(path: String, filename: String) {
        val subDirectory = File(directory, path)
        val file = File(subDirectory, filename)
        if (file.isFile) {
            file.delete()
        }
    }

    fun load(path: String, filename: String): File? {
        val subDirectory = File(directory, path)
        val file = File(subDirectory, filename)
        return if (file.isFile) file else null
    }

    fun exist(path: String, filename: String): Boolean {
        val subDirectory = File(directory, path)
        val file = File(subDirectory, filename)
        return file.isFile
    }

    fun append(path: String, filename: String, inputStream: InputStream) {
        val subDirectory = File(tempDirectory, path)
        subDirectory.mkdirs()
        val file = File(subDirectory, filename)
        FileOutputStream(file, true).use {
            ByteStreams.copy(inputStream, it)
        }
    }

    fun makeBlockPath(path: String) {
        val subDirectory = File(tempDirectory, path)
        subDirectory.mkdirs()
    }

    fun checkBlockPath(path: String): Boolean {
        val subDirectory = File(tempDirectory, path)
        return subDirectory.isDirectory
    }

    fun deleteBlockPath(path: String) {
        val subDirectory = File(tempDirectory, path)
        FileUtils.deleteDirectory(subDirectory)
        if (!subDirectory.exists()) return
    }

    fun storeBlock(path: String, sequence: Int, sha256: String, inputStream: InputStream) {
        val subDirectory = File(tempDirectory, path)
        File(subDirectory, "$sequence$BLOCK_SUFFIX").outputStream().use { ByteStreams.copy(inputStream, it) }
        FileUtils.writeStringToFile(File(subDirectory, "$sequence$SHA256_SUFFIX"), sha256, Charset.defaultCharset())
    }

    fun combineBlock(path: String): File {
        val subDirectory = File(tempDirectory, path)
        val blockFileList = FileUtils.listFiles(subDirectory, arrayOf(BLOCK_EXTENSION), false).sortedBy { it.name.removeSuffix(BLOCK_SUFFIX).toInt() }
        // 校验
        if (blockFileList.isNullOrEmpty()) {
            throw StorageException("No file block was found.")
        }
        for (index in blockFileList.indices) {
            val sequence = index + 1
            if (blockFileList[index].name.removeSuffix(BLOCK_SUFFIX).toInt() != sequence) {
                throw StorageException("File block $sequence not found.")
            }
        }
        val combinedFile = File(subDirectory, COMBINED_FILENAME)
        FileMergeUtils.mergeFiles(blockFileList, combinedFile)
        return combinedFile
    }

    fun listBlockInfo(path: String): List<Pair<Long, String>> {
        val subDirectory = File(tempDirectory, path)
        if (!subDirectory.exists()) return emptyList()
        val blockFileList = FileUtils.listFiles(subDirectory, arrayOf(BLOCK_EXTENSION), false).sortedBy { it.name.removeSuffix(BLOCK_SUFFIX).toInt() }
        return blockFileList.map {
            val size = it.length()
            val sha256 = FileUtils.readFileToString(File(subDirectory, it.name.replace(BLOCK_SUFFIX, SHA256_SUFFIX)), Charset.defaultCharset())
            Pair(size, sha256)
        }
    }

    fun cleanup(expireSeconds: Long): CleanupResult {
        return doClean(directory, expireSeconds)
    }

    private fun doClean(path: String, expireSeconds: Long): CleanupResult {
        val result = CleanupResult(0, 0)
        if (expireSeconds <= 0) return result

        val directory = File(path)
        val files = directory.listFiles() ?: arrayOf()
        files.forEach {
            if (it.isFile && isExpired(it, expireSeconds)) {
                val fileSize = it.length()
                if (it.delete()) {
                    result.count += 1
                    result.size += fileSize
                }
            } else if (it.isDirectory) {
                val subResult = doClean(it.absolutePath, expireSeconds)
                result.count += subResult.count
                result.size += subResult.size
            }
        }

        return result
    }

    private fun isExpired(file: File, expireSeconds: Long): Boolean {
        val isFile = file.isFile
        val isExpired = (System.currentTimeMillis() - file.lastModified()) >= expireSeconds * 1000
        return isFile && isExpired
    }
    companion object {
        const val BLOCK_SUFFIX = ".block"
        const val BLOCK_EXTENSION = "block"
        const val SHA256_SUFFIX = ".sha256"
        const val COMBINED_FILENAME = "combined.data"
    }
}