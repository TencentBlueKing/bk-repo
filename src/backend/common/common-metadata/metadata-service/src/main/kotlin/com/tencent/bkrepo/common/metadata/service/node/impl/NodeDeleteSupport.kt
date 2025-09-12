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

package com.tencent.bkrepo.common.metadata.service.node.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.properties.RouterControllerProperties
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.common.metadata.dao.node.NodeDao
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodesDeleteRequest
import com.tencent.bkrepo.common.metadata.service.node.NodeDeleteOperation
import com.tencent.bkrepo.common.metadata.service.repo.QuotaService
import com.tencent.bkrepo.common.metadata.service.router.RouterControllerService
import com.tencent.bkrepo.common.metadata.util.NodeDeleteHelper.buildCriteria
import com.tencent.bkrepo.common.metadata.util.NodeEventFactory.buildDeletedEvent
import com.tencent.bkrepo.common.metadata.util.NodeEventFactory.buildNodeCleanEvent
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import com.tencent.bkrepo.common.mongo.constant.ID
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import java.time.LocalDateTime

/**
 * 节点删除接口实现
 */
open class NodeDeleteSupport(
    private val nodeBaseService: NodeBaseService
) : NodeDeleteOperation {

    val nodeDao: NodeDao = nodeBaseService.nodeDao
    val quotaService: QuotaService = nodeBaseService.quotaService
    val routerControllerService: RouterControllerService = nodeBaseService.routerControllerService
    val routerControllerProperties: RouterControllerProperties = nodeBaseService.routerControllerProperties

    override fun deleteNode(deleteRequest: NodeDeleteRequest): NodeDeleteResult {
        with(deleteRequest) {
            // 不允许直接删除根目录
            if (PathUtils.isRoot(fullPath)) {
                throw ErrorCodeException(CommonMessageCode.METHOD_NOT_ALLOWED, "Can't delete root node.")
            }
            return deleteByPath(projectId, repoName, fullPath, operator, source)
        }
    }

    override fun deleteNodes(nodesDeleteRequest: NodesDeleteRequest): NodeDeleteResult {
        with(nodesDeleteRequest) {
            if (fullPaths.isEmpty()) {
                throw ErrorCodeException(CommonMessageCode.PARAMETER_EMPTY, "fullPaths is empty.")
            }
            fullPaths.forEach {
                // 不允许直接删除根目录
                if (PathUtils.isRoot(it)) {
                    throw ErrorCodeException(CommonMessageCode.METHOD_NOT_ALLOWED, "Can't delete root node.")
                }
            }
            return deleteByPaths(projectId, repoName, fullPaths, operator)
        }
    }

    override fun countDeleteNodes(nodesDeleteRequest: NodesDeleteRequest): Long {
        with(nodesDeleteRequest) {
            if (fullPaths.isEmpty()) {
                return 0L
            }
            val orOperation = mutableListOf<Criteria>()
            val normalizedFullPaths = fullPaths.map { PathUtils.normalizeFullPath(it) }
            orOperation.add(where(TNode::fullPath).inValues(normalizedFullPaths))
            normalizedFullPaths.forEach {
                val normalizedPath = PathUtils.toPath(it)
                val escapedPath = PathUtils.escapeRegex(normalizedPath)
                orOperation.add(where(TNode::fullPath).regex("^$escapedPath"))
            }
            val criteria = where(TNode::projectId).isEqualTo(projectId)
                .and(TNode::repoName).isEqualTo(repoName)
                .and(TNode::deleted).isEqualTo(null)
                .and(TNode::folder).isEqualTo(isFolder)
                .orOperator(*orOperation.toTypedArray())
            return nodeDao.count(Query(criteria))
        }
    }

    override fun deleteByFullPathWithoutDecreaseVolume(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String
    ) {
        val criteria = buildCriteria(projectId, repoName, fullPath)
        val query = Query(criteria)
        delete(query, operator, criteria, projectId, repoName, listOf(fullPath), false)
    }

    override fun deleteByPath(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String,
        source: String?,
    ): NodeDeleteResult {
        val criteria = buildCriteria(projectId, repoName, fullPath)
        val query = Query(criteria)
        return delete(query, operator, criteria, projectId, repoName, listOf(fullPath), source = source)
    }

    override fun deleteByPaths(
        projectId: String,
        repoName: String,
        fullPaths: List<String>,
        operator: String
    ): NodeDeleteResult {
        val normalizedFullPaths = fullPaths.map { PathUtils.normalizeFullPath(it) }
        val orOperation = mutableListOf(
            where(TNode::fullPath).inValues(normalizedFullPaths)
        )
        normalizedFullPaths.forEach {
            val normalizedPath = PathUtils.toPath(it)
            val escapedPath = PathUtils.escapeRegex(normalizedPath)
            orOperation.add(where(TNode::fullPath).regex("^$escapedPath"))
        }
        val criteria = where(TNode::projectId).isEqualTo(projectId)
            .and(TNode::repoName).isEqualTo(repoName)
            .and(TNode::deleted).isEqualTo(null)
            .orOperator(*orOperation.toTypedArray())
        val query = Query(criteria)
        return delete(query, operator, criteria, projectId, repoName, fullPaths)
    }

    override fun deleteBeforeDate(
        projectId: String,
        repoName: String,
        date: LocalDateTime,
        operator: String,
        path: String,
        decreaseVolume: Boolean
    ): NodeDeleteResult {
        val option = NodeListOption(includeFolder = false, deep = true)
        val timeCriteria = Criteria().orOperator(
            Criteria().and(TNode::lastAccessDate).lt(date).and(TNode::lastModifiedDate).lt(date),
            Criteria().and(TNode::lastAccessDate).`is`(null).and(TNode::lastModifiedDate).lt(date),
        )
        val criteria = NodeQueryHelper.nodeListCriteria(projectId, repoName, path, option)
            .andOperator(timeCriteria)
        val query = Query(criteria)
        val nodeDeleteResult = delete(query, operator, criteria, projectId, repoName, decreaseVolume = decreaseVolume)
        publishEvent(
            buildNodeCleanEvent(
                projectId, repoName, path, operator, nodeDeleteResult.deletedTime.toString()
            )
        )
        return nodeDeleteResult
    }

    override fun deleteNodeById(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String,
        nodeId: String
    ): NodeDeleteResult {
        require(!PathUtils.isRoot(fullPath)) { "Cannot delete root node." }
        val deleteTime = LocalDateTime.now()

        val criteria = buildCriteria(projectId, repoName, fullPath).apply {
            and(ID).isEqualTo(nodeId)
        }
        val query = Query(criteria)

        // 删除旧节点
        val updateDefinition = NodeQueryHelper.nodeDeleteUpdate(operator, deleteTime)
        val updateResult = nodeDao.updateFirst(query, updateDefinition)
        val deletedNum = updateResult.modifiedCount

        if (deletedNum == 0L) return NodeDeleteResult(0L, 0L, deleteTime)

        publishEvent(buildDeletedEvent(projectId, repoName, fullPath, operator, deleteTime.toString(), null))
        logger.info(
            "Delete old block base node: $fullPath, operator: $operator, delete num : $deletedNum, " +
                    "delete time: $deleteTime success"
        )
        return NodeDeleteResult(deletedNum, 0, deleteTime)
    }

    private fun delete(
        query: Query,
        operator: String,
        criteria: Criteria,
        projectId: String,
        repoName: String,
        fullPaths: List<String>? = null,
        decreaseVolume: Boolean = true,
        source: String? = null
    ): NodeDeleteResult {
        var deletedNum = 0L
        var deletedSize = 0L
        val deleteTime = LocalDateTime.now()
        val resourceKey = if (fullPaths == null) {
            "/$projectId/$repoName"
        } else if (fullPaths.size == 1) {
            "/$projectId/$repoName${fullPaths[0]}"
        } else {
            "/$projectId/$repoName$fullPaths"
        }
        try {
            val updateResult = nodeDao.updateMulti(query, NodeQueryHelper.nodeDeleteUpdate(operator, deleteTime))
            deletedNum = updateResult.modifiedCount
            if (deletedNum == 0L) {
                return NodeDeleteResult(deletedNum, deletedSize, deleteTime)
            }
            if (decreaseVolume) {
                var deletedCriteria = criteria.and(TNode::deleted).isEqualTo(deleteTime)
                fullPaths?.let {
                    // 节点删除接口返回的数据排除目录
                    deletedCriteria = deletedCriteria.and(TNode::folder).isEqualTo(false)
                    deletedNum = nodeDao.count(Query(deletedCriteria))
                }
                deletedSize = nodeBaseService.aggregateComputeSize(deletedCriteria)
                quotaService.decreaseUsedVolume(projectId, repoName, deletedSize)
            }
            fullPaths?.forEach { fullPath ->
                if (routerControllerProperties.enabled) {
                    routerControllerService.removeNodes(projectId, repoName, fullPath)
                }
                publishEvent(buildDeletedEvent(projectId, repoName, fullPath, operator, deleteTime.toString(), source))
            }
        } catch (exception: DuplicateKeyException) {
            logger.warn("Delete node[$resourceKey] by [$operator] error: [${exception.message}]")
        }
        logger.info(
            "Delete node[$resourceKey] by [$operator] success." +
                "$deletedNum nodes have been deleted. The size is ${HumanReadable.size(deletedSize)}"
        )
        return NodeDeleteResult(deletedNum, deletedSize, deleteTime)
    }


    companion object {
        private val logger = LoggerFactory.getLogger(NodeDeleteSupport::class.java)
    }
}
