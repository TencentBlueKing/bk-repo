/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.migrate.utils

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.mongo.constant.ID
import com.tencent.bkrepo.common.mongo.dao.util.sharding.HashShardingUtils.shardingSequenceFor
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTask
import com.tencent.bkrepo.job.migrate.pojo.MigrateRepoStorageTaskState
import com.tencent.bkrepo.job.migrate.pojo.Node
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo

/**
 * 待迁移制品遍历工具
 *
 * @param task 迁移任务
 * @param mongoTemplate mongoTemplate
 * @param collectionName 遍历的collection
 * @param pageSize 分页大小
 */
class NodeIterator(
    private val task: MigrateRepoStorageTask,
    private val mongoTemplate: MongoTemplate,
    private val collectionName: String = "node_${shardingSequenceFor(task.projectId, SHARDING_COUNT)}",
    private val pageSize: Int = DEFAULT_PAGE_SIZE
) : Iterator<Node> {

    /**
     * 待遍历的游标
     */
    private var cursor: Int = 0

    /**
     * 上一页最后一个节点ID
     */
    private var lastNodeId: String

    /**
     * 需要遍历的制品总数
     */
    val totalCount: Long

    /**
     * 当前正在遍历的数据
     */
    private var data: List<Node>

    init {
        lastNodeId = task.lastMigratedNodeId
        totalCount = mongoTemplate.count(Query(buildCriteria()), collectionName)
        data = nextPage()
    }

    override fun hasNext() = cursor != data.size

    override fun next(): Node {
        if (cursor >= data.size) {
            throw NoSuchElementException()
        }
        val node = data[cursor++]

        // 遍历到最后一个数据时尝试拉取新列表
        if (cursor == pageSize) {
            data = nextPage()
            cursor = 0
        }

        return node
    }

    private fun nextPage(): List<Node> {
        val query = Query(buildCriteria(lastNodeId)).limit(pageSize).with(Sort.by(Sort.Direction.ASC, ID))
        val result = mongoTemplate.find(query, Node::class.java, collectionName)
        if (result.isNotEmpty()) {
            lastNodeId = result.last().id
        }
        return result
    }

    private fun buildCriteria(lastId: String? = null): Criteria {
        val criteria = Criteria
            .where(Node::projectId.name).isEqualTo(task.projectId)
            .and(Node::repoName.name).isEqualTo(task.repoName)
            .and(NodeDetail::folder.name).isEqualTo(false)
            .and(Node::sha256.name).ne(FAKE_SHA256)
        if (task.state == MigrateRepoStorageTaskState.CORRECTING.name) {
            criteria.and(NodeDetail::createdDate.name).gte(task.startDate!!)
        } else {
            criteria.and(NodeDetail::createdDate.name).lt(task.startDate!!)
        }
        lastId?.let { criteria.and(ID).gt(ObjectId(it)) }
        return criteria
    }
}
