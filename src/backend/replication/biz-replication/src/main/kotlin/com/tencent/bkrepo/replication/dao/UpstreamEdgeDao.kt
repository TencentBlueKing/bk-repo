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

package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.replication.model.TUpstreamEdge
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeSourceType
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 上游边数据访问层
 */
@Repository
open class UpstreamEdgeDao : SimpleMongoDao<TUpstreamEdge>() {

    /**
     * 删除指定上游集群下、指定来源类型集合内的全部边。
     * 用于"先删后插"的远程快照覆盖逻辑。
     */
    open fun deleteByUpstreamAndSourceTypes(
        upstreamClusterName: String,
        sourceTypes: Collection<UpstreamEdgeSourceType>
    ): Long {
        if (sourceTypes.isEmpty()) return 0
        val criteria = Criteria
            .where(TUpstreamEdge::upstreamClusterName.name).`is`(upstreamClusterName)
            .and(TUpstreamEdge::sourceType.name).`in`(sourceTypes)
        return remove(Query(criteria)).deletedCount
    }

    /**
     * 删除全部 EDGE_PULL 边（本地直写覆盖前的清理）。
     */
    open fun deleteAllByEdgePull(): Long {
        val criteria = Criteria.where(TUpstreamEdge::sourceType.name).`is`(UpstreamEdgeSourceType.EDGE_PULL)
        return remove(Query(criteria)).deletedCount
    }

    /**
     * 删除指定上游集群范围内的 FEDERATION 镜像边（本地直写覆盖前的清理）。
     * 范围限定在"本节点所属联邦成员集"内，避免误删远端推送方写入的 FEDERATION 记录。
     */
    open fun deleteFederationByUpstreams(upstreamClusterNames: Collection<String>): Long {
        if (upstreamClusterNames.isEmpty()) return 0
        val criteria = Criteria
            .where(TUpstreamEdge::sourceType.name).`is`(UpstreamEdgeSourceType.FEDERATION)
            .and(TUpstreamEdge::upstreamClusterName.name).`in`(upstreamClusterNames)
        return remove(Query(criteria)).deletedCount
    }

    /**
     * 按 (upstreamClusterName, replicaTaskKey, sourceType) 三元组 upsert 单条记录。
     */
    open fun upsert(edge: TUpstreamEdge) {
        val criteria = Criteria
            .where(TUpstreamEdge::upstreamClusterName.name).`is`(edge.upstreamClusterName)
            .and(TUpstreamEdge::replicaTaskKey.name).`is`(edge.replicaTaskKey)
            .and(TUpstreamEdge::sourceType.name).`is`(edge.sourceType)
        val now = LocalDateTime.now()
        val update = Update()
            .set(TUpstreamEdge::replicaTaskName.name, edge.replicaTaskName)
            .set(TUpstreamEdge::status.name, edge.status)
            .set(TUpstreamEdge::lastSyncTime.name, edge.lastSyncTime)
            .set(TUpstreamEdge::lastModifiedDate.name, now)
            .setOnInsert(TUpstreamEdge::createdDate.name, edge.createdDate)
        upsert(Query(criteria), update)
    }

    /**
     * 批量 upsert
     */
    open fun batchUpsert(edges: Collection<TUpstreamEdge>) {
        edges.forEach { upsert(it) }
    }

    /**
     * 列出本地全部边（按状态过滤）。
     */
    open fun listByStatus(status: UpstreamEdgeStatus? = null): List<TUpstreamEdge> {
        val query = if (status == null) {
            Query()
        } else {
            Query(TUpstreamEdge::status.isEqualTo(status))
        }
        return find(query)
    }

    /**
     * 分页查询本地全部边。
     */
    open fun page(includeDisabled: Boolean, pageable: Pageable): List<TUpstreamEdge> {
        val query = if (includeDisabled) {
            Query()
        } else {
            Query(TUpstreamEdge::status.isEqualTo(UpstreamEdgeStatus.ENABLED))
        }
        return find(query.with(pageable))
    }

    /**
     * 分页计数
     */
    open fun count(includeDisabled: Boolean): Long {
        val query = if (includeDisabled) {
            Query()
        } else {
            Query(TUpstreamEdge::status.isEqualTo(UpstreamEdgeStatus.ENABLED))
        }
        return count(query)
    }
}
