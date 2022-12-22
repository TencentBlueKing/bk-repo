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

import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.fs.server.api.RRepositoryClient
import com.tencent.bkrepo.fs.server.service.FileNodeService
import com.tencent.bkrepo.fs.server.storage.CoStorageManager
import com.tencent.bkrepo.fs.server.storage.ReactiveArtifactFileFactory
import com.tencent.bkrepo.fs.server.copyTo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener

/**
 * 当文件块被更新时，刷新节点数据，保证普通下载可以下载到最新的数据
 * */
class NodeFlushListener(
    private val storageManager: CoStorageManager,
    private val fileNodeService: FileNodeService,
    private val rRepositoryClient: RRepositoryClient,
    private val applicationCoroutineScope: CoroutineScope
) {
    @EventListener(NodeFlushEvent::class)
    fun listen(event: NodeFlushEvent) {
        logger.info("Receive node flush event $event")
        applicationCoroutineScope.launch {
            with(event) {
                val node = rRepositoryClient.getNodeDetail(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath
                ).awaitSingle().data
                val storageCredentials = event.repositoryDetail.storageCredentials
                val range = Range.full(size)
                val fileInputStream = fileNodeService.read(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    storageCredentials = repositoryDetail.storageCredentials,
                    digest = node?.sha256,
                    size = node?.size,
                    range = range
                ) ?: throw ArtifactNotFoundException(event.toString())
                val artifactFile = ReactiveArtifactFileFactory.buildArtifactFileOnNotHttpRequest(storageCredentials)
                try {
                    fileInputStream.copyTo(artifactFile)
                    if (event.md5 != null) {
                        validateCheckSum(artifactFile.getFileMd5(), event.md5)
                    }
                    val nodeCreateRequest = NodeCreateRequest(
                        projectId = projectId,
                        repoName = repoName,
                        folder = false,
                        fullPath = fullPath,
                        size = artifactFile.getSize(),
                        sha256 = artifactFile.getFileSha256(),
                        md5 = artifactFile.getFileMd5(),
                        operator = event.userId,
                        overwrite = true
                    )
                    storageManager.storeNode(artifactFile, nodeCreateRequest, storageCredentials)
                    logger.info(
                        "Success to flush node[$projectId/$repoName$fullPath]," +
                            "sha256[${artifactFile.getFileSha256()}]"
                    )
                } catch (e: Exception) {
                    logger.error("Failed to flush node[$projectId/$repoName$fullPath]", e)
                } finally {
                    artifactFile.delete()
                }
            }
        }
    }

    private fun validateCheckSum(serverMd5: String, clientMd5: String) {
        if (serverMd5 != clientMd5) {
            throw IllegalStateException("File has broken,server md5 $serverMd5 != client md5 $clientMd5")
        }
    }

    companion object {

        private val logger = LoggerFactory.getLogger(NodeFlushListener::class.java)
    }
}
