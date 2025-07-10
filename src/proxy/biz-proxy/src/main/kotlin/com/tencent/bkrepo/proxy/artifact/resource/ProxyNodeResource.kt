/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.proxy.artifact.resource

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.artifact.manager.ProxyBlobCacheWriter
import com.tencent.bkrepo.common.artifact.manager.resource.AbstractNodeResource
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.artifactStream
import com.tencent.bkrepo.common.service.proxy.ProxyFeignClientFactory
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.replication.api.proxy.ProxyBlobReplicaClient
import com.tencent.bkrepo.replication.pojo.blob.BlobPullRequest
import org.slf4j.LoggerFactory

/**
 * 代理节点资源，节点来自Proxy所属的服务端
 */
class ProxyNodeResource(
    private val sha256: String,
    private val range: Range,
    private val storageCredentials: StorageCredentials?,
    private val storageService: StorageService
) : AbstractNodeResource() {

    private val storageKey = storageCredentials?.key

    /**
     * 存储同步client
     */
    private val blobReplicaClient: ProxyBlobReplicaClient by lazy {
        ProxyFeignClientFactory.create("replication")
    }

    override fun exists(): Boolean {
        return try {
            blobReplicaClient.check(sha256, storageKey, RepositoryType.GENERIC.name).data ?: false
        } catch (exception: Exception) {
            logger.error("Failed to check blob data[$sha256] in remote node.", exception)
            false
        }
    }

    override fun getArtifactInputStream(): ArtifactInputStream? {
        try {
            storageService.load(sha256, range, storageCredentials)?.let {
                return it
            }
            if (!exists()) {
                return null
            }
            val request = BlobPullRequest(sha256, range, storageKey)
            val response = blobReplicaClient.pull(request)
            check(response.status() == HttpStatus.OK.value) {
                "Failed to pull blob[$sha256] from remote node, status: ${response.status()}"
            }
            val artifactInputStream = response.body()?.asInputStream()?.artifactStream(range)
            if (artifactInputStream != null && range.isFullContent()) {
                val listener = ProxyBlobCacheWriter(storageService, sha256)
                artifactInputStream.addListener(listener)
            }
            logger.info("Pull blob data[$sha256] from remote node.")
            return artifactInputStream
        } catch (exception: Exception) {
            logger.error("Failed to pull blob data[$sha256] from remote node.", exception)
        }
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProxyNodeResource::class.java)
    }
}
