/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.metadata.dao.node

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.model.TNode
import com.tencent.bkrepo.common.metadata.util.NodeQueryHelper
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.mongo.reactive.dao.HashShardingMongoReactiveDao
import com.tencent.bkrepo.repository.pojo.node.NodeListOption
import org.springframework.context.annotation.Conditional
import org.springframework.data.domain.Page
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.and
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Repository
import java.time.LocalDateTime


/**
 * 节点 Dao
 */
@Repository
@Conditional(ReactiveCondition::class)
class RNodeDao: HashShardingMongoReactiveDao<TNode>() {
    /**
     * 查询节点
     */
    suspend fun findNode(projectId: String, repoName: String, fullPath: String): TNode? {
        // 系统设计上不保存根目录节点到数据库，但是有用户会手动创建根目录节点
        return this.findOne(NodeQueryHelper.nodeQuery(projectId, repoName, fullPath))
            ?: if (PathUtils.isRoot(fullPath)) buildRootNode(projectId, repoName) else null
    }

    /**
     * 查询节点是否存在
     */
    suspend fun exists(projectId: String, repoName: String, fullPath: String): Boolean {
        if (PathUtils.isRoot(fullPath)) {
            return true
        }
        return this.exists(NodeQueryHelper.nodeQuery(projectId, repoName, fullPath))
    }

    /**
     * 更新目录下变更的文件数量以及涉及的文件大小
     */
    suspend fun incSizeAndNodeNumOfFolder(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        nodeNum: Long
    ) {
        val query = NodeQueryHelper.nodeFolderQuery(projectId, repoName, fullPath)
        val update = Update()
            .inc(TNode::size.name, size)
            .inc(TNode::nodeNum.name, nodeNum)
            .set(TNode::lastModifiedDate.name, LocalDateTime.now())
        val options = FindAndModifyOptions()
        options.returnNew(true)
        val tNode = this.findAndModify(query, update, options, TNode::class.java)
        if (tNode != null && (tNode.nodeNum!! < 0L || tNode.size < 0L )) {
            // 如果数据为负数，将其设置为 0
            val updateMax = Update()
                .max(TNode::size.name, 0L)
                .max(TNode::nodeNum.name, 0L)
            this.updateFirst(query, updateMax)
        }
    }

    /**
     * 设置目录下的文件数量以及文件大小
     */
    suspend fun setSizeAndNodeNumOfFolder(
        projectId: String,
        repoName: String,
        fullPath: String,
        size: Long,
        nodeNum: Long
    ) {
        val query = NodeQueryHelper.nodeFolderQuery(projectId, repoName, fullPath)
        val update = Update().set(TNode::size.name, size)
            .set(TNode::nodeNum.name, nodeNum)
        this.updateFirst(query, update)
    }


    /**
     * 根据[sha256]分页查询节点，需要遍历所有分表
     *
     * @param includeDeleted 是否包含被删除的节点
     */
    suspend fun pageBySha256(
        sha256: String,
        option: NodeListOption,
        includeDeleted: Boolean = false
    ): Page<TNode> {
        val pageRequest = Pages.ofRequest(option.pageNumber, option.pageSize)

        // 构造查询条件
        val criteria = where(TNode::sha256).isEqualTo(sha256).and(TNode::folder).isEqualTo(false)
        if (!includeDeleted) {
            criteria.and(TNode::deleted).isEqualTo(null)
        }
        val query = Query(criteria)
        if (!option.includeMetadata) {
            query.fields().exclude(TNode::metadata.name)
        }

        return pageWithoutShardingKey(pageRequest, query)
    }

    companion object {
        fun buildRootNode(projectId: String, repoName: String): TNode {
            return TNode(
                createdBy = StringPool.EMPTY,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = StringPool.EMPTY,
                lastModifiedDate = LocalDateTime.now(),
                projectId = projectId,
                repoName = repoName,
                folder = true,
                path = PathUtils.ROOT,
                name = StringPool.EMPTY,
                fullPath = PathUtils.ROOT,
                size = 0
            )
        }
    }
}
