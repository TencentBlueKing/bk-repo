/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.service.node.impl.center

import com.tencent.bkrepo.common.api.constant.ensureSuffix
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.util.ClusterUtils
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeBaseService
import com.tencent.bkrepo.common.metadata.service.node.impl.NodeDeleteSupport
import com.tencent.bkrepo.common.metadata.util.NodeDeleteHelper.buildCriteria
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import org.bson.types.ObjectId
import org.jboss.logging.Logger
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime

class CenterNodeDeleteSupport(
    private val nodeBaseService: NodeBaseService,
    private val clusterProperties: ClusterProperties
): NodeDeleteSupport(
    nodeBaseService
) {

    override fun deleteByPath(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String,
        source: String?,
    ): NodeDeleteResult {
        val clusterName = SecurityUtils.getClusterName()
        if (clusterName.isNullOrEmpty()) {
            return super.deleteByPath(projectId, repoName, fullPath, operator, source)
        }
        val normalizedFullPath = PathUtils.normalizeFullPath(fullPath)
        val node = nodeDao.findNode(projectId, repoName, normalizedFullPath)
            ?: return NodeDeleteResult(0,0, LocalDateTime.now())
        if (node.folder) {
            return delete(node, operator)
        }

        return if (deleteFileNode(node, operator)) {
            NodeDeleteResult(1, node.size, LocalDateTime.now())
        } else {
            NodeDeleteResult(0, 0, LocalDateTime.now())
        }
    }

    override fun deleteByPaths(
        projectId: String,
        repoName: String,
        fullPaths: List<String>,
        operator: String
    ): NodeDeleteResult {
        var deletedNumber = 0L
        var deletedSize = 0L
        fullPaths.forEach {
            val result = deleteByPath(projectId, repoName, it, operator)
            deletedNumber += result.deletedNumber
            deletedSize += result.deletedSize
        }
        return NodeDeleteResult(deletedNumber, deletedSize, LocalDateTime.now())
    }

    override fun deleteBeforeDate(
        projectId: String,
        repoName: String,
        date: LocalDateTime,
        operator: String,
        path: String,
        decreaseVolume: Boolean
    ): NodeDeleteResult {
        val clusterName = SecurityUtils.getClusterName()
        if (clusterName.isNullOrEmpty()) {
            return super.deleteBeforeDate(projectId, repoName, date, operator, path, decreaseVolume)
        }
        var deletedSize = 0L
        var deletedNum = 0L
        val option = NodeListOption(includeFolder = false, deep = true)
        val timeCriteria = Criteria().orOperator(
            Criteria().and(TNode::lastAccessDate).lt(date).and(TNode::lastModifiedDate).lt(date),
            Criteria().and(TNode::lastAccessDate).`is`(null).and(TNode::lastModifiedDate).lt(date),
        )
        val criteria = NodeQueryHelper.nodeListCriteria(projectId, repoName, path, option)
            .andOperator(timeCriteria)
            .and(TNode::clusterNames).isEqualTo(clusterName)
        val pageSize = 1
        var queryCount: Int
        var lastId = ObjectId(MIN_OBJECT_ID)
        do {
            val query = Query(criteria).addCriteria(Criteria.where(ID).gt(lastId)).limit(pageSize)
                .with(Sort.by(Sort.Direction.ASC, TNode::id.name))
            val nodes = nodeDao.find(query)
            nodes.forEach {
                if (deleteFileNode(it, operator)) {
                    deletedNum ++
                    deletedSize += it.size
                }
            }
            queryCount = nodes.size
            if (nodes.isNotEmpty()) {
                lastId = ObjectId(nodes.last().id!!)
            }
        } while (queryCount == pageSize)
        logger.info(
            "Delete node [/$projectId/$repoName] created before $date by [$operator] success. " +
                "$deletedNum nodes have been deleted. The size is ${HumanReadable.size(deletedSize)}"
        )
        return NodeDeleteResult(deletedNum, deletedSize, LocalDateTime.now())
    }

    override fun deleteNodeById(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String,
        nodeId: String
    ): NodeDeleteResult {
        val clusterName = SecurityUtils.getClusterName()
        if (clusterName.isNullOrEmpty()) {
            return super.deleteNodeById(projectId, repoName, fullPath, operator, nodeId)
        }

        val criteria = buildCriteria(projectId, repoName, fullPath).apply {
            and(ID).isEqualTo(nodeId)
        }
        val node = nodeDao.findOne(Query(criteria))
            ?: return NodeDeleteResult(0,0, LocalDateTime.now())

        if (node.folder) {
            return delete(node, operator)
        }

        return if (deleteFileNode(node, operator, nodeId)) {
            NodeDeleteResult(1, node.size, LocalDateTime.now())
        } else {
            NodeDeleteResult(0, 0, LocalDateTime.now())
        }
    }

    private fun delete(folder: TNode, operator: String): NodeDeleteResult {
        var deletedNumber = 0L
        var deletedSize = 0L
        val criteria = where(TNode::projectId).isEqualTo(folder.projectId)
            .and(TNode::repoName).isEqualTo(folder.repoName)
            .and(TNode::deleted).isEqualTo(null)
            .and(TNode::path).isEqualTo(folder.fullPath.ensureSuffix("/"))
        val query = Query(criteria)
        var subNodes = nodeDao.find(query)
        subNodes.forEach {
            if (it.folder) {
                val result = delete(it, operator)
                deletedNumber += result.deletedNumber
                deletedSize += result.deletedSize
            } else {
                deleteFileNode(it, operator)
                deletedNumber ++
                deletedSize += it.size
            }
        }
        subNodes = nodeDao.find(query)
        if (subNodes.isEmpty()) {
            super.deleteByFullPathWithoutDecreaseVolume(
                projectId = folder.projectId,
                repoName = folder.repoName,
                fullPath = folder.fullPath,
                operator = operator
            )
        }
        return NodeDeleteResult(deletedNumber, deletedSize, LocalDateTime.now())
    }

    private fun deleteFileNode(
        node: TNode,
        operator: String,
        nodeId: String? = null
    ): Boolean {
        if (!ClusterUtils.containsSrcCluster(node.clusterNames)) {
            return false
        }
        val srcCluster = SecurityUtils.getClusterName() ?: clusterProperties.self.name.toString()
        node.clusterNames = node.clusterNames.orEmpty().minus(srcCluster)

        if (node.clusterNames.orEmpty().isNotEmpty()) {
            // 更新数据库中节点的 clusterNames
            val query = NodeQueryHelper.nodeQuery(node.projectId, node.repoName, node.fullPath)
            val update = Update().pull(TNode::clusterNames.name, srcCluster)
            nodeDao.updateFirst(query, update)
            return true
        }

        // 当 clusterNames 为空时，删除节点
        if (nodeId != null) {
            super.deleteNodeById(node.projectId, node.repoName, node.fullPath, operator, nodeId)
        } else {
            super.deleteByFullPathWithoutDecreaseVolume(node.projectId, node.repoName, node.fullPath, operator)
        }
        quotaService.decreaseUsedVolume(node.projectId, node.repoName, node.size)
        return true
    }

    companion object {
        private val logger = Logger.getLogger(CenterNodeDeleteSupport::class.java)
    }
}
