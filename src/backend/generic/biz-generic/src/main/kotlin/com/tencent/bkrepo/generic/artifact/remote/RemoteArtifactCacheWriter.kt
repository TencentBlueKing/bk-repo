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

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactDataReceiver
import com.tencent.bkrepo.common.artifact.stream.StreamReadListener
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import com.tencent.bkrepo.common.storage.util.toPath
import com.tencent.bkrepo.generic.artifact.findRemoteMetadata
import com.tencent.bkrepo.generic.artifact.updateParentMetadata
import com.tencent.bkrepo.repository.api.MetadataClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory

/**
 * 代理拉取数据写入缓存
 */
class RemoteArtifactCacheWriter(
    private val context: ArtifactContext,
    private val storageManager: StorageManager,
    private val cacheLocks: RemoteArtifactCacheLocks,
    private val remoteNodes: List<Any>,
    private val metadataClient: MetadataClient,
    monitor: StorageHealthMonitor,
    contentLength: Long,
    storageProperties: StorageProperties,
) : StreamReadListener {

    private val projectId: String = context.repositoryDetail.projectId
    private val repoName: String = context.repositoryDetail.name
    private val fullPath: String = context.artifactInfo.getArtifactFullPath()
    private val cacheLockCreated: Boolean = cacheLocks.create(projectId, repoName, fullPath).second
    private val receiver: ArtifactDataReceiver?
    private val useLocalPath: Boolean

    init {
        val shouldCache = try {
            cacheLockCreated && cacheLocks.tryLock(projectId, repoName, fullPath)
        } catch (e: Exception) {
            logger.error("create cache lock for artifact[${context.artifactInfo}] failed", e)
            false
        }

        if (shouldCache) {
            val storageCredentials = context.repositoryDetail.storageCredentials
                ?: storageProperties.defaultStorageCredentials()
            // 主要路径，可以为DFS路径
            val path = storageCredentials.upload.location.toPath()
            // 本地路径
            val localPath = storageCredentials.upload.localPath.toPath()
            // 本地路径阈值
            val localThreshold = storageProperties.receive.localThreshold

            useLocalPath = contentLength > 0 && contentLength < localThreshold.toBytes()
            receiver = ArtifactDataReceiver(
                storageProperties.receive,
                storageProperties.monitor,
                if (useLocalPath) localPath else path,
                randomPath = !useLocalPath
            )

            // 本地磁盘不需要fallback
            if (!useLocalPath && !monitor.healthy.get()) {
                receiver.unhealthy(monitor.getFallbackPath(), monitor.fallBackReason)
                logger.warn("remote artifact[${context.artifactInfo}] cache receiver was unhealthy")
            }
            logger.info("start cache remote artifact[${context.artifactInfo}]")
        } else {
            useLocalPath = false
            receiver = null
            if (cacheLockCreated) {
                cacheLocks.remove(projectId, repoName, fullPath)
                logger.info("remote artifact[${context.artifactInfo}] was caching by other instance, skip cache")
            } else {
                logger.info("remote artifact[${context.artifactInfo}] was caching, skip cache")
            }
        }
    }

    override fun data(i: Int) {
        receiver?.receive(i)
    }

    override fun data(buffer: ByteArray, off: Int, length: Int) {
        receiver?.receiveChunk(buffer, off, length)
    }

    override fun finish() {
        if (receiver != null) {
            receiver.finish()
            val artifactFile = ReceiverArtifactFile(receiver, useLocalPath)
            storageManager.storeArtifactFile(
                buildCacheNodeCreateRequest(context, artifactFile),
                artifactFile,
                context.repositoryDetail.storageCredentials
            )
            metadataClient.updateParentMetadata(remoteNodes, projectId, repoName, fullPath)
            logger.info(
                "receive[${receiver.filePath}] finished, store remote artifact[${context.artifactInfo}] cache success"
            )
        }
    }

    override fun close() {
        if (receiver != null) {
            receiver.close()
            cacheLocks.release(projectId, repoName, fullPath)
            logger.info("close remote artifact[${context.artifactInfo}] cache receiver[${receiver.filePath}] success")
        }
    }

    private fun buildCacheNodeCreateRequest(context: ArtifactContext, artifactFile: ArtifactFile): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = projectId,
            repoName = repoName,
            folder = false,
            fullPath = fullPath,
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            md5 = artifactFile.getFileMd5(),
            nodeMetadata = findRemoteMetadata(remoteNodes, fullPath),
            overwrite = true,
            operator = context.userId
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RemoteArtifactCacheWriter::class.java)
    }
}
