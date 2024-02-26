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

import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.cache.CacheStorageService
import com.tencent.bkrepo.common.storage.core.cache.evication.ArtifactCacheEvictionProperties.Companion.CACHE_TYPE_LOCAL
import com.tencent.bkrepo.common.storage.core.cache.evication.ArtifactCacheEvictionProperties.Companion.CACHE_TYPE_REDIS
import com.tencent.bkrepo.common.storage.core.cache.evication.local.LocalSLRUCache
import com.tencent.bkrepo.common.storage.core.cache.evication.redis.RedisSLRUCache
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisTemplate

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ArtifactCacheEvictionProperties::class)
@AutoConfigureAfter(StorageAutoConfiguration::class)
class ArtifactCacheConfiguration {
    @Bean
    @ConditionalOnBean(OrderedCachedFactory::class, CacheStorageService::class)
    fun storageCacheCleaner(
        cacheFactory: OrderedCachedFactory<String, Long>,
        storageService: CacheStorageService,
        fileLocator: FileLocator,
        storageProperties: StorageProperties,
        artifactCacheEvictionProperties: ArtifactCacheEvictionProperties,
    ): ArtifactCacheCleaner {
        return DefaultArtifactCacheCleaner(
            cacheFactory,
            storageService,
            fileLocator,
            artifactCacheEvictionProperties
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = "artifact.cache.eviction", name = ["cacheType"], havingValue = CACHE_TYPE_LOCAL)
    fun localCacheFactory(): OrderedCachedFactory<String, Long> {
        return object : OrderedCachedFactory<String, Long> {
            override fun create(cacheProperties: CacheProperties): OrderedCache<String, Long> {
                return LocalSLRUCache(0).apply {
                    setMaxWeight(cacheProperties.maxSize)
                    setKeyWeightSupplier { _, v -> v.toString().toLong() }
                }
            }
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "artifact.cache.eviction", name = ["cacheType"], havingValue = CACHE_TYPE_REDIS)
    @ConditionalOnBean(RedisTemplate::class)
    fun redisCacheFactory(
        redisTemplate: RedisTemplate<String, String>
    ): OrderedCachedFactory<String, Long> {
        return object : OrderedCachedFactory<String, Long> {
            override fun create(cacheProperties: CacheProperties): OrderedCache<String, Long> {
                val cacheName = cacheProperties.path.replace("/", "__")
                val cache = RedisSLRUCache(cacheName, redisTemplate, 0)
                cache.setMaxWeight(cacheProperties.maxSize)
                return cache
            }
        }
    }
}
