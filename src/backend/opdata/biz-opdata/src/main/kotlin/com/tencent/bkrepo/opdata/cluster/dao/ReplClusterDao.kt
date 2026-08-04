/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.opdata.cluster.dao

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.replication.pojo.cluster.ClusterNodeRecord
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository

/**
 * replication 模块 cluster_node 集合的只读 DAO。
 *
 * 由 op 模块直接读取该集合用于动态推导拓扑，不参与写操作；
 * 反序列化目标 [ClusterNodeRecord] 来自 replication-api 的只读 pojo（无 @Document），
 * 集合名通过覆写 [collectionName] 显式声明。
 */
@Repository
class ReplClusterDao : SimpleMongoDao<ClusterNodeRecord>() {

    override val collectionName: String = ClusterNodeRecord.COLLECTION_NAME

    /**
     * 列出所有节点。
     */
    fun listAll(): List<ClusterNodeRecord> {
        return find(Query())
    }

    /**
     * 根据节点类型筛选。
     */
    fun listByType(type: ClusterNodeType): List<ClusterNodeRecord> {
        return find(Query(ClusterNodeRecord::type.isEqualTo(type)))
    }

    /**
     * 根据节点类型集合筛选。
     */
    fun listByTypes(types: Collection<ClusterNodeType>): List<ClusterNodeRecord> {
        if (types.isEmpty()) return emptyList()
        val criteria = Criteria.where(ClusterNodeRecord::type.name).`in`(types)
        return find(Query(criteria))
    }

    /**
     * 统计指定类型节点数量。
     */
    fun countByType(type: ClusterNodeType): Long {
        return count(Query(ClusterNodeRecord::type.isEqualTo(type)))
    }

    /**
     * 按名称查询单个节点。
     */
    fun findByName(name: String): ClusterNodeRecord? {
        return findOne(Query(ClusterNodeRecord::name.isEqualTo(name)))
    }

    /**
     * 按 REMOTE 类型分页查询节点，支持模糊关键字。
     *
     * keyword 同时匹配 name 和 url。
     */
    fun pageRemoteNodes(
        keyword: String?,
        pageNumber: Int,
        pageSize: Int
    ): List<ClusterNodeRecord> {
        val query = buildRemoteQuery(keyword)
            .with(Sort.by(Sort.Direction.ASC, ClusterNodeRecord::name.name))
            .with(PageRequest.of(pageNumber - 1, pageSize))
        return find(query)
    }

    /**
     * 统计满足关键字过滤条件的 REMOTE 节点数量。
     */
    fun countRemoteNodes(keyword: String?): Long {
        return count(buildRemoteQuery(keyword))
    }

    private fun buildRemoteQuery(keyword: String?): Query {
        val criteria = Criteria.where(ClusterNodeRecord::type.name).`is`(ClusterNodeType.REMOTE)
        if (!keyword.isNullOrBlank()) {
            val regex = java.util.regex.Pattern.quote(keyword)
            criteria.orOperator(
                Criteria.where(ClusterNodeRecord::name.name).regex(regex),
                Criteria.where(ClusterNodeRecord::url.name).regex(regex)
            )
        }
        return Query(criteria)
    }
}
