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

package com.tencent.bkrepo.replication.pojo.topology

import com.tencent.bkrepo.common.api.pojo.ClusterNodeType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 链式追溯节点
 *
 * 表示拓扑树中的一个节点；children 即该节点的上游集合（向上一层）。
 * 当 children 为空数组时，该节点是当前已展开链路的终点（可能因深度耗尽、上游为空、
 * unreachable 或 legacy 等原因停止）。
 */
@Schema(title = "链式追溯节点")
data class TopologyNode(
    @Schema(title = "节点 ClusterNode 名称（根节点为本节点）")
    val clusterName: String,
    @Schema(title = "节点访问 URL；本节点取自 cluster.self.url，远端取自本地 t_cluster_node")
    val url: String? = null,
    @Schema(title = "节点类型 standalone / edge / center")
    val type: ClusterNodeType? = null,
    @Schema(title = "与父节点的连接边 key（即上游 ReplicaTask / FederatedRepository key）；根节点为空")
    val replicaTaskKey: String? = null,
    @Schema(title = "与父节点连接边的来源类型")
    val sourceType: UpstreamEdgeSourceType? = null,
    @Schema(title = "与父节点连接边的状态")
    val status: UpstreamEdgeStatus? = null,
    @Schema(title = "该节点距离根节点的深度（根节点为 0）")
    val depth: Int = 0,
    @Schema(title = "因深度耗尽截断")
    val truncated: Boolean = false,
    @Schema(title = "RPC 失败 / 节点不可达")
    val unreachable: Boolean = false,
    @Schema(title = "RPC 失败的简述")
    val unreachableReason: String? = null,
    @Schema(title = "走到一个老版本上游节点（不支持本特性接口），停止继续展开")
    val legacy: Boolean = false,
    @Schema(title = "该边为禁用状态（仅在 includeDisabled=true 时返回）")
    val disabled: Boolean = false,
    @Schema(title = "上游节点列表，即拓扑树中本节点的子节点")
    val children: List<TopologyNode> = emptyList(),
    @Schema(title = "本节点最后被覆盖的时间")
    val lastSyncTime: LocalDateTime? = null
)
