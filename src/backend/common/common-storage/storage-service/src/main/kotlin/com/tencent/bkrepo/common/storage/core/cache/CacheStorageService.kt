package com.tencent.bkrepo.common.storage.core.cache

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.storage.core.AbstractStorageService
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.common.storage.filesystem.check.FileSynchronizeVisitor
import com.tencent.bkrepo.common.storage.filesystem.check.SynchronizeResult
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupResult
import org.apache.commons.lang.RandomStringUtils
import org.springframework.beans.factory.annotation.Autowired
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 支持缓存的存储服务
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
class CacheStorageService : AbstractStorageService() {

    @Autowired
    private lateinit var storageProperties: StorageProperties

    private val tempPath: Path by lazy { Paths.get(storageProperties.cache.path, temp) }

    private val cacheClient: FileSystemClient by lazy { FileSystemClient(storageProperties.cache.path) }

    override fun doStore(path: String, filename: String, artifactFile: ArtifactFile, credentials: StorageCredentials) {
        val cachedFile = cacheClient.store(path, filename, artifactFile.getInputStream(), artifactFile.getSize())
        fileStorage.store(path, filename, cachedFile, credentials)
    }

    override fun doStore(path: String, filename: String, file: File, credentials: StorageCredentials) {
        val cachedFile = cacheClient.store(path, filename, file.inputStream(), file.length())
        fileStorage.store(path, filename, cachedFile, credentials)
    }

    override fun doLoad(path: String, filename: String, credentials: StorageCredentials): File? {
        return cacheClient.load(path, filename) ?: run {
            val cachedFile = cacheClient.touch(path, filename)
            fileStorage.load(path, filename, cachedFile, credentials) ?: run {
                cachedFile.deleteOnExit()
                null
            }
        }
    }

    override fun doDelete(path: String, filename: String, credentials: StorageCredentials) {
        fileStorage.delete(path, filename, credentials)
        cacheClient.delete(path, filename)
    }

    override fun doExist(path: String, filename: String, credentials: StorageCredentials): Boolean {
        return fileStorage.exist(path, filename, credentials)
    }

    override fun doManualRetry(path: String, filename: String, credentials: StorageCredentials) {
        val cachedFile = cacheClient.load(path, filename) ?: throw RuntimeException("File [$filename] is missing.")
        fileStorage.store(path, filename, cachedFile, credentials)
    }

    override fun getTempPath(): String? {
        return tempPath.toString()
    }

    override fun cleanUp(): CleanupResult {
        return cacheClient.cleanUp(storageProperties.cache.expireDays)
    }

    override fun synchronizeFile(storageCredentials: StorageCredentials?): SynchronizeResult {
        val credentials = getCredentialsOrDefault(storageCredentials)
        val visitor = FileSynchronizeVisitor(tempPath, fileLocator, fileStorage, credentials)
        cacheClient.walk(visitor)
        return visitor.checkResult
    }

    override fun doCheckHealth(credentials: StorageCredentials) {
        val path = "health-check"
        val filename = System.nanoTime().toString()
        val randomSize = 10

        val content = RandomStringUtils.randomAlphabetic(randomSize)
        var receiveFile: File? = null
        try {
            receiveFile = File.createTempFile(path, filename)
            // 写文件
            val file = cacheClient.store(path, filename, content.byteInputStream(), randomSize.toLong(), true)
            fileStorage.synchronizeStore(path, filename, file, credentials)
            // 读文件
            val cachedFile = cacheClient.load(path, filename)
            fileStorage.load(path, filename, receiveFile, credentials)
            assert(cachedFile != null) { "Failed to load cached file." }
            assert(receiveFile != null) { "Failed to load file." }
            assert(content == receiveFile!!.readText()) {"File content inconsistent."}
            assert(content == cachedFile!!.readText()) {"File content inconsistent."}
            // 删除文件
            cacheClient.delete(path, filename)
            fileStorage.delete(path, filename, credentials)
        } catch (exception: Exception) {
            throw exception
        } finally {
            receiveFile?.deleteOnExit()
        }
    }

    companion object {
        const val temp = "temp"
    }
}
