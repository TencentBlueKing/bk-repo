/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.common.metadata.service.node.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.common.metadata.service.node.NodeStatsOperation
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import org.springframework.data.mongodb.core.aggregation.Aggregation.group
import org.springframework.data.mongodb.core.aggregation.Aggregation.match
import org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import java.time.LocalDateTime

/**
 * 节点统计接口
 */
open class NodeStatsSupport(
    nodeBaseService: NodeBaseService
) : NodeStatsOperation {

    private val nodeDao: NodeDao = nodeBaseService.nodeDao

    override fun computeSize(
        artifact: ArtifactInfo,
        estimated: Boolean
    ): NodeSizeInfo {
        val node = getNodeInfo(artifact)
        // 节点为文件直接返回
        if (!node.folder) {
            return NodeSizeInfo(subNodeCount = 0, subNodeWithoutFolderCount = 0, size = node.size)
        }
        if (estimated) {
            return computeEstimatedSize(node)
        }
        val listOption = NodeListOption(includeFolder = true, deep = true)
        val criteria = NodeQueryHelper.nodeListCriteria(
            artifact.projectId,
            artifact.repoName,
            artifact.getArtifactFullPath(),
            listOption)
        val listOptionWithOutFolder = NodeListOption(includeFolder = false, deep = true)
        val criteriaWithOutFolder = NodeQueryHelper.nodeListCriteria(
            artifact.projectId,
            artifact.repoName,
            artifact.getArtifactFullPath(),
            listOptionWithOutFolder
        )
        val nodeSizeInfo = accurateSizeCalculate(criteria, criteriaWithOutFolder)
        nodeDao.setSizeAndNodeNumOfFolder(
                artifact.projectId,
                artifact.repoName,
                artifact.getArtifactFullPath(),
                size = nodeSizeInfo.size,
                nodeNum = nodeSizeInfo.subNodeWithoutFolderCount)
        return nodeSizeInfo
    }

    override fun computeSizeBeforeClean(artifact: ArtifactInfo, before: LocalDateTime): NodeSizeInfo {
        val nodeInfo = getNodeInfo(artifact)
        if (!nodeInfo.folder) {
            return NodeSizeInfo(subNodeCount = 0, subNodeWithoutFolderCount = 0, size = nodeInfo.size)
        }
        val listOption = NodeListOption(includeFolder = true, deep = true)
        val withoutFolderListOption = NodeListOption(includeFolder = false, deep = true)
        val timeCriteria =  Criteria().orOperator(
            Criteria().and(TNode::lastAccessDate).lt(before).and(TNode::lastModifiedDate).lt(before),
            Criteria().and(TNode::lastAccessDate).`is`(null).and(TNode::lastModifiedDate).lt(before),
        )
        val criteria = NodeQueryHelper.nodeListCriteria(
            artifact.projectId,
            artifact.repoName,
            artifact.getArtifactFullPath(),
            listOption).andOperator(timeCriteria)
        val criteriaWithOutFolder = NodeQueryHelper.nodeListCriteria(
            artifact.projectId,
            artifact.repoName,
            artifact.getArtifactFullPath(),
            withoutFolderListOption).andOperator(timeCriteria)
        return accurateSizeCalculate(criteria, criteriaWithOutFolder)
    }
    
    private fun getNodeInfo(artifact: ArtifactInfo): TNode {
        val projectId = artifact.projectId
        val repoName = artifact.repoName
        val fullPath = artifact.getArtifactFullPath()
        val node = nodeDao.findNode(projectId, repoName, fullPath)
            ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, fullPath)
        return node
    }

    /**
     * 计算大小
     */
    private fun accurateSizeCalculate(criteria: Criteria, criteriaWithOutFolder: Criteria):NodeSizeInfo {
        val count = nodeDao.count(Query(criteria))
        val countWithOutFolder = nodeDao.count(Query(criteriaWithOutFolder))
        val size = aggregateComputeSize(criteriaWithOutFolder)
        return NodeSizeInfo(subNodeCount = count, subNodeWithoutFolderCount = countWithOutFolder, size = size)
    }

    /**
     * 计算目录大小信息的估计值
     *
     */
    private fun computeEstimatedSize(node: TNode): NodeSizeInfo {
        val countCriteria = NodeQueryHelper.nodeListCriteria(
            projectId = node.projectId,
            repoName = node.repoName,
            path = node.fullPath,
            option = NodeListOption(includeFolder = true, deep = true)
        )
        val count = nodeDao.count(Query(countCriteria))
        if (node.fullPath != StringPool.ROOT) {
            return NodeSizeInfo(count, node.nodeNum ?: 0, node.size)
        }
        val criteria = NodeQueryHelper.nodeListCriteria(
            projectId = node.projectId,
            repoName = node.repoName,
            path = node.fullPath,
            option = NodeListOption(includeFolder = true, deep = false)
        )

        val aggregation = newAggregation(
            match(criteria),
            group().sum(TNode::size.name).`as`(NodeSizeInfo::size.name)
                .sum(TNode::nodeNum.name).`as`(NodeSizeInfo::subNodeCount.name)
        )
        val aggregateResult = nodeDao.aggregate(aggregation, HashMap::class.java)
        val data = aggregateResult.mappedResults.firstOrNull()
        return NodeSizeInfo(
            subNodeCount = count,
            subNodeWithoutFolderCount = data?.get(NodeSizeInfo::subNodeCount.name) as? Long ?: 0,
            size = data?.get(NodeSizeInfo::size.name) as? Long ?: 0
        )
    }

    override fun countFileNode(artifact: ArtifactInfo): Long {
        with(artifact) {
            val listOption = NodeListOption(
                includeFolder = false,
                includeMetadata = false,
                deep = true,
                sort = false
            )
            val query = NodeQueryHelper.nodeListQuery(projectId, repoName, getArtifactFullPath(), listOption)
            return nodeDao.count(query)
        }
    }

    override fun aggregateComputeSize(criteria: Criteria): Long {
        val aggregation = newAggregation(
            match(criteria),
            group().sum(TNode::size.name).`as`(NodeSizeInfo::size.name)
        )
        val aggregateResult = nodeDao.aggregate(aggregation, HashMap::class.java)
        return aggregateResult.mappedResults.firstOrNull()?.get(NodeSizeInfo::size.name) as? Long ?: 0
    }
}
