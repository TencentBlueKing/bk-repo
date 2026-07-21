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

package com.tencent.bkrepo.replication.model

import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeSourceType
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeStatus
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 上游边
 *
 * 记录"哪些节点直接指向我"，是链式拓扑追溯的本地一跳来源。
 * 每个节点本地维护一份；通过周期性快照同步（推送类 ReplicaTask / FederatedRepository）
 * 与本地直写（EDGE_PULL / FEDERATION 镜像侧）两条路径写入。
 */
@Document("upstream_edge")
@CompoundIndexes(
    CompoundIndex(
        name = "upstream_edge_idx",
        def = "{'upstreamClusterName': 1, 'replicaTaskKey': 1, 'sourceType': 1}",
        unique = true,
        background = true
    )
)
data class TUpstreamEdge(
    var id: String? = null,
    /**
     * 上游集群（ClusterNode）名称
     */
    @Indexed(background = true)
    var upstreamClusterName: String,
    /**
     * 上游 ReplicaTask / FederatedRepository 的唯一 key
     * - 推送类 ReplicaTask：取 ReplicaTask.key
     * - EDGE_PULL ReplicaTask：取本地 ReplicaTask.key
     * - FEDERATION：取 federationId（或 federationId:repoFqn）
     */
    var replicaTaskKey: String,
    /**
     * 上游 ReplicaTask / FederatedRepository 的名称（仅供展示）
     */
    var replicaTaskName: String? = null,
    /**
     * 边的来源类型
     */
    var sourceType: UpstreamEdgeSourceType,
    /**
     * 边的状态
     */
    var status: UpstreamEdgeStatus = UpstreamEdgeStatus.ENABLED,
    /**
     * 最近一次被快照覆盖（或本地直写）的时间
     */
    var lastSyncTime: LocalDateTime,
    var createdDate: LocalDateTime,
    var lastModifiedDate: LocalDateTime
)
