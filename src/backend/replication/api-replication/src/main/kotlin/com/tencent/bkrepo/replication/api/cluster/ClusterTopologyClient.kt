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

package com.tencent.bkrepo.replication.api.cluster

import com.tencent.bkrepo.common.api.constant.REPLICATION_SERVICE_NAME
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.replication.pojo.topology.TopologyNode
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeSyncRequest
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 拓扑追溯跨集群通信客户端：
 *  - 推送方调用 [syncUpstreamEdges] 把"指向接收方"的全部边快照下发给接收方；
 *  - 链式追溯调用 [expandUpstream] 取得对端节点的拓扑展开结果。
 *
 * 路径与对外用户接口完全一致（共用同一 controller），鉴权由现有 cluster 凭证拦截器处理。
 */
@RequestMapping("/api/replication/topology")
@FeignClient(REPLICATION_SERVICE_NAME, contextId = "ClusterTopologyClient")
interface ClusterTopologyClient {

    @PostMapping("/upstream-edges/sync")
    fun syncUpstreamEdges(
        @RequestBody request: UpstreamEdgeSyncRequest
    ): Response<Void>

    @GetMapping("/upstream")
    fun expandUpstream(
        @RequestParam(name = "maxDepth", required = false) maxDepth: Int? = null,
        @RequestParam(name = "includeDisabled", required = false) includeDisabled: Boolean? = null,
        @RequestParam(name = "_internal", required = false) internalCall: Boolean? = null,
        @RequestHeader(name = HEADER_TRACE_ID, required = false) traceId: String? = null,
        @RequestHeader(name = HEADER_DEPTH, required = false) depth: Int? = null
    ): Response<TopologyNode>

    companion object {
        const val HEADER_TRACE_ID = "X-Bkrepo-Topology-TraceId"
        const val HEADER_DEPTH = "X-Bkrepo-Topology-Depth"
    }
}
