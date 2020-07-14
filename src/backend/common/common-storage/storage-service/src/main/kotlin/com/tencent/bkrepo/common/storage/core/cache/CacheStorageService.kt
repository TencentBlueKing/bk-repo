package com.tencent.bkrepo.common.storage.core.cache

import com.tencent.bkrepo.common.api.constant.StringPool.TEMP
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.bound
import com.tencent.bkrepo.common.storage.core.AbstractStorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.common.storage.filesystem.check.FileSynchronizeVisitor
import com.tencent.bkrepo.common.storage.filesystem.check.SynchronizeResult
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupResult
import java.io.InputStream
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

    @Resource
    private lateinit var taskAsyncExecutor: Executor

    override fun doStore(path: String, filename: String, artifactFile: ArtifactFile, credentials: StorageCredentials) {
        when {
            artifactFile.isInMemory() -> {
                fileStorage.store(path, filename, artifactFile.getInputStream(), artifactFile.getSize(), credentials)
            }
            artifactFile.isFallback() -> {
                fileStorage.store(path, filename, artifactFile.flushToFile(), credentials)
            }
            else -> {
                val cachedFile = getCacheClient(credentials).move(path, filename, artifactFile.flushToFile())
                taskAsyncExecutor.execute {
                    fileStorage.store(path, filename, cachedFile, credentials)
                }
            }
        }
    }

    override fun doLoad(path: String, filename: String, range: Range, credentials: StorageCredentials): InputStream? {
        val cacheClient = getCacheClient(credentials)
        return if (isLoadCacheFirst(range, credentials)) {
            val cachedFile = cacheClient.load(path, filename) ?: run {
                val newCacheFile = cacheClient.touch(path, filename)
                fileStorage.load(path, filename, newCacheFile, credentials) ?: run {
                    cacheClient.delete(path, filename)
                    null
                }
            }
            cachedFile?.bound(range)
        } else {
            fileStorage.load(path, filename, range, credentials) ?: run {
                getCacheClient(credentials).load(path, filename)?.bound(range)
            }
        }
    }

    override fun doDelete(path: String, filename: String, credentials: StorageCredentials) {
        fileStorage.delete(path, filename, credentials)
        getCacheClient(credentials).delete(path, filename)
    }

    override fun doExist(path: String, filename: String, credentials: StorageCredentials): Boolean {
        return fileStorage.exist(path, filename, credentials)
    }

    override fun getTempPath(credentials: StorageCredentials): String {
        return Paths.get(credentials.cache.path, TEMP).toString()
    }

    /**
     * 覆盖父类cleanUp逻辑，还包括清理缓存的文件内容
     */
    override fun cleanUp(storageCredentials: StorageCredentials?): CleanupResult {
        val credentials = getCredentialsOrDefault(storageCredentials)
        return getCacheClient(credentials).cleanUp(credentials.cache.expireDays)
    }

    override fun synchronizeFile(storageCredentials: StorageCredentials?): SynchronizeResult {
        val credentials = getCredentialsOrDefault(storageCredentials)
        val tempPath = Paths.get(credentials.cache.path, TEMP)
        val visitor = FileSynchronizeVisitor(tempPath, fileLocator, fileStorage, credentials)
        getCacheClient(credentials).walk(visitor)
        return visitor.checkResult
    }

    override fun doCheckHealth(credentials: StorageCredentials) {
        if (!monitor.health.get()) {
            throw IllegalStateException("Cache storage is unhealthy: ${monitor.reason}")
        }
        super.doCheckHealth(credentials)
    }

    /**
     * 判断是否优先从缓存加载数据
     * 判断规则:
     * 当cacheFirst开启，并且cache磁盘健康，并且当前文件未超过内存阈值大小
     */
    private fun isLoadCacheFirst(range: Range, credentials: StorageCredentials): Boolean {
        val isExceedThreshold = range.total > storageProperties.fileSizeThreshold.toBytes()
        val isHealth = if (credentials == storageProperties.defaultStorageCredentials()) {
            monitor.health.get()
        } else {
            true
        }
        val cacheFirst = credentials.cache.loadCacheFirst
        return cacheFirst && isHealth && isExceedThreshold
    }

    private fun getCacheClient(credentials: StorageCredentials): FileSystemClient {
        return FileSystemClient(credentials.cache.path)
    }
}
