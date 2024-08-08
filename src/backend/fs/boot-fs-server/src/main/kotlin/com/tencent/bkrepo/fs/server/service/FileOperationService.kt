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

package com.tencent.bkrepo.fs.server.service

import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.fs.server.api.RRepositoryClient
import com.tencent.bkrepo.fs.server.constant.FS_ATTR_KEY
import com.tencent.bkrepo.fs.server.context.ReactiveArtifactContextHolder
import com.tencent.bkrepo.fs.server.model.NodeAttribute
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.fs.server.request.BlockRequest
import com.tencent.bkrepo.fs.server.request.FlushRequest
import com.tencent.bkrepo.fs.server.storage.CoStorageManager
import com.tencent.bkrepo.fs.server.storage.CoArtifactFile
import com.tencent.bkrepo.repository.pojo.metadata.MetadataModel
import com.tencent.bkrepo.repository.pojo.metadata.MetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.service.NodeSetLengthRequest
import kotlinx.coroutines.reactor.awaitSingle
import java.time.LocalDateTime

class FileOperationService(
    private val rRepositoryClient: RRepositoryClient,
    private val storageManager: CoStorageManager,
    private val fileNodeService: FileNodeService
) {

    suspend fun read(nodeDetail: NodeDetail, range: Range): ArtifactInputStream? {
        val repo = ReactiveArtifactContextHolder.getRepoDetail()
        return fileNodeService.read(
            nodeDetail = nodeDetail,
            storageCredentials = repo.storageCredentials,
            range = range
        )
    }

    suspend fun write(artifactFile: CoArtifactFile, request: BlockRequest, user: String): TBlockNode {
        with(request) {
            val blockNode = TBlockNode(
                createdBy = user,
                createdDate = LocalDateTime.now(),
                nodeFullPath = fullPath,
                startPos = offset,
                sha256 = artifactFile.getFileSha256(),
                projectId = projectId,
                repoName = repoName,
                size = artifactFile.getSize()
            )
            storageManager.storeBlock(artifactFile, blockNode)
            return blockNode
        }
    }

    suspend fun flush(request: FlushRequest, user: String) {
        with(request) {
            rRepositoryClient.listMetadata(projectId, repoName, fullPath).awaitSingle().data
                ?.get(FS_ATTR_KEY) ?: let {
                val attributes = NodeAttribute(
                    uid = NodeAttribute.NOBODY,
                    gid = NodeAttribute.NOBODY,
                    mode = NodeAttribute.DEFAULT_MODE
                )
                val fsAttr = MetadataModel(
                    key = FS_ATTR_KEY,
                    value = attributes
                )
                val saveMetaDataRequest = MetadataSaveRequest(
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    nodeMetadata = listOf(fsAttr),
                    operator = user
                )
                rRepositoryClient.saveMetadata(saveMetaDataRequest).awaitSingle()
            }

            val nodeSetLengthRequest = NodeSetLengthRequest(
                projectId = projectId,
                repoName = repoName,
                fullPath = fullPath,
                newLength = length,
                operator = user
            )
            rRepositoryClient.setLength(nodeSetLengthRequest).awaitSingle()
        }
    }
}
