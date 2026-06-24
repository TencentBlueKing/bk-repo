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

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.common.service.cluster.ClusterInfo
import com.tencent.bkrepo.common.service.cluster.properties.ClusterProperties
import com.tencent.bkrepo.replication.config.ReplicationProperties
import com.tencent.bkrepo.replication.config.TopologyProperties
import com.tencent.bkrepo.replication.metrics.TopologyMetrics
import com.tencent.bkrepo.replication.model.TUpstreamEdge
import com.tencent.bkrepo.replication.pojo.topology.TopologyNode
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeSourceType
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

/**
 * TopologyService 行为单元测试。
 *
 * 关注：
 *  - internalCall=true：只组装"根 + 本地一跳的 children"，不调 expand service；
 *  - internalCall=false：交给 expand service，并把指标记录下来；
 *  - 空表也返回合法响应（root + 空 children）。
 */
@DisplayName("TopologyService 单元测试")
class TopologyServiceTest {

    private val upstreamEdgeService: UpstreamEdgeService = mock()
    private val expandService: TopologyExpandService = mock()
    private val metrics: TopologyMetrics = mock()
    private val replicationProperties = ReplicationProperties().apply {
        topology = TopologyProperties()
    }
    private val clusterProperties = ClusterProperties(
        role = ClusterNodeType.STANDALONE,
        self = ClusterInfo(name = "self", url = "http://self")
    )
    private lateinit var service: TopologyService

    @BeforeEach
    fun setUp() {
        service = TopologyService(
            upstreamEdgeService,
            clusterProperties,
            replicationProperties,
            expandService,
            metrics
        )
    }

    @Test
    fun `internal call should compose root + local-one-hop without invoking expand service`() {
        whenever(upstreamEdgeService.listLocalEdges(any())).doReturn(
            listOf(
                edge("clusterA", "task1", UpstreamEdgeSourceType.REPLICA_PUSH),
                edge("clusterB", "task2", UpstreamEdgeSourceType.EDGE_PULL)
            )
        )

        val node = service.queryUpstream(internalCall = true)
        assertEquals("self", node.clusterName)
        assertEquals(2, node.children.size)
        assertTrue(node.children.all { it.children.isEmpty() })
        verify(expandService, never()).expandFromRoot(
            any(), any(), any(), any(), any(), any(), any()
        )
    }

    @Test
    fun `top-level call should delegate to expand service and record metrics`() {
        whenever(upstreamEdgeService.listLocalEdges(any())).doReturn(emptyList())
        whenever(
            expandService.expandFromRoot(any(), any(), any(), any(), any(), any(), any())
        ).doReturn(
            TopologyNode(clusterName = "self", depth = 0, children = emptyList())
        )

        val node = service.queryUpstream()
        assertNotNull(node)
        verify(expandService).expandFromRoot(any(), any(), any(), any(), any(), any(), any())
        verify(metrics).recordQueryDuration(org.mockito.kotlin.any())
        verify(metrics).recordQueryDepth(org.mockito.kotlin.any())
    }

    @Test
    fun `empty edges should still return a valid root response`() {
        whenever(upstreamEdgeService.listLocalEdges(any())).doReturn(emptyList())
        whenever(
            expandService.expandFromRoot(any(), any(), any(), any(), any(), any(), any())
        ).doReturn(
            TopologyNode(clusterName = "self", depth = 0, children = emptyList())
        )

        val node = service.queryUpstream()
        assertEquals("self", node.clusterName)
        assertTrue(node.children.isEmpty())
        assertFalse(node.unreachable)
    }

    private fun edge(
        upstream: String,
        taskKey: String,
        sourceType: UpstreamEdgeSourceType
    ): TUpstreamEdge {
        val now = LocalDateTime.now()
        return TUpstreamEdge(
            upstreamClusterName = upstream,
            replicaTaskKey = taskKey,
            replicaTaskName = taskKey,
            sourceType = sourceType,
            status = UpstreamEdgeStatus.ENABLED,
            lastSyncTime = now,
            createdDate = now,
            lastModifiedDate = now
        )
    }
}
