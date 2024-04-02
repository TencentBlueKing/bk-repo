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

package com.tencent.bkrepo.common.storage.core.cache.indexer

import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.cache.CacheStorageService
import com.tencent.bkrepo.common.storage.core.cache.indexer.StorageCacheIndexProperties.Companion.CACHE_TYPE_REDIS_LRU
import com.tencent.bkrepo.common.storage.core.cache.indexer.StorageCacheIndexProperties.Companion.CACHE_TYPE_REDIS_SLRU
import com.tencent.bkrepo.common.storage.core.cache.indexer.listener.StorageEldestRemovedListener
import com.tencent.bkrepo.common.storage.core.cache.indexer.redis.RedisLRUCacheIndexer
import com.tencent.bkrepo.common.storage.core.cache.indexer.redis.RedisSLRUCacheIndexer
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.util.toPath
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisTemplate

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedisTemplate::class)
@ConditionalOnProperty(prefix = "storage.cache.index", name = ["enabled"])
@EnableConfigurationProperties(StorageCacheIndexProperties::class)
class StorageCacheIndexConfiguration {
    @Bean
    fun storageCacheIndexerManager(
        cacheFactory: StorageCacheIndexerFactory<String, Long>,
        storageProperties: StorageProperties,
        storageCacheIndexProperties: StorageCacheIndexProperties,
    ): StorageCacheIndexerManager {
        return StorageCacheIndexerManager(cacheFactory, storageCacheIndexProperties)
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage.cache.index", name = ["type"], havingValue = CACHE_TYPE_REDIS_LRU)
    fun redisLruCacheFactory(
        storageCacheIndexProperties: StorageCacheIndexProperties,
        redisTemplate: RedisTemplate<String, String>,
        fileLocator: FileLocator,
        customizer: IndexerCustomizer<String, Long>,
    ): StorageCacheIndexerFactory<String, Long> {
        return object : StorageCacheIndexerFactory<String, Long> {
            override fun create(name: String, credentials: StorageCredentials): StorageCacheIndexer<String, Long> {
                val cachePath = credentials.cache.path.toPath()
                val hashTag = storageCacheIndexProperties.hashTag
                val evict = storageCacheIndexProperties.evict
                val maxEvictCount = storageCacheIndexProperties.maxEvictCount
                val indexer = RedisLRUCacheIndexer(
                    name, cachePath, fileLocator, maxEvictCount, redisTemplate, 0,
                    hashTag = hashTag, evict = evict
                )
                indexer.setMaxWeight(credentials.cache.maxSize)
                customizer.customize(indexer, credentials)
                return indexer
            }
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "storage.cache.index", name = ["type"], havingValue = CACHE_TYPE_REDIS_SLRU)
    fun redisSlruCacheFactory(
        storageCacheIndexProperties: StorageCacheIndexProperties,
        redisTemplate: RedisTemplate<String, String>,
        fileLocator: FileLocator,
        customizer: IndexerCustomizer<String, Long>,
    ): StorageCacheIndexerFactory<String, Long> {
        return object : StorageCacheIndexerFactory<String, Long> {
            override fun create(name: String, credentials: StorageCredentials): StorageCacheIndexer<String, Long> {
                val cachePath = credentials.cache.path.toPath()
                val hashTag = storageCacheIndexProperties.hashTag
                val evict = storageCacheIndexProperties.evict
                val maxEvictCount = storageCacheIndexProperties.maxEvictCount
                val indexer = RedisSLRUCacheIndexer(
                    name, cachePath, fileLocator, maxEvictCount, redisTemplate, 0,
                    hashTag = hashTag, evict = evict
                )
                indexer.setMaxWeight(credentials.cache.maxSize)
                customizer.customize(indexer, credentials)
                return indexer
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun defaultIndexerCustomizer(
        storageService: CacheStorageService,
        fileLocator: FileLocator,
    ): IndexerCustomizer<String, Long> {
        return object : IndexerCustomizer<String, Long> {
            override fun customize(indexer: StorageCacheIndexer<String, Long>, credentials: StorageCredentials) {
                val listener = StorageEldestRemovedListener(credentials, fileLocator, storageService)
                indexer.addEldestRemovedListener(listener)
            }
        }
    }
}
