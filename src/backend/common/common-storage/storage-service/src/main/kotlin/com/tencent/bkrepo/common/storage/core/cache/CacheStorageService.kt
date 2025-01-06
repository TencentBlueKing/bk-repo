/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.storage.core.cache

import com.tencent.bkrepo.common.api.constant.StringPool.TEMP
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream.Companion.METADATA_KEY_CACHE_ENABLED
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.storage.core.AbstractStorageService
import com.tencent.bkrepo.common.storage.core.cache.event.CacheFileEventPublisher
import com.tencent.bkrepo.common.storage.core.cache.event.CacheFileLoadedEventPublisher
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.FileSystemClient
import com.tencent.bkrepo.common.storage.filesystem.check.FileSynchronizeVisitor
import com.tencent.bkrepo.common.storage.filesystem.check.SynchronizeResult
import com.tencent.bkrepo.common.storage.filesystem.cleanup.BasedAtimeAndMTimeFileExpireResolver
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupFileVisitor
import com.tencent.bkrepo.common.storage.filesystem.cleanup.CleanupResult
import com.tencent.bkrepo.common.storage.filesystem.cleanup.FileRetainResolver
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 支持缓存的存储服务
 */
class CacheStorageService(
    private val threadPoolTaskExecutor: ThreadPoolTaskExecutor,
    private val fileRetainResolver: FileRetainResolver? = null,
) : AbstractStorageService() {

    private val cacheFileEventPublisher by lazy { CacheFileEventPublisher(publisher) }

    override fun doStore(
        path: String,
        filename: String,
        artifactFile: ArtifactFile,
        credentials: StorageCredentials,
        storageClass: String?,
    ) {
        when {
            artifactFile.isInMemory() -> {
                fileStorage.store(
                    path,
                    filename,
                    artifactFile.getInputStream(),
                    artifactFile.getSize(),
                    credentials,
                )
            }

            artifactFile.isFallback() || artifactFile.isInLocalDisk() -> {
                fileStorage.store(path, filename, artifactFile.flushToFile(), credentials, storageClass)
            }

            else -> {
                val cacheFile = getCacheClient(credentials).move(path, filename, artifactFile.flushToFile())
                cacheFileEventPublisher.publishCacheFileLoadedEvent(credentials, cacheFile)
                async2Store(filename, credentials, path, cacheFile, storageClass)
            }
        }
    }

    private fun async2Store(
        filename: String,
        credentials: StorageCredentials,
        path: String,
        cacheFile: File,
        storageClass: String?,
    ) {
        threadPoolTaskExecutor.execute {
            try {
                fileStorage.store(path, filename, cacheFile, credentials, storageClass)
            } catch (ignored: Exception) {
                // 此处为异步上传，失败后异常不会被外层捕获，所以单独捕获打印error日志
                logger.error("Failed to async store file [$filename] on [${credentials.key}]", ignored)
                // 失败时把文件放入暂存区，后台任务会进行补偿。
                stagingFile(credentials, path, filename, cacheFile)
            }
        }
    }

    override fun doLoad(
        path: String,
        filename: String,
        range: Range,
        credentials: StorageCredentials,
    ): ArtifactInputStream? {
        val cacheClient = getCacheClient(credentials)
        val loadCacheFirst = isLoadCacheFirst(range, credentials)
        if (loadCacheFirst) {
            loadArtifactStreamFromCache(cacheClient, path, filename, range)?.let {
                cacheFileEventPublisher.publishCacheFileAccessEvent(path, filename, range.total!!, credentials)
                return it.apply { putMetadata(METADATA_KEY_CACHE_ENABLED, true) }
            }
        }
        val artifactInputStream = fileStorage.load(path, filename, range, credentials)?.artifactStream(range)
        if (artifactInputStream != null && loadCacheFirst && range.isFullContent()) {
            val cachePath = Paths.get(credentials.cache.path, path)
            val tempPath = Paths.get(credentials.cache.path, TEMP)
            val cacheFileLoadedEventPublisher = CacheFileLoadedEventPublisher(publisher, credentials)
            val readListener = CachedFileWriter(cachePath, filename, tempPath, cacheFileLoadedEventPublisher)
            artifactInputStream.addListener(readListener)
        }

        return if (artifactInputStream == null && !loadCacheFirst) {
            cacheClient.load(path, filename)?.artifactStream(range)
        } else {
            artifactInputStream
        }?.apply { putMetadata(METADATA_KEY_CACHE_ENABLED, loadCacheFirst) }
    }

    override fun doDelete(path: String, filename: String, credentials: StorageCredentials) {
        val cacheFilePath = "${credentials.cache.path}$path$filename"
        val size = File(cacheFilePath).length()
        fileStorage.delete(path, filename, credentials)
        getCacheClient(credentials).delete(path, filename)
        cacheFileEventPublisher.publishCacheFileDeletedEvent(path, filename, size, credentials)
    }

    override fun doExist(path: String, filename: String, credentials: StorageCredentials): Boolean {
        return fileStorage.exist(path, filename, credentials)
    }

    override fun doCheckRestore(path: String, filename: String, credentials: StorageCredentials): Boolean {
        return fileStorage.checkRestore(path, filename, credentials)
    }

    override fun doRestore(path: String, filename: String, days: Int, tier: String, credentials: StorageCredentials) {
        fileStorage.restore(path, filename, days, tier, credentials)
    }

    /**
     * 覆盖父类cleanUp逻辑，还包括清理缓存的文件内容
     */
    override fun cleanUp(storageCredentials: StorageCredentials?): Map<Path, CleanupResult> {
        val credentials = getCredentialsOrDefault(storageCredentials)
        val rootPath = Paths.get(credentials.cache.path)
        val tempPath = getTempPath(credentials)
        val stagingPath = getStagingPath(credentials)
        val resolver = BasedAtimeAndMTimeFileExpireResolver(credentials.cache.expireDuration)
        val visitor = CleanupFileVisitor(
            rootPath,
            tempPath,
            stagingPath,
            fileStorage,
            fileLocator,
            credentials,
            resolver,
            publisher,
            fileRetainResolver
        )
        getCacheClient(credentials).walk(visitor)
        val result = mutableMapOf<Path, CleanupResult>()
        result[rootPath] = visitor.result
        result.putAll(cleanUploadPath(credentials))
        return result
    }

    override fun synchronizeFile(storageCredentials: StorageCredentials?): SynchronizeResult {
        val credentials = getCredentialsOrDefault(storageCredentials)
        val rootPath = Paths.get(credentials.cache.path, STAGING)
        val visitor = FileSynchronizeVisitor(rootPath, fileLocator, fileStorage, credentials)
        getStagingClient(credentials).walk(visitor)
        return visitor.result
    }

    override fun doCheckHealth(credentials: StorageCredentials) {
        val monitor = getMonitor(credentials)
        if (!monitor.healthy.get()) {
            throw IllegalStateException("Cache storage is unhealthy: ${monitor.fallBackReason}")
        }
        super.doCheckHealth(credentials)
    }

    override fun copy(
        digest: String,
        fromCredentials: StorageCredentials?,
        toCredentials: StorageCredentials?
    ) {
        val path = fileLocator.locate(digest)
        val from = getCredentialsOrDefault(fromCredentials)
        val to = getCredentialsOrDefault(toCredentials)
        if (doExist(path, digest, from)) {
            super.copy(digest, from, to)
        } else {
            val cacheFile = getCacheClient(from).load(path, digest)
                ?: throw FileNotFoundException(Paths.get(from.cache.path, path, digest).toString())
            fileStorage.store(path, digest, cacheFile, to)
        }
    }

    /**
     * 删除缓存文件，需要检查文件是否已经在最终存储中存在，避免将未上传成功的制品删除导致数据丢失
     */
    fun deleteCacheFile(
        path: String,
        filename: String,
        credentials: StorageCredentials,
    ): Boolean {
        val cacheFilePath = "${credentials.cache.path}$path$filename"
        return if (doExist(path, filename, credentials)) {
            val size = File(cacheFilePath).length()
            getCacheClient(credentials).delete(path, filename)
            cacheFileEventPublisher.publishCacheFileDeletedEvent(path, filename, size, credentials)
            logger.info("Cache [$cacheFilePath] was deleted")
            true
        } else {
            logger.info("Cache file[$cacheFilePath] was not in storage")
            false
        }
    }

    /**
     * 判断缓存文件是否存在
     */
    fun cacheExists(
        path: String,
        filename: String,
        credentials: StorageCredentials,
    ): Boolean {
        return getCacheClient(credentials).exist(path, filename)
    }

    /**
     * 获取存储的缓存目录健康状态
     */
    fun cacheHealthy(credentials: StorageCredentials?): Boolean {
        return getMonitor(getCredentialsOrDefault(credentials)).healthy.get()
    }

    /**
     * 从缓存中加载构件。
     *
     * 生成一个构件流需要两个步骤,判断文件是否存在，存在则新建一个文件流。
     * 因为两个步骤所以存在可能，判断时文件存在，但是新建一个文件流时，文件被移除。
     * 所以这里需要将两个步骤合在一起，以是否成功新建文件流为最终结果，如果这过程中文件被移除，
     * 则认为加载失败，返回null。
     *
     * @param cacheClient 缓存客户端
     * @param path 文件路径
     * @param filename 文件名
     * @param range 加载文件范围
     * @return 成功返回构件流，否则返回null
     * */
    private fun loadArtifactStreamFromCache(
        cacheClient: FileSystemClient,
        path: String,
        filename: String,
        range: Range,
    ): ArtifactInputStream? {
        try {
            return cacheClient.load(path, filename)?.artifactStream(range)
        } catch (e: Exception) {
            logger.warn("Can't load file from cache.", e)
        }
        return null
    }

    /**
     * 判断是否优先从缓存加载数据
     * 判断规则:
     * 当cacheFirst开启，并且cache磁盘健康，并且当前文件未超过内存阈值大小
     */
    private fun isLoadCacheFirst(range: Range, credentials: StorageCredentials): Boolean {
        val total = range.total ?: return false
        val isExceedThreshold = total > storageProperties.receive.fileSizeThreshold.toBytes()
        val isHealth = getMonitor(credentials).healthy.get()
        val cacheFirst = credentials.cache.loadCacheFirst
        return cacheFirst && isHealth && isExceedThreshold
    }

    private fun getMonitor(credentials: StorageCredentials): StorageHealthMonitor {
        return monitorHelper.getMonitor(storageProperties, credentials)
    }

    private fun getCacheClient(credentials: StorageCredentials): FileSystemClient {
        return FileSystemClient(credentials.cache.path)
    }

    private fun getStagingClient(credentials: StorageCredentials): FileSystemClient {
        return FileSystemClient(getStagingPath(credentials))
    }

    private fun getStagingPath(credentials: StorageCredentials): Path {
        return Paths.get(credentials.cache.path, STAGING)
    }

    private fun stagingFile(credentials: StorageCredentials, path: String, filename: String, file: File) {
        try {
            getStagingClient(credentials).createLink(path, filename, file)
        } catch (e: Exception) {
            logger.error("Create staging file link failed.", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CacheStorageService::class.java)
        private const val STAGING = "staging"
    }
}
