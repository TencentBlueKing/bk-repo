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

package com.tencent.bkrepo.opdata.cluster.topology.controller

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ChannelTrafficTrendPoint
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ChannelTrafficTrendVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ChannelTrafficVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.NodeTrafficSummaryVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.RemoteAggregationVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TopologyChannelVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TopologyNodeVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TopologyVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TrafficGranularity
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TrafficPeriod
import com.tencent.bkrepo.opdata.cluster.topology.service.TopologyService
import com.tencent.bkrepo.opdata.cluster.topology.service.TrafficStatsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime

/**
 * ClusterTopologyController standalone MockMvc 测试。
 *
 * 重点验证接口路由、参数绑定与 service 调用契约；权限拦截 (@Principal) 由全量 SpringBoot
 * 上下文集成测试覆盖，此处不重复模拟。
 */
internal class ClusterTopologyControllerTest {

    private lateinit var topologyService: TopologyService
    private lateinit var trafficStatsService: TrafficStatsService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        topologyService = mock()
        trafficStatsService = mock()
        val controller = ClusterTopologyController(topologyService, trafficStatsService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `getTopology should merge traffic into channels and return success`() {
        val skeleton = TopologyVO(
            nodes = listOf(
                TopologyNodeVO(
                    id = "center-sz",
                    name = "center-sz",
                    url = "http://a",
                    type = ClusterNodeType.CENTER,
                    status = "HEALTHY",
                    errorReason = null,
                    lastReportTime = null,
                    region = null,
                    networkZone = null,
                    displayName = "center-sz",
                    description = null
                )
            ),
            channels = listOf(
                TopologyChannelVO(
                    id = "center-sz->edge-hk",
                    sourceCluster = "center-sz",
                    targetCluster = "edge-hk",
                    replicaTypes = setOf("REAL_TIME"),
                    totalTaskCount = 1,
                    activeTaskCount = 1,
                    allDisabled = false,
                    recentTrafficBytes = null
                )
            ),
            remoteSummary = RemoteAggregationVO(0, 0, 0)
        )
        whenever(topologyService.buildTopology(false)).thenReturn(skeleton)
        whenever(trafficStatsService.aggregateChannelTraffic(eq(TrafficPeriod.LAST_24H))).thenReturn(
            listOf(
                ChannelTrafficVO(
                    sourceCluster = "center-sz",
                    targetCluster = "edge-hk",
                    totalBytes = 1024L,
                    successCount = 5L
                )
            )
        )

        mockMvc.perform(get("/api/cluster/topology"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.channels[0].recentTrafficBytes").value(1024))
            .andExpect(jsonPath("$.data.channels[0].sourceCluster").value("center-sz"))
            .andExpect(jsonPath("$.data.nodes[0].name").value("center-sz"))

        verify(topologyService).buildTopology(false)
    }

    @Test
    fun `trafficChannels should map period parameter and return list`() {
        whenever(trafficStatsService.aggregateChannelTraffic(eq(TrafficPeriod.LAST_7D))).thenReturn(
            listOf(
                ChannelTrafficVO("a", "b", 100L, 1L)
            )
        )

        mockMvc.perform(get("/api/cluster/topology/traffic/channels").param("period", "7d"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].sourceCluster").value("a"))
            .andExpect(jsonPath("$.data[0].totalBytes").value(100))

        verify(trafficStatsService).aggregateChannelTraffic(TrafficPeriod.LAST_7D)
    }

    @Test
    fun `trafficChannelTrend should pass parsed parameters to service`() {
        whenever(
            trafficStatsService.getChannelTrend(
                eq("a"),
                eq("b"),
                any(),
                any(),
                eq(TrafficGranularity.HOUR)
            )
        ).thenReturn(
            ChannelTrafficTrendVO(
                sourceCluster = "a",
                targetCluster = "b",
                granularity = TrafficGranularity.HOUR,
                points = listOf(ChannelTrafficTrendPoint(LocalDateTime.now(), 200L, 2L)),
                totalBytes = 200L,
                totalSuccessCount = 2L
            )
        )

        mockMvc.perform(
            get("/api/cluster/topology/traffic/channel/trend")
                .param("source", "a")
                .param("target", "b")
                .param("startTime", "2024-01-01T00:00:00")
                .param("endTime", "2024-01-01T01:00:00")
                .param("granularity", "hour")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalBytes").value(200))
            .andExpect(jsonPath("$.data.granularity").value("HOUR"))
    }

    @Test
    fun `nodeTraffic should call service with cluster name and period`() {
        whenever(trafficStatsService.getNodeTrafficSummary(eq("center-sz"), eq(TrafficPeriod.LAST_24H))).thenReturn(
            NodeTrafficSummaryVO("center-sz", 5L, 7L)
        )

        mockMvc.perform(get("/api/cluster/topology/node/center-sz/traffic"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.outboundBytes").value(5))
            .andExpect(jsonPath("$.data.inboundBytes").value(7))

        verify(trafficStatsService).getNodeTrafficSummary("center-sz", TrafficPeriod.LAST_24H)
    }
}
