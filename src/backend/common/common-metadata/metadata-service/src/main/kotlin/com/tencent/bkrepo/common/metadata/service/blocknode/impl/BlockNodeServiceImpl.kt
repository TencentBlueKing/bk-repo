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

package com.tencent.bkrepo.common.metadata.service.blocknode.impl

import com.tencent.bkrepo.common.artifact.exception.NodeNotFoundException
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.condition.SyncCondition
import com.tencent.bkrepo.common.metadata.constant.FAKE_SHA256
import com.tencent.bkrepo.common.metadata.constant.ID
import com.tencent.bkrepo.common.metadata.dao.blocknode.BlockNodeDao
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TBlockNode
import com.tencent.bkrepo.common.metadata.service.blocknode.BlockNodeService
import com.tencent.bkrepo.common.metadata.service.file.FileReferenceService
import com.tencent.bkrepo.common.metadata.util.BlockNodeQueryHelper
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo

import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Conditional(SyncCondition::class)
class BlockNodeServiceImpl(
    private val blockNodeDao: BlockNodeDao,
    private val fileReferenceService: FileReferenceService,
    private val nodeDao: NodeDao
) : BlockNodeService {

    override fun createBlock(blockNode: TBlockNode, storageCredentials: StorageCredentials?): TBlockNode {
        with(blockNode) {
            val bn = blockNodeDao.save(blockNode)
            fileReferenceService.increment(bn.sha256, storageCredentials?.key)
            logger.info("Create block node[$projectId/$repoName$nodeFullPath-$startPos] ,sha256[$sha256] success.")
            return bn
        }
    }

    override fun updateBlock(
        blockNode: TBlockNode,
        startPos: Long,
        endPos: Long,
    ) {
        with(blockNode) {
            val criteria = BlockNodeQueryHelper.fullPathCriteria(projectId, repoName, nodeFullPath,false)
                criteria.and(TBlockNode::id).isEqualTo(id)
                        .and(TBlockNode::version).isEqualTo(version)
            val update = BlockNodeQueryHelper.updateVersionBlocks(startPos, endPos)
            blockNodeDao.updateBlock(Query(criteria), update)
            logger.info("Update block node[$projectId/$repoName/$nodeFullPath-$startPos], sha256[$sha256] success.")
        }


    }

    override fun listBlocks(
        range: Range,
        projectId: String,
        repoName: String,
        fullPath: String,
        createdDate: String
    ): List<TBlockNode> {
        val query = BlockNodeQueryHelper.listQuery(projectId, repoName, fullPath, createdDate, range)
        return blockNodeDao.find(query)
    }

    override fun listBlocksInVersion(
        projectId: String,
        repoName: String,
        fullPath: String,
        createdDate: String?,
        version: String
    ): List<TBlockNode> {
        val query =
            BlockNodeQueryHelper.listQueryInVersion(projectId, repoName, fullPath, createdDate, version)
        return blockNodeDao.find(query)
    }

    override fun deleteBlocks(
        projectId: String,
        repoName: String,
        fullPath: String,
        version: String?
    ) {
        val criteria = BlockNodeQueryHelper.fullPathCriteria(projectId, repoName, fullPath, false)
            .apply { and(TBlockNode::version.name).isEqualTo(version) }
        val update = BlockNodeQueryHelper.deleteUpdate()
        blockNodeDao.updateMulti(Query(criteria), update)
        logger.info("Delete node blocks[$projectId/$repoName$fullPath] success. Version: $version")
    }

    override fun moveBlocks(projectId: String, repoName: String, fullPath: String, dstFullPath: String) {
        val node = nodeDao.findNode(projectId, repoName, dstFullPath)
            ?: throw NodeNotFoundException(dstFullPath)
        if (node.folder) {
            val criteria = BlockNodeQueryHelper.fullPathCriteria(projectId, repoName, fullPath, true)
            val blocks = blockNodeDao.find(Query(criteria))
            blocks.forEach {
                val update = Update().set(TBlockNode::nodeFullPath.name, it.nodeFullPath.replace(fullPath, dstFullPath))
                val query = Query(Criteria.where(ID).isEqualTo(it.id).and(TBlockNode::repoName).isEqualTo(repoName))
                blockNodeDao.updateMulti(query, update)
            }
        } else {
            val criteria = BlockNodeQueryHelper.fullPathCriteria(projectId, repoName, fullPath, false)
            val update = BlockNodeQueryHelper.moveUpdate(dstFullPath)
            blockNodeDao.updateMulti(Query(criteria), update)
        }
        logger.info("Move node[$projectId/$repoName$fullPath] to node[$projectId/$repoName$dstFullPath] success.")
    }



    override fun restoreBlocks(
        projectId: String,
        repoName: String,
        fullPath: String,
        nodeCreateDate: LocalDateTime,
        nodeDeleteDate: LocalDateTime
    ) {
        val criteria =
            BlockNodeQueryHelper.deletedCriteria(projectId, repoName, fullPath, nodeCreateDate, nodeDeleteDate)
        val update = BlockNodeQueryHelper.restoreUpdate()
        val result = blockNodeDao.updateMulti(Query(criteria), update)
        logger.info("Restore ${result.modifiedCount} blocks node[$projectId/$repoName$fullPath] " +
            "between $nodeCreateDate and $nodeDeleteDate success.")
    }

    override fun info(nodeDetail: NodeDetail, range: Range): List<RegionResource> {
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
        private val logger = LoggerFactory.getLogger(BlockNodeServiceImpl::class.java)
    }
}
