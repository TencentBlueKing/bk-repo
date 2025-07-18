/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.blocknode.impl

import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.constant.ID
import com.tencent.bkrepo.common.metadata.dao.blocknode.RBlockNodeDao
import com.tencent.bkrepo.common.metadata.dao.node.RNodeDao
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.service.blocknode.RBlockNodeService
import com.tencent.bkrepo.common.metadata.service.file.RFileReferenceService
import com.tencent.bkrepo.common.metadata.util.BlockNodeQueryHelper
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Conditional(ReactiveCondition::class)
class RBlockNodeServiceImpl(
    private val rBlockNodeDao: RBlockNodeDao,
    private val rFileReferenceService: RFileReferenceService,
    private val rNodeDao: RNodeDao
) : RBlockNodeService {

    override suspend fun createBlock(blockNode: TBlockNode, storageCredentials: StorageCredentials?): TBlockNode {
        with(blockNode) {
            val bn = rBlockNodeDao.save(blockNode)
            rFileReferenceService.increment(bn.sha256, storageCredentials?.key)
            logger.info("Create block node[$projectId/$repoName$nodeFullPath-$startPos] ,sha256[$sha256] success.")
            return bn
        }
    }

    override suspend fun listBlocks(
        range: Range,
        projectId: String,
        repoName: String,
        fullPath: String,
        createdDate: String
    ): List<TBlockNode> {
        val query = BlockNodeQueryHelper.listQuery(projectId, repoName, fullPath, createdDate, range)
        return rBlockNodeDao.find(query)
    }

    override suspend fun deleteBlocks(
        projectId: String,
        repoName: String,
        fullPath: String
    ) {
        val criteria = BlockNodeQueryHelper.fullPathCriteria(projectId, repoName, fullPath, false)
        val update = BlockNodeQueryHelper.deleteUpdate()
        rBlockNodeDao.updateMulti(Query(criteria), update)
        logger.info("Delete node blocks[$projectId/$repoName$fullPath] success.")
    }

    override suspend fun moveBlocks(projectId: String, repoName: String, fullPath: String, dstFullPath: String) {
        val nodeDetail = rNodeDao.findNode(projectId, repoName, dstFullPath) ?: throw NodeNotFoundException(dstFullPath)
        if (nodeDetail.folder) {
            val criteria = BlockNodeQueryHelper.fullPathCriteria(projectId, repoName, fullPath, true)
            val blocks = rBlockNodeDao.find(Query(criteria))
            blocks.forEach {
                val update = BlockNodeQueryHelper.moveUpdate(it.nodeFullPath.replace(fullPath, dstFullPath))
                val query = Query(Criteria.where(ID).isEqualTo(it.id).and(TBlockNode::repoName).isEqualTo(repoName))
                rBlockNodeDao.updateMulti(query, update)
            }
        } else {
            val criteria = BlockNodeQueryHelper.fullPathCriteria(projectId, repoName, fullPath, false)
            val update = BlockNodeQueryHelper.moveUpdate(dstFullPath)
            rBlockNodeDao.updateMulti(Query(criteria), update)
        }
        logger.info("Move node[$projectId/$repoName$fullPath] to node[$projectId/$repoName$dstFullPath] success.")
    }


    override suspend fun restoreBlocks(
        projectId: String,
        repoName: String,
        fullPath: String,
        nodeCreateDate: LocalDateTime,
        nodeDeleteDate: LocalDateTime
    ) {
        val criteria =
            BlockNodeQueryHelper.deletedCriteria(projectId, repoName, fullPath, nodeCreateDate, nodeDeleteDate)
        val update = BlockNodeQueryHelper.restoreUpdate()
        val result = rBlockNodeDao.updateMulti(Query(criteria), update)
        logger.info(
            "Restore ${result.modifiedCount} blocks node[$projectId/$repoName$fullPath] " +
                "between $nodeCreateDate and $nodeDeleteDate success."
        )
    }

    override suspend fun info(nodeDetail: NodeDetail, range: Range): List<RegionResource> {
        with(nodeDetail) {
            val blocks = listBlocks(range, projectId, repoName, fullPath, createdDate)
            val blockResources = mutableListOf<RegionResource>()
            if (sha256 != null && sha256 != FAKE_SHA256) {
                val nodeData = RegionResource(sha256!!, 0, size, 0, size)
                blockResources.add(nodeData)
            }
            blocks.forEach {
                val res = RegionResource(it.sha256, it.startPos, it.size, 0, it.size)
                blockResources.add(res)
            }
            return blockResources
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RBlockNodeService::class.java)
    }
}
