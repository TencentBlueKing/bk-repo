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

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.fs.server.api.RRepositoryClient
import com.tencent.bkrepo.fs.server.model.TBlockNode
import com.tencent.bkrepo.fs.server.repository.BlockNodeRepository
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.gte
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.lte
import org.springframework.data.mongodb.core.query.where

/**
 * 文件块服务
 * */
class BlockNodeServiceImpl(
    private val blockNodeRepository: BlockNodeRepository,
    private val rRepositoryClient: RRepositoryClient
) : BlockNodeService {

    override suspend fun getBlock(projectId: String, repoName: String, fullPath: String, offset: Long): TBlockNode? {
        val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
            .and(TBlockNode::projectId.name).isEqualTo(projectId)
            .and(TBlockNode::repoName.name).isEqualTo(repoName)
            .and(TBlockNode::startPos.name).isEqualTo(offset)
        return blockNodeRepository.findOne(Query(criteria))
    }

    override suspend fun createBlock(blockNode: TBlockNode, repositoryDetail: RepositoryDetail): TBlockNode {
        with(blockNode) {
            val criteria = where(TBlockNode::nodeFullPath).isEqualTo(blockNode.nodeFullPath)
                .and(TBlockNode::projectId.name).isEqualTo(projectId)
                .and(TBlockNode::repoName.name).isEqualTo(repoName)
                .and(TBlockNode::startPos.name).isEqualTo(blockNode.startPos)

            val update = Update().set(TBlockNode::sha256.name, blockNode.sha256)
                .set(TBlockNode::size.name, blockNode.size)
                .set(TBlockNode::lastModifiedDate.name, blockNode.lastModifiedDate)
                .set(TBlockNode::lastModifiedBy.name, blockNode.lastModifiedBy)

            blockNodeRepository.upsert(Query(criteria), update)
            rRepositoryClient.increment(blockNode.sha256, repositoryDetail.storageCredentials?.key).awaitSingle()
            logger.info("Create block node[$projectId/$repoName/$nodeFullPath-$startPos] ,sha256[$sha256] success.")
            return blockNode
        }
    }

    override suspend fun deleteBlock(blockNode: TBlockNode, repositoryDetail: RepositoryDetail) {
        with(blockNode) {
            val criteria = where(TBlockNode::nodeFullPath).isEqualTo(blockNode.nodeFullPath)
                .and(TBlockNode::projectId.name).isEqualTo(projectId)
                .and(TBlockNode::repoName.name).isEqualTo(repoName)
                .and(TBlockNode::startPos.name).isEqualTo(blockNode.startPos)

            blockNodeRepository.remove(Query(criteria))
            rRepositoryClient.decrement(blockNode.sha256, repositoryDetail.storageCredentials?.key).awaitSingle()
            logger.info("Delete block node[$projectId/$repoName/$nodeFullPath-$startPos]")
        }
    }

    override suspend fun listBlocks(
        range: Range,
        projectId: String,
        repoName: String,
        fullPath: String
    ): List<TBlockNode> {
        val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
            .and(TBlockNode::projectId.name).isEqualTo(projectId)
            .and(TBlockNode::repoName.name).isEqualTo(repoName)
            .norOperator(
                TBlockNode::startPos.gte(range.end),
                TBlockNode::endPos.lte(range.start)
            )
        return blockNodeRepository.find(Query(criteria))
    }

    override suspend fun getLatestBlock(projectId: String, repoName: String, fullPath: String): TBlockNode? {
        val criteria = where(TBlockNode::nodeFullPath).isEqualTo(fullPath)
            .and(TBlockNode::projectId.name).isEqualTo(projectId)
            .and(TBlockNode::repoName.name).isEqualTo(repoName)
        val query = Query(criteria)
        query.with(Sort.by(TBlockNode::endPos.name).descending())
        return blockNodeRepository.findOne(query)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BlockNodeServiceImpl::class.java)
    }
}
