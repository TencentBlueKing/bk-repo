/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.fs.server.listener

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fs.server.api.RRepositoryClient
import com.tencent.bkrepo.fs.server.file.ReactiveArtifactFile
import com.tencent.bkrepo.fs.server.model.TBlockNode
import com.tencent.bkrepo.fs.server.service.BlockNodeService
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory

/**
 * 当文件块被更新时，刷新节点数据，保证普通下载可以下载到最新的数据
 * */
class NodeFlushListener(
    val rRepositoryClient: RRepositoryClient,
    val blockNodeService: BlockNodeService,
    val storageService: StorageService
) {
    @EventListener(NodeFlushEvent::class)
    fun listen(event: NodeFlushEvent) {
        with(event) {
            runBlocking {
                val blockNodes = blockNodeService.listBlocks(projectId, repoName, fullPath)
                if (blockNodes.isEmpty()) {
                    return@runBlocking
                }
                val newArtifactFile = mergeBlock(blockNodes, storageCredentials)
                storageService.store(newArtifactFile.getFileSha256(), newArtifactFile, storageCredentials)
                val nodeCreateRequest = NodeCreateRequest(
                    projectId = projectId,
                    repoName = repoName,
                    folder = false,
                    fullPath = fullPath,
                    size = newArtifactFile.getSize(),
                    sha256 = newArtifactFile.getFileSha256(),
                    md5 = newArtifactFile.getFileMd5(),
                    operator = userId,
                    overwrite = true
                )
                val node = rRepositoryClient.createNode(nodeCreateRequest).awaitSingle()
                logger.info("Success flush node[$projectId/$repoName/$fullPath],[${blockNodes.size}] blocks,sha256[${node.data?.sha256}]")
            }
        }
    }

    private suspend fun mergeBlock(blocks: List<TBlockNode>, storageCredentials: StorageCredentials): ArtifactFile {
        val artifactFile = ReactiveArtifactFile(storageCredentials)
        flow<DataBuffer> {
            blocks.forEach {
                val size = it.size.toLong()
                val range = Range.full(size)
                val artifactInputStream = storageService.load(it.sha256, range, storageCredentials)
                    ?: throw RuntimeException("block data miss")
                DataBufferUtils.readInputStream({ artifactInputStream }, DefaultDataBufferFactory.sharedInstance, 1024).asFlow().collect { buf ->
                    emit(buf)
                }
            }
        }.collect {
            artifactFile.write(it)
        }
        return artifactFile
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NodeFlushListener::class.java)
    }
}
