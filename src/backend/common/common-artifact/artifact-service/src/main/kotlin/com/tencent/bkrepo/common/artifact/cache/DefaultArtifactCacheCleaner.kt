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

package com.tencent.bkrepo.common.artifact.cache

import com.tencent.bkrepo.common.artifact.constant.DEFAULT_STORAGE_KEY
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.cache.CacheStorageService
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

open class DefaultArtifactCacheCleaner(
    private val cacheFactory: OrderedCachedFactory<String, Long>,
    private val nodeClient: NodeClient,
    private val repositoryClient: RepositoryClient,
    private val storageService: CacheStorageService,
    private val fileLocator: FileLocator,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val storageProperties: StorageProperties,
    private val artifactCacheEvictionProperties: ArtifactCacheEvictionProperties,
) : ArtifactCacheCleaner {

    private val storageCacheMap = ConcurrentHashMap<String, OrderedCache<String, Long>>()

    @Async
    override fun onCacheAccessed(projectId: String, repoName: String, fullPath: String) {
        if (!artifactCacheEvictionProperties.enabled) {
            return
        }
        nodeClient.getNodeDetail(projectId, repoName, fullPath).data?.let {
            onCacheAccessed(it, getStorageKey(projectId, repoName))
        }
    }

    @Async
    override fun onCacheAccessed(node: NodeDetail, storageKey: String?) {
        if (!node.folder) {
            onCacheAccessed(storageKey ?: DEFAULT_STORAGE_KEY, node.sha256!!, node.size)
        }
    }

    @Async
    override fun onCacheDeleted(storageKey: String, sha256: String) {
        getCache(storageKey).remove(sha256)
    }

    open fun onCacheAccessed(storageKey: String, sha256: String, size: Long) {
        if (!artifactCacheEvictionProperties.enabled || sha256 == FAKE_SHA256) {
            return
        }
        logger.info("Cache file accessed, sha256[$sha256], storage[$storageKey], size[$size]")
        getCache(storageKey).put(sha256, size)
    }

    /**
     * 定时同步最新的存储配置
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    open fun refreshStorageCredentials() {
        val credentials = storageCredentialsClient.list().data ?: return
        for (credential in credentials) {
            storageCacheMap[credential.key!!]?.let { refreshCacheProperties(it, credential) }
        }
        storageCacheMap[DEFAULT_STORAGE_KEY]?.let {
            refreshCacheProperties(it, storageProperties.defaultStorageCredentials())
        }
    }

    private fun refreshCacheProperties(cache: OrderedCache<String, Long>, credentials: StorageCredentials) {
        cache.setMaxWeight(credentials.cache.maxSize)
        cache.getEldestRemovedListeners().forEach {
            if (it is StorageEldestRemovedListener) {
                it.setCredentials(credentials)
            }
        }
    }

    private fun getCache(storageKey: String): OrderedCache<String, Long> {
        return storageCacheMap.getOrPut(storageKey) {
            val credentials = getStorageCredentials(storageKey)
            val listener = StorageEldestRemovedListener(credentials, fileLocator, storageService)
            cacheFactory.create(credentials.cache).apply { addEldestRemovedListener(listener) }
        }
    }

    private fun getStorageKey(projectId: String, repoName: String): String {
        return repositoryClient.getRepoInfo(projectId, repoName).data?.storageCredentialsKey ?: DEFAULT_STORAGE_KEY
    }

    private fun getStorageCredentials(key: String): StorageCredentials {
        return if (key == DEFAULT_STORAGE_KEY) {
            storageProperties.defaultStorageCredentials()
        } else {
            storageCredentialsClient.findByKey(key).data!!
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
