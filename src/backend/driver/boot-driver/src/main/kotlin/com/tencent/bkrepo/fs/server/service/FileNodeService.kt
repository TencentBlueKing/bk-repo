/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
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
import com.tencent.bkrepo.common.metadata.service.blocknode.RBlockNodeService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import com.tencent.bkrepo.fs.server.storage.CoStorageManager
import com.tencent.bkrepo.repository.pojo.node.NodeDetail

class FileNodeService(
    private val blockNodeService: RBlockNodeService,
    private val coStorageManager: CoStorageManager
) {

    /**
     * 读取指定范围的文件
     * 使用块数据overlay节点数据，当节点数据不存在时，则合并块，同时块之间空的地方补0
     * @param nodeDetail 节点详情
     * @param storageCredentials 仓库的存储实例
     * @param range 需要读取的文件范围
     * */
    suspend fun read(
        nodeDetail: NodeDetail,
        storageCredentials: StorageCredentials?,
        range: Range
    ): ArtifactInputStream? {
        val blocks = blockNodeService.info(nodeDetail, range)
        return coStorageManager.loadArtifactInputStream(blocks, range, storageCredentials)
    }

    suspend fun info(
        nodeDetail: NodeDetail,
        range: Range
    ): List<RegionResource> {
        return blockNodeService.info(nodeDetail, range)
    }

    suspend fun deleteNodeBlocks(projectId: String, repoName: String, nodeFullPath: String) {
        blockNodeService.deleteBlocks(projectId, repoName, nodeFullPath)
    }

    suspend fun renameNodeBlocks(projectId: String, repoName: String, nodeFullPath: String, newFullPath: String) {
        blockNodeService.moveBlocks(projectId, repoName, nodeFullPath, newFullPath)
    }
}
