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

package com.tencent.bkrepo.common.artifact.event.listener

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.event.ArtifactResponseEvent
import com.tencent.bkrepo.common.artifact.event.ArtifactUploadedEvent
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream.Companion.METADATA_KEY_CACHE_ENABLED
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.core.cache.evication.StorageCacheEvictor
import com.tencent.bkrepo.common.storage.core.cache.evication.StorageCacheEvictionProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ArtifactCacheAccessListener(
    private val storageCacheEvictionProperties: StorageCacheEvictionProperties,
    private val storageCacheEvictorProvider: ObjectProvider<StorageCacheEvictor>,
    private val storageProperties: StorageProperties,
) {
    @EventListener(ArtifactUploadedEvent::class)
    fun listen(event: ArtifactUploadedEvent) {
        val artifactFile = event.context.getArtifactFileOrNull()
        if (storageCacheEvictionProperties.enabled && artifactFile != null) {
            safeRecordArtifactCacheAccess(artifactFile)
        }
    }

    @EventListener(ArtifactResponseEvent::class)
    fun listen(event: ArtifactResponseEvent) {
        if (storageCacheEvictionProperties.enabled) {
            safeRecordArtifactCacheAccess(event.artifactResource, event.storageCredentials)
        }
    }

    /**
     * 记录缓存访问信息
     */
    private fun safeRecordArtifactCacheAccess(resource: ArtifactResource, storageCredentials: StorageCredentials?) {
        try {
            if (resource.node != null && !resource.node.folder) {
                recordSingleNode(resource, storageCredentials)
            } else if (resource.nodes.isNotEmpty()) {
                recordMultiNode(resource, storageCredentials)
            }
        } catch (ignore: Exception) {
            logger.warn("failed to record artifact cache access", ignore)
        }
    }

    private fun recordSingleNode(resource: ArtifactResource, storageCredentials: StorageCredentials?) {
        val cacheEnabled = resource.artifactMap.values.firstOrNull()?.getMetadata(METADATA_KEY_CACHE_ENABLED)
        if (cacheEnabled == true) {
            val credentials = storageCredentials ?: storageProperties.defaultStorageCredentials()
            val node = resource.node!!
            storageCacheEvictorProvider.ifAvailable?.onCacheAccessed(credentials, node.sha256!!, node.size)
        }
    }

    private fun recordMultiNode(resource: ArtifactResource, storageCredentials: StorageCredentials?) {
        resource.artifactMap.forEach { (name, ais) ->
            if (ais.getMetadata(METADATA_KEY_CACHE_ENABLED) == true) {
                val credentials = storageCredentials ?: storageProperties.defaultStorageCredentials()
                val node = resource.nodes.firstOrNull { name.endsWith(it.name) }
                node?.let {
                    storageCacheEvictorProvider.ifAvailable?.onCacheAccessed(credentials, it.sha256!!, it.size)
                }
            }
        }
    }

    private fun safeRecordArtifactCacheAccess(artifactFile: ArtifactFile) {
        try {
            if (artifactFile.isInMemory() || artifactFile.isInLocalDisk() || artifactFile.isFallback()) {
                return
            }
            val repo = ArtifactContextHolder.getRepoDetail() ?: return
            val credentials = repo.storageCredentials ?: storageProperties.defaultStorageCredentials()
            val storageCacheEvictor = storageCacheEvictorProvider.ifAvailable
            storageCacheEvictor?.onCacheAccessed(credentials, artifactFile.getFileSha256(), artifactFile.getSize())
        } catch (ignore: Exception) {
            logger.warn("failed to record artifact cache access", ignore)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactCacheAccessListener::class.java)
    }
}
