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
import com.tencent.bkrepo.common.storage.filesystem.cleanup.event.FileReservedEvent
import com.tencent.bkrepo.common.storage.util.toPath
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap

open class DefaultStorageCacheEvictor(
    private val cacheFactory: StrategyFactory<String, Long>,
    private val storageService: CacheStorageService,
    private val fileLocator: FileLocator,
    private val storageCacheEvictionProperties: StorageCacheEvictionProperties,
) : StorageCacheEvictor {

    private val storageCacheMap = ConcurrentHashMap<String, StorageCacheEvictStrategy<String, Long>>()

    @Async
    override fun onCacheDeleted(credentials: StorageCredentials, sha256: String) {
        if (storageCacheEvictionProperties.enabled) {
            logger.info("remove cache of [$sha256], storage[${credentials.key}]")
            getStrategy(credentials).remove(sha256)
        }
    }


    @Async
    override fun onCacheReserved(credentials: StorageCredentials, sha256: String, size: Long, score: Double) {
        if (storageCacheEvictionProperties.enabled) {
            val strategy = getStrategy(credentials)
            if (!strategy.containsKey(sha256)) {
                strategy.put(sha256, size)
            }
        }
    }

    @Async
    override fun onCacheAccessed(credentials: StorageCredentials, sha256: String, size: Long) {
        if (!storageCacheEvictionProperties.enabled) {
            return
        }
        logger.info("cache file accessed, sha256[$sha256], storage[${credentials.key}], size[$size]")
        getStrategy(credentials).put(sha256, size)
    }

    override fun sync(credentials: StorageCredentials) {
        if (storageCacheEvictionProperties.enabled) {
            logger.info("start sync ${credentials.key}")
            getStrategy(credentials).sync()
        }
    }

    @Async
    @EventListener(FileDeletedEvent::class)
    open fun onFileCleaned(event: FileDeletedEvent) {
        if (event.rootPath.toPath() == event.credentials.cache.path.toPath()) {
            onCacheDeleted(event.credentials, event.sha256)
        }
    }

    @Async
    @EventListener(FileReservedEvent::class)
    open fun onFileReserved(event: FileReservedEvent) {
        if (event.rootPath.toPath() == event.credentials.cache.path.toPath()) {
            val attributes = Files.readAttributes(event.fullPath.toPath(), BasicFileAttributes::class.java)
            val lastAccessTime = attributes.lastAccessTime().toMillis()
            val size = attributes.size()
            onCacheReserved(event.credentials, event.sha256, size, lastAccessTime.toDouble())
        }
    }

    private fun getStrategy(credentials: StorageCredentials): StorageCacheEvictStrategy<String, Long> {
        val cache = storageCacheMap.getOrPut(credentials.cache.path) {
            val listener = StorageEldestRemovedListener(credentials, fileLocator, storageService)
            cacheFactory.create(credentials.cache).apply { addEldestRemovedListener(listener) }
        }
        refreshCacheProperties(cache, credentials)
        return cache
    }

    private fun refreshCacheProperties(
        cache: StorageCacheEvictStrategy<String, Long>,
        credentials: StorageCredentials
    ) {
        cache.setMaxWeight(credentials.cache.maxSize)
        cache.getEldestRemovedListeners().forEach {
            if (it is StorageEldestRemovedListener) {
                it.setCredentials(credentials)
            }
        }
    }

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
        private val logger = LoggerFactory.getLogger(DefaultStorageCacheEvictor::class.java)
    }
}
