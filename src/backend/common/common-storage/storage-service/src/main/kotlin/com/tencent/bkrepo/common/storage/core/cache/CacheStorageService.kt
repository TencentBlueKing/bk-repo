package com.tencent.bkrepo.common.storage.core.cache

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.storage.core.AbstractStorageService
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupResult
import org.springframework.beans.factory.annotation.Autowired
import java.io.File
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

    private val cacheClient: FileSystemClient by lazy { FileSystemClient(storageProperties.cache.path) }

    override fun doStore(path: String, filename: String, artifactFile: ArtifactFile, credentials: StorageCredentials) {
        val cachedFile = cacheClient.store(path, filename, artifactFile.getInputStream())
        fileStorage.store(path, filename, cachedFile, credentials)
    }

    override fun doStore(path: String, filename: String, file: File, credentials: StorageCredentials) {
        val cachedFile = cacheClient.store(path, filename, file.inputStream())
        fileStorage.store(path, filename, cachedFile, credentials)
    }

    override fun doLoad(path: String, filename: String, credentials: StorageCredentials): File? {
        return cacheClient.load(path, filename) ?: run {
            val cachedFile = cacheClient.touch(path, filename)
            fileStorage.load(path, filename, cachedFile, credentials)
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
        return Paths.get(storageProperties.cache.path, "temp").toString()
    }

    override fun cleanUp(): CleanupResult {
        return cacheClient.cleanUp(storageProperties.cache.expireDays)
    }
}
