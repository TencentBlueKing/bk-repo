/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.generic.artifact.remote

import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.generic.artifact.findRemoteMetadata
import com.tencent.bkrepo.generic.artifact.updateParentMetadata
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component

/**
 * 异步将远程制品缓存到本地
 */
@Component
class AsyncRemoteArtifactCacheWriter(
    private val storageManager: StorageManager,
    private val nodeClient: NodeClient,
    private val httpClientBuilderFactory: AsyncCacheHttpClientBuilderFactory,
    private val executor: ThreadPoolTaskExecutor,
    private val cacheLocks: RemoteArtifactCacheLocks,
    private val metadataClient: MetadataClient,
) {
    fun cache(cacheTask: CacheTask) {
        with(cacheTask) {
            // lock存在表示正在缓存中，直接返回
            val (_, createNew) = cacheLocks.create(projectId, repoName, fullPath)
            if (!createNew) {
                logger.info("artifact[${"$projectId/$repoName$fullPath"}] is caching, skip cache")
                return
            }

            try {
                executor.execute { tryCache(cacheTask) }
            } catch (e: Exception) {
                cacheLocks.remove(projectId, repoName, fullPath)
                throw e
            }
        }
    }

    private fun tryCache(cacheTask: CacheTask) {
        val projectId = cacheTask.projectId
        val repoName = cacheTask.repoName
        val fullPath = cacheTask.fullPath
        try {
            if (nodeClient.getNodeDetail(projectId, repoName, fullPath).data != null) {
                logger.info("artifact[$projectId/$repoName$fullPath] was cached, skip cache")
            } else if (cacheLocks.tryLock(projectId, repoName, fullPath)) {
                try {
                    doCache(cacheTask)
                } finally {
                    cacheLocks.release(projectId, repoName, fullPath)
                }
            } else {
                logger.info("artifact[$projectId/$repoName$fullPath] is caching by other instance, skip cache")
            }
        } finally {
            cacheLocks.remove(projectId, repoName, fullPath)
        }
    }

    private fun doCache(cacheTask: CacheTask) {
        with(cacheTask) {
            logger.info("start async cache remote artifact[$projectId/$repoName$fullPath]")
            val httpClient = httpClientBuilderFactory.newBuilder(remoteConfiguration).build()
            // 移除分片下载header
            val request = request.newBuilder().removeHeader(HttpHeaders.RANGE).build()

            // 发起请求
            val response = httpClient.newCall(request).execute()

            // 缓存文件
            if (response.isSuccessful) {
                val contentLength = response.header(HttpHeaders.CONTENT_LENGTH)?.toLongOrNull()
                val artifactFile = ArtifactFileFactory.build(response.body!!.byteStream(), contentLength)
                try {
                    cacheArtifactFile(cacheTask, artifactFile)
                } finally {
                    artifactFile.delete()
                }
                logger.info("async cache remote artifact[$projectId/$repoName$fullPath] success")
            } else {
                val errMsg = response.body?.string()
                logger.error(
                    "async cache remote artifact[$projectId/$repoName$fullPath] failed, " +
                            " ${request.method} [${request.url}], code[${response.code}], err[$errMsg]"
                )
            }

            response.body?.close()
        }
    }

    /**
     * 将远程拉取的构件缓存本地
     */
    private fun cacheArtifactFile(cacheTask: CacheTask, artifactFile: ArtifactFile): NodeDetail {
        with(cacheTask) {
            val nodeCreateRequest = buildCacheNodeCreateRequest(cacheTask, artifactFile)
            val nodeDetail = storageManager.storeArtifactFile(nodeCreateRequest, artifactFile, storageCredentials)
            metadataClient.updateParentMetadata(remoteNodes, projectId, repoName, fullPath)
            return nodeDetail
        }
    }

    /**
     * 获取缓存节点创建请求
     */
    private fun buildCacheNodeCreateRequest(cacheTask: CacheTask, artifactFile: ArtifactFile): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = cacheTask.projectId,
            repoName = cacheTask.repoName,
            folder = false,
            fullPath = cacheTask.fullPath,
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            md5 = artifactFile.getFileMd5(),
            nodeMetadata = findRemoteMetadata(cacheTask.remoteNodes, cacheTask.fullPath),
            overwrite = true,
            operator = cacheTask.userId
        )
    }

    data class CacheTask(
        val projectId: String,
        val repoName: String,
        val storageCredentials: StorageCredentials?,
        val remoteConfiguration: RemoteConfiguration,
        val fullPath: String,
        val userId: String,
        val request: Request,
        val remoteNodes: List<Any>
    )

    companion object {
        private val logger = LoggerFactory.getLogger(AsyncRemoteArtifactCacheWriter::class.java)
    }
}
