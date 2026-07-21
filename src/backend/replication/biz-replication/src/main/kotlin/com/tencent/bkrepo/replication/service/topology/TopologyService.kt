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

package com.tencent.bkrepo.replication.service.topology

import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.metrics.TopologyMetrics
import com.tencent.bkrepo.replication.model.TUpstreamEdge
import com.tencent.bkrepo.replication.pojo.topology.TopologyNode
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeStatus
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 链式追溯查询服务（叶子能力）。
 *
 * 仅负责"读本节点 t_upstream_edge → 组装当前节点为根的拓扑树"这一跳；
 * 跨集群递归展开下一层由 [TopologyExpandService] 完成（任务 6）。
 */
@Service
class TopologyService(
    private val upstreamEdgeService: UpstreamEdgeService,
    private val clusterProperties: ClusterProperties,
    private val replicationProperties: ReplicationProperties,
    private val topologyExpandService: TopologyExpandService,
    private val topologyMetrics: TopologyMetrics
) {

    /**
     * 单跳查询：返回以本节点为根、children 为本地 UpstreamEdge 表内容的拓扑树。
     *
     * @param maxDepth 期望的最大深度（含根节点本身）。1 表示只返回根节点 + 本地一跳；10 是上限。
     * @param includeDisabled 是否同时返回 status=DISABLED 的边
     * @param internalCall 内部跨集群 RPC 调用标记；true 时不再继续展开 children（由调用方递归驱动），
     *                     false 时由本节点主动驱动跨集群展开
     * @param traceId 跨集群追踪 id；为空时由本节点生成
     * @param depth 当前节点距离顶层根的深度，用于防超限与日志
     */
    fun queryUpstream(
        maxDepth: Int? = null,
        includeDisabled: Boolean = false,
        internalCall: Boolean = false,
        traceId: String? = null,
        depth: Int = 0
    ): TopologyNode {
        val started = System.nanoTime()
        val configuredMax = replicationProperties.topology.maxDepth
        val effectiveMax = (maxDepth ?: configuredMax).coerceIn(1, configuredMax)
        val finalTraceId = traceId ?: UUID.randomUUID().toString()

        val rootClusterName = clusterProperties.self.name ?: "<unknown-self>"
        val rootUrl = clusterProperties.self.url.takeIf { it.isNotBlank() }
        val rootType = clusterProperties.role

        val edges = upstreamEdgeService.listLocalEdges(includeDisabled)

        // 内部 RPC 调用：只组装"根 + 本地一跳的 children（无递归）"，由调用方自行递归
        if (internalCall) {
            val children = edges.map { edge ->
                edge.toTopologyChildLeaf(depth + 1, includeDisabled)
            }
            return TopologyNode(
                clusterName = rootClusterName,
                url = rootUrl,
                type = rootType,
                depth = depth,
                children = children
            )
        }

        // 顶层调用：交给 TopologyExpandService 驱动递归展开
        val tree = topologyExpandService.expandFromRoot(
            rootClusterName = rootClusterName,
            rootUrl = rootUrl,
            rootType = rootType,
            localEdges = edges,
            maxDepth = effectiveMax,
            includeDisabled = includeDisabled,
            traceId = finalTraceId
        )
        topologyMetrics.recordQueryDuration(System.nanoTime() - started)
        topologyMetrics.recordQueryDepth(tree.maxReachedDepth())
        return tree
    }

    /**
     * 计算一棵已展开的拓扑树实际到达的最大深度。
     */
    private fun TopologyNode.maxReachedDepth(): Int {
        if (children.isEmpty()) return depth
        return children.maxOf { it.maxReachedDepth() }
    }

    /**
     * 内部 RPC 模式下，把本地一条边渲染成不再展开的子节点。
     */
    private fun TUpstreamEdge.toTopologyChildLeaf(childDepth: Int, includeDisabled: Boolean): TopologyNode {
        return TopologyNode(
            clusterName = upstreamClusterName,
            replicaTaskKey = replicaTaskKey,
            sourceType = sourceType,
            status = status,
            depth = childDepth,
            disabled = includeDisabled && status == UpstreamEdgeStatus.DISABLED,
            lastSyncTime = lastSyncTime
        )
    }
}
