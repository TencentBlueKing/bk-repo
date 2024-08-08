/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 *  A copy of the MIT License is included in this file.
 *
 *
 *  Terms of the MIT License:
 *  ---------------------------------------------------
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 *  documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 *  the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 *  LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 *  NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 *  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.service.blocknode

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import java.time.LocalDateTime

interface RBlockNodeService {
    /**
     * 查询出范围内的分块
     * */
    suspend fun listBlocks(
        range: Range,
        projectId: String,
        repoName: String,
        fullPath: String,
        createdDate: String
    ): List<TBlockNode>

    /**
     * 创建分块
     * */
    suspend fun createBlock(
        blockNode: TBlockNode,
        storageCredentials: StorageCredentials?
    ): TBlockNode

    /**
     * 删除旧分块，即删除非指定的nodeCurrentSha256的分块。
     * 如果未指定nodeCurrentSha256，则删除节点所有分块
     * @param projectId 项目id
     * @param repoName 仓库名
     * @param fullPath 文件路径
     * */
    suspend fun deleteBlocks(
        projectId: String,
        repoName: String,
        fullPath: String
    )

    /**
     * 移动文件对应分块
     */
    suspend fun moveBlocks(
        projectId: String,
        repoName: String,
        fullPath: String,
        dstFullPath: String
    )

    /**
     * 恢复文件对应分块
     */
    suspend fun restoreBlocks(
        projectId: String,
        repoName: String,
        fullPath: String,
        nodeCreateDate: LocalDateTime,
        nodeDeleteDate: LocalDateTime
    )

    /**
     * 查询节点对应范围的分块资源
     */
    suspend fun info(
        nodeDetail: NodeDetail,
        range: Range
    ): List<RegionResource>
}
