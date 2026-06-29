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

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ChannelTrafficTrendVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.ChannelTrafficVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.NodeTrafficSummaryVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TopologyChannelVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TopologyVO
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TrafficGranularity
import com.tencent.bkrepo.opdata.cluster.topology.pojo.TrafficPeriod
import com.tencent.bkrepo.opdata.cluster.topology.service.TopologyService
import com.tencent.bkrepo.opdata.cluster.topology.service.TrafficStatsService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 集群拓扑查询接口。
 *
 * 仅对 ADMIN 开放，所有耗时超过 [SLOW_LOG_THRESHOLD_MS] 的请求会记录 WARN 日志，
 * 便于后续容量评估与性能调优。
 */
@RestController
@RequestMapping("/api/cluster/topology")
@Principal(PrincipalType.ADMIN)
class ClusterTopologyController(
    private val topologyService: TopologyService,
    private val trafficStatsService: TrafficStatsService
) {

    /**
     * 拓扑骨架数据 + 默认时段（最近 24h）的通道流量摘要。
     */
    @GetMapping
    fun getTopology(
        @RequestParam(name = "onlyEnabled", required = false, defaultValue = "false") onlyEnabled: Boolean,
        @RequestParam(name = "trafficPeriod", required = false) trafficPeriod: String?
    ): Response<TopologyVO> {
        val start = System.currentTimeMillis()
        val skeleton = topologyService.buildTopology(onlyEnabled)

        // 组合默认时段流量到通道
        val period = TrafficPeriod.parse(trafficPeriod)
        val trafficByChannel: Map<Pair<String, String>, ChannelTrafficVO> = trafficStatsService
            .aggregateChannelTraffic(period)
            .associateBy { it.sourceCluster to it.targetCluster }

        val mergedChannels: List<TopologyChannelVO> = skeleton.channels.map { channel ->
            val key = channel.sourceCluster to channel.targetCluster
            val trafficBytes = trafficByChannel[key]?.totalBytes
            channel.copy(recentTrafficBytes = trafficBytes)
        }
        val merged = skeleton.copy(channels = mergedChannels)

        warnIfSlow("getTopology", start)
        return ResponseBuilder.success(merged)
    }

    /**
     * 通道流量汇总：所有通道在指定时段的成功流量字节数。
     */
    @GetMapping("/traffic/channels")
    fun trafficChannels(
        @RequestParam(name = "period", required = false) period: String?
    ): Response<List<ChannelTrafficVO>> {
        val start = System.currentTimeMillis()
        val result = trafficStatsService.aggregateChannelTraffic(TrafficPeriod.parse(period))
        warnIfSlow("trafficChannels[period=$period]", start)
        return ResponseBuilder.success(result)
    }

    /**
     * 单条通道的流量趋势：自适应粒度（≤24h hour，≤7d day，更长 day 降采样），最大跨度 90 天。
     */
    @GetMapping("/traffic/channel/trend")
    fun trafficChannelTrend(
        @RequestParam("source") source: String,
        @RequestParam("target") target: String,
        @RequestParam("startTime") startTime: String,
        @RequestParam("endTime") endTime: String,
        @RequestParam(name = "granularity", required = false) granularity: String?
    ): Response<ChannelTrafficTrendVO> {
        val start = System.currentTimeMillis()
        val begin = parseLocalDateTime(startTime)
        val end = parseLocalDateTime(endTime)
        val result = trafficStatsService.getChannelTrend(
            source = source,
            target = target,
            startTime = begin,
            endTime = end,
            granularity = TrafficGranularity.parse(granularity)
        )
        warnIfSlow("trafficChannelTrend[$source->$target]", start)
        return ResponseBuilder.success(result)
    }

    /**
     * 节点出入流量汇总。
     */
    @GetMapping("/node/{clusterName}/traffic")
    fun nodeTraffic(
        @PathVariable("clusterName") clusterName: String,
        @RequestParam(name = "period", required = false) period: String?
    ): Response<NodeTrafficSummaryVO> {
        val start = System.currentTimeMillis()
        val result = trafficStatsService.getNodeTrafficSummary(clusterName, TrafficPeriod.parse(period))
        warnIfSlow("nodeTraffic[$clusterName]", start)
        return ResponseBuilder.success(result)
    }

    private fun parseLocalDateTime(raw: String): LocalDateTime {
        return runCatching { LocalDateTime.parse(raw) }
            .getOrElse { LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }
    }

    private fun warnIfSlow(name: String, startMillis: Long) {
        val cost = System.currentTimeMillis() - startMillis
        if (cost > SLOW_LOG_THRESHOLD_MS) {
            logger.warn("[topology] slow query: api={}, cost={}ms", name, cost)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClusterTopologyController::class.java)
        private const val SLOW_LOG_THRESHOLD_MS: Long = 3_000
    }
}
