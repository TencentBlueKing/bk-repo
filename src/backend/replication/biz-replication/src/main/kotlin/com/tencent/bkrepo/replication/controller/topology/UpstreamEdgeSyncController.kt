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

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.topology.UpstreamEdgeSyncRequest
import com.tencent.bkrepo.replication.service.topology.UpstreamEdgeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 链式拓扑追溯 - 上游边快照接收接口。
 *
 * 接收来自其它集群的快照同步请求，单次请求会原子覆盖
 * (upstreamClusterName=请求体声明的上游, sourceType=REPLICA_PUSH) 范围的旧记录，
 * 严格不触碰本节点本地直写的 EDGE_PULL / FEDERATION 记录。
 *
 * FEDERATION 边由各成员节点本地直写维护（联邦创建/更新时已在每个成员节点写入完整副本），
 * 不再走远程推送路径，所以本接口不接收也不应包含 FEDERATION 条目。
 *
 * 鉴权：复用现有 cluster 凭证拦截器（cluster.token / ak-sk 签名），
 * 同时本接口会额外校验请求体 upstreamClusterName 与凭证身份（X-BKREPO-MS-CLUSTER）一致，
 * 防止伪造他人身份的快照。
 */
@Tag(name = "拓扑追溯-快照接收")
@RestController
@RequestMapping("/api/replication/topology")
class UpstreamEdgeSyncController(
    private val upstreamEdgeService: UpstreamEdgeService
) {

    @Operation(summary = "接收上游边快照同步")
    @PostMapping("/upstream-edges/sync")
    fun syncUpstreamEdges(
        @RequestBody request: UpstreamEdgeSyncRequest
    ): Response<Void> {
        verifyAuthenticatedSource(request.upstreamClusterName)
        upstreamEdgeService.applyRemoteSnapshot(request.upstreamClusterName, request.entries)
        return ResponseBuilder.success()
    }

    /**
     * 校验请求载荷里的 upstreamClusterName 与签名身份一致。
     *
     * 跨集群调用方会在 [com.tencent.bkrepo.common.api.constant.MS_REQUEST_SRC_CLUSTER] header
     * 中携带自己的集群名（由 FeignClientFactory.createInterceptor 写入），server 端通过
     * [SecurityUtils.getClusterName] 读取。两者不一致时丢弃并写审计日志。
     *
     * 当来源 header 缺失（比如本节点自调用、运维人员通过浏览器调用）时不抛异常，由上游 service
     * 的"必须在本地 ClusterNode 已注册"做兜底约束。
     */
    private fun verifyAuthenticatedSource(declaredUpstream: String) {
        val signedClusterName = SecurityUtils.getClusterName()
        if (!signedClusterName.isNullOrBlank() && signedClusterName != declaredUpstream) {
            logger.warn(
                "[Audit] reject upstream-edge snapshot: payload upstream=[$declaredUpstream] " +
                    "but signature identity=[$signedClusterName]"
            )
            throw IllegalArgumentException(
                "payload upstreamClusterName does not match cluster signature identity"
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UpstreamEdgeSyncController::class.java)
    }
}
