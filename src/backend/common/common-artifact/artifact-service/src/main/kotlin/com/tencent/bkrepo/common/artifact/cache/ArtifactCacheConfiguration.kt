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

import com.tencent.bkrepo.common.artifact.cache.local.LocalCountMinSketchCounter
import com.tencent.bkrepo.common.artifact.cache.local.LocalWTinyLFUCache
import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.cache.CacheStorageService
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ArtifactCacheEvictionProperties::class)
class ArtifactCacheConfiguration {
    @Bean
    fun storageCacheCleaner(
        cacheFactory: OrderedCachedFactory<String, Any?>,
        nodeClient: NodeClient,
        repositoryClient: RepositoryClient,
        storageService: CacheStorageService,
        fileLocator: FileLocator,
        storageCredentialsClient: StorageCredentialsClient,
        storageProperties: StorageProperties,
        artifactCacheEvictionProperties: ArtifactCacheEvictionProperties,
    ): ArtifactCacheCleaner {
        return DefaultArtifactCacheCleaner(
            cacheFactory,
            nodeClient,
            repositoryClient,
            storageService,
            fileLocator,
            storageCredentialsClient,
            storageProperties,
            artifactCacheEvictionProperties
        )
    }

    @Bean
    fun cacheFactory(): OrderedCachedFactory<String, Any?> {
        return object : OrderedCachedFactory<String, Any?> {
            override fun create(cacheProperties: CacheProperties): OrderedCache<String, Any?> {
                return LocalWTinyLFUCache(0, LocalCountMinSketchCounter()).apply {
                    setMaxWeight(cacheProperties.maxSize)
                }
            }
        }
    }
}
