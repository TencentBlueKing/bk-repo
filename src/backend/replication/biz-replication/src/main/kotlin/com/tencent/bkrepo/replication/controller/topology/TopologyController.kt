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

package com.tencent.bkrepo.replication.controller.topology

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.api.cluster.ClusterTopologyClient
import com.tencent.bkrepo.replication.job.topology.UpstreamEdgeSyncJob
import com.tencent.bkrepo.replication.pojo.topology.TopologyNode
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeInfo
import com.tencent.bkrepo.replication.service.topology.TopologyService
import com.tencent.bkrepo.replication.service.topology.UpstreamEdgeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 链式拓扑追溯 - 查询 / 运维接口。
 *
 * 路径与跨集群 [ClusterTopologyClient] 共用 `/api/replication/topology/...`：
 *  - GET /upstream            链式追溯查询（用户 / 跨集群均可调用，由 _internal 区分）
 *  - GET /upstream-edges      运维：分页查看本地表
 *  - POST /sync-now           运维：立即触发一次同步
 */
@Tag(name = "拓扑追溯-查询接口")
@RestController
@RequestMapping("/api/replication/topology")
class TopologyController(
    private val topologyService: TopologyService,
    private val upstreamEdgeService: UpstreamEdgeService,
    private val upstreamEdgeSyncJob: UpstreamEdgeSyncJob
) {

    @Operation(summary = "链式追溯查询（向上游展开）")
    @GetMapping("/upstream")
    fun queryUpstream(
        @RequestParam(name = "maxDepth", required = false) maxDepth: Int? = null,
        @RequestParam(name = "includeDisabled", required = false, defaultValue = "false") includeDisabled: Boolean = false,
        @RequestParam(name = "_internal", required = false, defaultValue = "false") internalCall: Boolean = false,
        @RequestHeader(name = ClusterTopologyClient.HEADER_TRACE_ID, required = false) traceId: String? = null,
        @RequestHeader(name = ClusterTopologyClient.HEADER_DEPTH, required = false) depth: Int? = null
    ): Response<TopologyNode> {
        val node = topologyService.queryUpstream(
            maxDepth = maxDepth,
            includeDisabled = includeDisabled,
            internalCall = internalCall,
            traceId = traceId,
            depth = depth ?: 0
        )
        return ResponseBuilder.success(node)
    }

    @Operation(summary = "查看本节点 UpstreamEdge 表（运维接口）")
    @GetMapping("/upstream-edges")
    @Principal(PrincipalType.ADMIN)
    fun listUpstreamEdges(
        @RequestParam(name = "includeDisabled", required = false, defaultValue = "false") includeDisabled: Boolean = false,
        @RequestParam(name = "pageNumber", required = false, defaultValue = "1") pageNumber: Int = 1,
        @RequestParam(name = "pageSize", required = false, defaultValue = "50") pageSize: Int = 50
    ): Response<Page<UpstreamEdgeInfo>> {
        return ResponseBuilder.success(upstreamEdgeService.pageLocalEdges(includeDisabled, pageNumber, pageSize))
    }

    @Operation(summary = "立即触发一次拓扑同步（运维接口）")
    @PostMapping("/sync-now")
    @Principal(PrincipalType.ADMIN)
    fun syncNow(): Response<Map<String, Any?>> {
        val summary = upstreamEdgeSyncJob.runOnce()
        val payload = mapOf(
            "edgePullCount" to summary.edgePullCount,
            "federationLocalCount" to summary.federationLocalCount,
            "pushedEntries" to summary.pushedEntries,
            "pushSucceeded" to summary.pushSucceeded,
            "pushFailed" to summary.pushFailed,
            "pushSkippedLegacy" to summary.pushSkippedLegacy,
            "localFailed" to summary.localFailed,
            "remoteFailed" to summary.remoteFailed
        )
        return ResponseBuilder.success(payload)
    }
}
