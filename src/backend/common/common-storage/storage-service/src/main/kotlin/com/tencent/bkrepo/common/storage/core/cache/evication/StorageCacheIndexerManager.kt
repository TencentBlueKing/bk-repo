/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.common.storage.core.cache.evication

import com.tencent.bkrepo.common.storage.core.cache.CacheStorageService
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.cleanup.event.FileDeletedEvent
import com.tencent.bkrepo.common.storage.filesystem.cleanup.event.FileSurvivedEvent
import com.tencent.bkrepo.common.storage.util.toPath
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * 磁盘缓存索引管理器，用于记录缓存访问情况，在缓存达到限制大小时淘汰存储层缓存文件
 */
open class StorageCacheIndexerManager(
    private val cacheFactory: StorageCacheIndexerFactory<String, Long>,
    private val storageService: CacheStorageService,
    private val fileLocator: FileLocator,
    private val storageCacheIndexProperties: StorageCacheIndexProperties,
) {

    private val storageCacheMap = ConcurrentHashMap<String, StorageCacheIndexer<String, Long>>()

    /**
     * 存储层缓存文件被删除时回调，同时删除淘汰策略内维护的缓存索引
     *
     * @param credentials 缓存所在的存储
     * @param sha256 被删除的缓存文件的sha256
     */
    @Async
    open fun onCacheDeleted(credentials: StorageCredentials, sha256: String) {
        if (storageCacheIndexProperties.enabled) {
            logger.info("remove cache of [$sha256], storage[${credentials.key}]")
            getOrCreateIndexer(credentials).remove(sha256)
        }
    }

    /**
     * 缓存文件在清理任务中存活时调用，用于添加缺失的缓存索引到淘汰策略淘汰内
     *
     * @param credentials 缓存文件所在的存储
     * @param sha256 被删除的缓存文件的sha256
     * @param size 缓存文件大小
     * @param score 缓存索引优先级，用于缓存淘汰决策
     */
    @Async
    open fun onCacheReserved(credentials: StorageCredentials, sha256: String, size: Long, score: Double) {
        if (storageCacheIndexProperties.enabled) {
            val strategy = getOrCreateIndexer(credentials)
            if (!strategy.containsKey(sha256)) {
                logger.info("file[$sha256] of credential[${credentials.key}] will be put into strategy")
                strategy.put(sha256, size, score)
            }
        }
    }

    /**
     *  缓存文件被访问时的回调，记录访问信息，用于缓存淘汰决策
     *
     *  @param credentials 缓存文件所在存储
     *  @param sha256 缓存文件sha256
     *  @param size 缓存文件大小
     */
    @Async
    open fun onCacheAccessed(credentials: StorageCredentials, sha256: String, size: Long) {
        if (!storageCacheIndexProperties.enabled) {
            return
        }
        logger.info("cache file accessed, sha256[$sha256], storage[${credentials.key}], size[$size]")
        getOrCreateIndexer(credentials).put(sha256, size)
    }

    /**
     * 同步淘汰策略内维护的索引与实际缓存文件
     */
    open fun sync(credentials: StorageCredentials) {
        if (storageCacheIndexProperties.enabled) {
            logger.info("start sync ${credentials.key}")
            getOrCreateIndexer(credentials).sync()
        }
    }

    @Async
    @EventListener(FileDeletedEvent::class)
    open fun onFileDeleted(event: FileDeletedEvent) {
        if (event.rootPath.toPath() == event.credentials.cache.path.toPath()) {
            onCacheDeleted(event.credentials, event.sha256)
        }
    }

    @Async
    @EventListener(FileSurvivedEvent::class)
    open fun onFileSurvived(event: FileSurvivedEvent) {
        if (event.rootPath.toPath() == event.credentials.cache.path.toPath()) {
            val attributes = Files.readAttributes(event.fullPath.toPath(), BasicFileAttributes::class.java)
            val lastAccessTime = attributes.lastAccessTime().toMillis()
            val size = attributes.size()
            onCacheReserved(event.credentials, event.sha256, size, lastAccessTime.toDouble())
        }
    }

    private fun getOrCreateIndexer(credentials: StorageCredentials): StorageCacheIndexer<String, Long> {
        val cache = storageCacheMap.getOrPut(credentials.cache.path) {
            val listener = StorageEldestRemovedListener(credentials, fileLocator, storageService)
            cacheFactory.create(credentials.cache).apply { addEldestRemovedListener(listener) }
        }
        // 获取策略时检查是否需要更新缓存淘汰策略配置
        refreshCacheProperties(cache, credentials)
        return cache
    }

    private fun refreshCacheProperties(
        cache: StorageCacheIndexer<String, Long>,
        credentials: StorageCredentials
    ) {
        cache.setMaxWeight(credentials.cache.maxSize)
        cache.getEldestRemovedListeners().forEach {
            if (it is StorageEldestRemovedListener) {
                it.setCredentials(credentials)
            }
        }
    }

    /**
     * 缓存淘汰监听器，缓存索引被淘汰时同时删除硬盘上的缓存文件
     */
    private class StorageEldestRemovedListener(
        @Volatile
        private var storageCredentials: StorageCredentials,
        private val fileLocator: FileLocator,
        private val storageService: CacheStorageService,
    ) : EldestRemovedListener<String, Long> {
        override fun onEldestRemoved(key: String, value: Long) {
            val path = fileLocator.locate(key)
            storageService.deleteCacheFile(path, key, storageCredentials)
        }

        fun setCredentials(credentials: StorageCredentials) {
            this.storageCredentials = credentials
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StorageCacheIndexerManager::class.java)
    }
}
