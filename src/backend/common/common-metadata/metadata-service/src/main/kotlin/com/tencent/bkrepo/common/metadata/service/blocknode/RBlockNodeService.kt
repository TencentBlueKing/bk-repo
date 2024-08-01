package com.tencent.bkrepo.common.metadata.service.blocknode

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
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
}
