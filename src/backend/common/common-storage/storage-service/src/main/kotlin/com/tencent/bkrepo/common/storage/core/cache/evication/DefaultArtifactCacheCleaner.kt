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
import com.tencent.bkrepo.common.storage.filesystem.cleanup.event.FileCleanedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import java.util.concurrent.ConcurrentHashMap

open class DefaultArtifactCacheCleaner(
    private val cacheFactory: OrderedCachedFactory<String, Long>,
    private val storageService: CacheStorageService,
    private val fileLocator: FileLocator,
    private val artifactCacheEvictionProperties: ArtifactCacheEvictionProperties,
) : ArtifactCacheCleaner {

    private val storageCacheMap = ConcurrentHashMap<String, OrderedCache<String, Long>>()

    @Async
    override fun onCacheDeleted(credentials: StorageCredentials, sha256: String) {
        if (artifactCacheEvictionProperties.enabled) {
            logger.info("remove cache of [$sha256], storage[${credentials.key}]")
            getCache(credentials).remove(sha256)
        }
    }

    override fun onCacheAccessed(credentials: StorageCredentials, sha256: String, size: Long) {
        if (!artifactCacheEvictionProperties.enabled) {
            return
        }
        logger.info("cache file accessed, sha256[$sha256], storage[${credentials.key}], size[$size]")
        getCache(credentials).put(sha256, size)
    }

    @Async
    @EventListener(FileCleanedEvent::class)
    open fun onFileCleaned(event: FileCleanedEvent) {
        if (event.rootPath == event.credentials.cache.path) {
            onCacheDeleted(event.credentials, event.sha256)
        }
    }

    private fun getCache(credentials: StorageCredentials): OrderedCache<String, Long> {
        val cache = storageCacheMap.getOrPut(credentials.cache.path) {
            val listener = StorageEldestRemovedListener(credentials, fileLocator, storageService)
            cacheFactory.create(credentials.cache).apply { addEldestRemovedListener(listener) }
        }
        refreshCacheProperties(cache, credentials)
        return cache
    }

    private fun refreshCacheProperties(cache: OrderedCache<String, Long>, credentials: StorageCredentials) {
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
        private val logger = LoggerFactory.getLogger(DefaultArtifactCacheCleaner::class.java)
    }
}
