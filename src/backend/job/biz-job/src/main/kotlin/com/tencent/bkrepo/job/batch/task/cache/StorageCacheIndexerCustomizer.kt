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

package com.tencent.bkrepo.job.batch.task.cache

import com.tencent.bkrepo.common.artifact.constant.DEFAULT_STORAGE_KEY
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.core.cache.CacheStorageService
import com.tencent.bkrepo.common.storage.core.cache.indexer.IndexerCustomizer
import com.tencent.bkrepo.common.storage.core.cache.indexer.StorageCacheIndexer
import com.tencent.bkrepo.common.storage.core.cache.indexer.listener.StorageEldestRemovedListener
import com.tencent.bkrepo.common.storage.core.cache.indexer.metrics.StorageCacheIndexerMetrics
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.filesystem.cleanup.FileRetainResolver
import org.springframework.stereotype.Component

@Component
class StorageCacheIndexerCustomizer(
    private val resolver: FileRetainResolver,
    private val fileLocator: FileLocator,
    private val storageService: StorageService,
    private val storageCacheIndexerMetrics: StorageCacheIndexerMetrics,
) : IndexerCustomizer<String, Long> {
    override fun customize(indexer: StorageCacheIndexer<String, Long>, credentials: StorageCredentials) {
        if (storageService is CacheStorageService) {
            indexer.addEldestRemovedListener(
                EldestRemovedListener(credentials, fileLocator, storageService, storageCacheIndexerMetrics, resolver)
            )
        }
    }

    /**
     * 兼容缓存保留策略的缓存移除监听器，仅满足保留策略的才删除对应的缓存文件
     */
    private class EldestRemovedListener(
        storageCredentials: StorageCredentials,
        fileLocator: FileLocator,
        storageService: CacheStorageService,
        storageCacheIndexerMetrics: StorageCacheIndexerMetrics,
        private val resolver: FileRetainResolver,
    ) : StorageEldestRemovedListener(storageCredentials, fileLocator, storageService, storageCacheIndexerMetrics) {
        override fun onEldestRemoved(key: String, value: Long) {
            if (!resolver.retain(key)) {
                super.onEldestRemoved(key, value)
            } else {
                val storageKey = storageCredentials.key ?: DEFAULT_STORAGE_KEY
                storageCacheIndexerMetrics?.evicted(storageKey, value, false)
            }
        }
    }
}
