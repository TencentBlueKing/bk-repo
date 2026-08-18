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

package com.tencent.bkrepo.opdata.cluster.topology.pojo

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 拓扑视图：节点 + 通道 + REMOTE 聚合。
 */
@Schema(title = "集群拓扑视图")
data class TopologyVO(
    @get:Schema(title = "长期组网节点列表")
    val nodes: List<TopologyNodeVO>,
    @get:Schema(title = "节点间组网通道列表")
    val channels: List<TopologyChannelVO>,
    @get:Schema(title = "REMOTE 节点聚合统计")
    val remoteSummary: RemoteAggregationVO
)

@Schema(title = "拓扑节点")
data class TopologyNodeVO(
    @get:Schema(title = "节点唯一 ID（与 name 一致）")
    val id: String,
    @get:Schema(title = "节点名称")
    val name: String,
    @get:Schema(title = "节点访问地址")
    val url: String?,
    @get:Schema(title = "节点类型")
    val type: ClusterNodeType,
    @get:Schema(title = "健康状态：HEALTHY / UNHEALTHY 等")
    val status: String?,
    @get:Schema(title = "失败原因，仅 UNHEALTHY 时有值")
    val errorReason: String?,
    @get:Schema(title = "最近心跳时间")
    val lastReportTime: LocalDateTime?,
    @get:Schema(title = "地域，元数据")
    val region: String?,
    @get:Schema(title = "网络区域，元数据")
    val networkZone: String?,
    @get:Schema(title = "展示名，元数据")
    val displayName: String?,
    @get:Schema(title = "描述，元数据")
    val description: String?
)

@Schema(title = "拓扑通道（边）")
data class TopologyChannelVO(
    @get:Schema(title = "通道唯一 ID，由 source->target 组成")
    val id: String,
    @get:Schema(title = "源集群名称")
    val sourceCluster: String,
    @get:Schema(title = "目标集群名称")
    val targetCluster: String,
    @get:Schema(title = "通道承载的同步类型集合")
    val replicaTypes: Set<String>,
    @get:Schema(title = "通道任务总数")
    val totalTaskCount: Int,
    @get:Schema(title = "活跃（enabled=true）任务数")
    val activeTaskCount: Int,
    @get:Schema(title = "通道是否全部任务都已停用")
    val allDisabled: Boolean,
    @get:Schema(title = "默认时段（最近 24h）流量字节数；为 null 时表示未加载或加载失败")
    val recentTrafficBytes: Long? = null
)

@Schema(title = "REMOTE 节点聚合统计")
data class RemoteAggregationVO(
    @get:Schema(title = "REMOTE 节点总数")
    val remoteNodeCount: Long,
    @get:Schema(title = "活跃 REMOTE 任务数（enabled=true）")
    val activeRemoteTaskCount: Long,
    @get:Schema(title = "已完成 REMOTE 任务数（enabled=false 或最近无新执行）")
    val completedRemoteTaskCount: Long
)
