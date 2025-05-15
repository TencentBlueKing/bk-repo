/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.fs.server.service.node

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.util.NodeEventFactory.buildDeletedEvent
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import com.tencent.bkrepo.common.service.util.SpringContextUtils.Companion.publishEvent
import com.tencent.bkrepo.common.metadata.util.NodeDeleteHelper.buildCriteria
import com.tencent.bkrepo.repository.pojo.node.NodeDeleteResult
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import java.time.LocalDateTime

/**
 * 节点删除接口实现
 */
open class RNodeDeleteSupport(
    private val nodeBaseService: RNodeBaseService
) : RNodeDeleteOperation {

    private val nodeDao = nodeBaseService.nodeDao
    private val quotaService = nodeBaseService.quotaService

    override suspend fun deleteNode(deleteRequest: NodeDeleteRequest): NodeDeleteResult {
        with(deleteRequest) {
            // 不允许直接删除根目录
            if (PathUtils.isRoot(fullPath)) {
                throw ErrorCodeException(CommonMessageCode.METHOD_NOT_ALLOWED, "Can't delete root node.")
            }
            return deleteByPath(projectId, repoName, fullPath, operator)
        }
    }

    override suspend fun deleteByFullPathWithoutDecreaseVolume(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String
    ) {
        val criteria = buildCriteria(projectId, repoName, fullPath)
        val query = Query(criteria)
        delete(query, operator, criteria, projectId, repoName, listOf(fullPath), false)
    }

    override suspend fun deleteByPath(
        projectId: String,
        repoName: String,
        fullPath: String,
        operator: String
    ): NodeDeleteResult {
        val criteria = buildCriteria(projectId, repoName, fullPath)
        val query = Query(criteria)
        return delete(query, operator, criteria, projectId, repoName, listOf(fullPath))
    }

    private suspend fun delete(
        query: Query,
        operator: String,
        criteria: Criteria,
        projectId: String,
        repoName: String,
        fullPaths: List<String>? = null,
        decreaseVolume: Boolean = true
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
            fullPaths?.forEach {
                publishEvent(buildDeletedEvent(projectId, repoName, it, operator))
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
        private val logger = LoggerFactory.getLogger(RNodeDeleteSupport::class.java)
    }
}
