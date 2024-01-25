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
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import org.springframework.stereotype.Component

@Component
class DefaultStorageCacheCleaner(
    private val cache: OrderedCache<String, Any?>,
    private val nodeClient: NodeClient,
    private val repositoryClient: RepositoryClient,
    private val storageService: CacheStorageService,
    private val fileLocator: FileLocator,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val storageProperties: StorageProperties,
) : StorageCacheCleaner {

    init {
        cache.addEldestRemovedListener(object : EldestRemovedListener<String, Any?> {
            override fun onEldestRemoved(key: String, value: Any?) {
                val (storageKey, sha256) = parseKey(key)
                val path = fileLocator.locate(sha256)
                storageService.deleteCacheFile(path, sha256, getStorageCredentials(storageKey))
            }
        })
    }

    override fun onCacheAccessed(projectId: String, repoName: String, fullPath: String) {
        nodeClient.getNodeDetail(projectId, repoName, fullPath).data?.let {
            if (!it.folder) {
                val storageKey =
                    repositoryClient.getRepoInfo(projectId, repoName).data?.storageCredentialsKey ?: DEFAULT_STORAGE_KEY
                onCacheAccessed(storageKey, it.sha256!!)
            }
        }
    }

    override fun onCacheAccessed(storageKey: String, sha256: String) {
        val key = generateKey(storageKey, sha256)
        onCacheAccessed(key)
    }

    private fun onCacheAccessed(key: String) {
        cache.put(key, null)
    }

    private fun generateKey(storageKey: String, sha256: String) = "$storageKey:$sha256"

    private fun parseKey(key: String): Pair<String, String> {
        val splits = key.split(":")
        require(splits.size == 2)
        // (storageKey, sha256)
        return Pair(splits[0], splits[1])
    }

    private fun getStorageCredentials(key: String): StorageCredentials {
        return if (key == DEFAULT_STORAGE_KEY) {
            storageProperties.defaultStorageCredentials()
        } else {
            storageCredentialsClient.findByKey(key).data!!
        }
    }
}
