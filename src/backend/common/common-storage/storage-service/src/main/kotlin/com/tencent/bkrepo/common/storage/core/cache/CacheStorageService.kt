package com.tencent.bkrepo.common.storage.core.cache

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.bound
import com.tencent.bkrepo.common.storage.core.AbstractStorageService
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.common.storage.filesystem.check.FileSynchronizeVisitor
import com.tencent.bkrepo.common.storage.filesystem.check.SynchronizeResult
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupResult
import org.springframework.beans.factory.annotation.Autowired
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executor
import javax.annotation.Resource

/**
 * 支持缓存的存储服务
 *
 * @author: carrypan
 * @date: 2019/12/26
 */
class CacheStorageService : AbstractStorageService() {

    @Autowired
    private lateinit var storageProperties: StorageProperties

    @Resource
    private lateinit var taskAsyncExecutor: Executor

    private val tempPath: Path by lazy { Paths.get(storageProperties.cache.path, temp) }

    private val cacheClient: FileSystemClient by lazy { FileSystemClient(storageProperties.cache.path) }

    override fun doStore(path: String, filename: String, artifactFile: ArtifactFile, credentials: StorageCredentials) {
        when {
            artifactFile.isInMemory() -> {
                fileStorage.store(path, filename, artifactFile.getInputStream(), credentials)
            }
            artifactFile.isFallback() -> {
                fileStorage.store(path, filename, artifactFile.flushToFile(), credentials)
            }
            else -> {
                val cachedFile = cacheClient.store(path, filename, artifactFile.flushToFile())
                taskAsyncExecutor.execute {
                    fileStorage.store(path, filename, cachedFile, credentials)
                }
            }
        }
    }

    override fun doLoad(path: String, filename: String, range: Range, credentials: StorageCredentials): InputStream? {
        return if (!isExceedThreshold(range) || !monitor.health.get()) {
            fileStorage.load(path, filename, range, credentials) ?: run {
                cacheClient.load(path, filename)?.bound(range)
            }
        } else {
            val cachedFile = cacheClient.load(path, filename) ?: run {
                cacheClient.touch(path, filename).let {
                    fileStorage.load(path, filename, it, credentials) ?: run {
                        Files.deleteIfExists(it.toPath())
                        null
                    }
                }
            }
            cachedFile?.bound(range)
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

    override fun getTempPath() = tempPath.toString()

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
        if (!monitor.health.get()) {
            throw RuntimeException("Cache storage is unhealthy: ${monitor.reason}")
        }
        super.doCheckHealth(credentials)
    }

    private fun isExceedThreshold(range: Range): Boolean {
        return range.total > monitor.uploadConfig.fileSizeThreshold.toBytes()
    }

    companion object {
        const val temp = "temp"
    }
}
