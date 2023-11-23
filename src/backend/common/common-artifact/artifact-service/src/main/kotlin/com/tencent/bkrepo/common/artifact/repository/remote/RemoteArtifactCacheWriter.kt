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

package com.tencent.bkrepo.common.artifact.repository.remote

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.manager.StorageManager
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactDataReceiver
import com.tencent.bkrepo.common.artifact.stream.StreamReadListener
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import com.tencent.bkrepo.common.storage.util.toPath
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.slf4j.LoggerFactory

/**
 * 代理拉取数据写入缓存
 */
class RemoteArtifactCacheWriter(
    private val context: ArtifactContext,
    private val storageManager: StorageManager,
    monitor: StorageHealthMonitor,
    contentLength: Long,
    storageProperties: StorageProperties,
) : StreamReadListener {

    private val receiver: ArtifactDataReceiver
    private val useLocalPath: Boolean

    init {
        val storageCredentials = context.storageCredentials ?: storageProperties.defaultStorageCredentials()
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
    }

    override fun data(i: Int) {
        receiver.receive(i)
    }

    override fun data(buffer: ByteArray, off: Int, length: Int) {
        receiver.receiveChunk(buffer, off, length)
    }

    override fun finish() {
        receiver.finish()
        val artifactFile = ReceiverArtifactFile(receiver, useLocalPath)
        storageManager.storeArtifactFile(
            buildCacheNodeCreateRequest(context, artifactFile),
            artifactFile,
            context.storageCredentials
        )
        logger.info(
            "receive[${receiver.filePath}] finished, store remote artifact[${context.artifactInfo}] cache success"
        )
    }

    override fun close() {
        receiver.close()
        logger.info("close remote artifact[${context.artifactInfo}] cache receiver[${receiver.filePath}] success")
    }

    private fun buildCacheNodeCreateRequest(context: ArtifactContext, artifactFile: ArtifactFile): NodeCreateRequest {
        return NodeCreateRequest(
            projectId = context.repositoryDetail.projectId,
            repoName = context.repositoryDetail.name,
            folder = false,
            fullPath = context.artifactInfo.getArtifactFullPath(),
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            md5 = artifactFile.getFileMd5(),
            overwrite = true,
            operator = context.userId
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RemoteArtifactCacheWriter::class.java)
    }
}
