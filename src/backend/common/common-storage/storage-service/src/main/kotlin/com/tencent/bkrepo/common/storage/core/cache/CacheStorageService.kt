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
import org.apache.commons.lang.RandomStringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.util.unit.DataSize
import java.io.InputStream
import java.nio.charset.Charset
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

    @Value("\${upload.fileSizeThreshold}")
    private lateinit var fileSizeThreshold: DataSize

    private val tempPath: Path by lazy { Paths.get(storageProperties.cache.path, temp) }

    private val cacheClient: FileSystemClient by lazy { FileSystemClient(storageProperties.cache.path) }

    override fun doStore(path: String, filename: String, artifactFile: ArtifactFile, credentials: StorageCredentials) {
        if (storageProperties.cache.useThreshold && artifactFile.isInMemory()) {
            fileStorage.store(path, filename, artifactFile.getInputStream(), credentials)
        } else {
            val cachedFile = cacheClient.store(path, filename, artifactFile.flushToFile())
            taskAsyncExecutor.execute {
                fileStorage.store(path, filename, cachedFile, credentials)
            }
        }
    }

    override fun doLoad(path: String, filename: String, range: Range, credentials: StorageCredentials): InputStream? {
        return if (storageProperties.cache.useThreshold && !isExceedThreshold(range)) {
            fileStorage.load(path, filename, range, credentials) ?: run {
                cacheClient.load(path, filename)?.bound(range)
            }
        } else {
            val cachedFile = cacheClient.load(path, filename) ?: run {
                cacheClient.touch(path, filename).let {
                    fileStorage.load(path, filename, it, credentials) ?: run {
                        it.deleteOnExit()
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
        val filename = System.nanoTime().toString()
        val size = 100

        val content = RandomStringUtils.randomAlphabetic(size)
        try {
            // write
            cacheClient.store(HEALTH_CHECK_PATH, filename, content.byteInputStream(), size.toLong(), true)
            fileStorage.store(HEALTH_CHECK_PATH, filename, content.byteInputStream(), credentials)
            // read
            val cachedFile = cacheClient.load(HEALTH_CHECK_PATH, filename)
            val loadedContent = fileStorage.load(HEALTH_CHECK_PATH, filename, Range.ofFull(size.toLong()), credentials)
                ?.readBytes()?.toString(Charset.defaultCharset()).orEmpty()
            // check
            assert(cachedFile != null) { "Failed to load cached file." }
            assert(content == loadedContent) { "File content inconsistent." }
            assert(content == cachedFile!!.readText()) { "File content inconsistent." }
        } catch (exception: Exception) {
            throw exception
        } finally {
            // delete
            cacheClient.delete(HEALTH_CHECK_PATH, filename)
            fileStorage.delete(HEALTH_CHECK_PATH, filename, credentials)
        }
    }

    private fun isExceedThreshold(range: Range): Boolean {
        return range.total > fileSizeThreshold.toBytes()
    }

    companion object {
        const val temp = "temp"
    }
}
