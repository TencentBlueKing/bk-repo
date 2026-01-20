/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.replication.controller.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.pojo.federation.FederationDiffStats
import com.tencent.bkrepo.replication.pojo.federation.FederationNodeCount
import com.tencent.bkrepo.replication.pojo.federation.FederationPathDiff
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryCreateRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederationDiffStatsRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryDeleteRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryUpdateRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederationDiffRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederationPathDiffRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederationSmartDiffRequest
import com.tencent.bkrepo.replication.service.FederationRepositoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 联邦仓库配置接口
 */
@Tag(name = "联邦仓库接口")
@RestController
@Principal(type = PrincipalType.ADMIN)

@RequestMapping("/api/federation")
class UserFederationRepositoryController(
    private val federationRepositoryService: FederationRepositoryService,
) {


    @Operation(summary = "创建联邦仓库")
    @PostMapping("/create")
    fun federatedRepositoryCreate(
        @RequestBody request: FederatedRepositoryCreateRequest,
    ): Response<String> {
        return ResponseBuilder.success(federationRepositoryService.createFederationRepository(request))

    }

    @Operation(summary = "更新联邦仓库配置")
    @PutMapping("/update")
    fun federatedRepositoryUpdate(
        @RequestBody request: FederatedRepositoryUpdateRequest,
    ): Response<Boolean> {
        return ResponseBuilder.success(federationRepositoryService.updateFederationRepository(request))
    }

    @Operation(summary = "删除联邦仓库（解散整个联邦）")
    @DeleteMapping("/delete/{projectId}/{repoName}/{federationId}")
    fun federatedRepositoryDelete(
        @PathVariable("projectId") projectId: String,
        @PathVariable("repoName") repoName: String,
        @PathVariable("federationId") federationId: String,
    ): Response<Void> {
        federationRepositoryService.deleteFederationRepositoryConfig(projectId, repoName, federationId)
        return ResponseBuilder.success()
    }

    @Operation(summary = "从联邦中移除指定集群")
    @DeleteMapping("/remove-cluster")
    fun removeClusterFromFederation(
        @RequestBody request: FederatedRepositoryDeleteRequest
    ): Response<Boolean> {
        federationRepositoryService.removeClusterFromFederation(request)
        return ResponseBuilder.success()
    }


    @Operation(summary = "查询联邦仓库")
    @GetMapping("/list/{projectId}/{repoName}")
    fun federatedRepositoryQuery(
        @PathVariable("projectId") projectId: String,
        @PathVariable("repoName") repoName: String,
        @RequestParam("federationId") federationId: String? = null,
    ): Response<List<FederatedRepositoryInfo>> {
        return ResponseBuilder.success(
            federationRepositoryService.listFederationRepository(projectId, repoName, federationId)
        )
    }


    @Operation(summary = "联邦仓库fullsync")
    @PostMapping("/fullSync/{projectId}/{repoName}/{federationId}")
    fun federatedRepositoryFullSync(
        @PathVariable("projectId") projectId: String,
        @PathVariable("repoName") repoName: String,
        @PathVariable("federationId") federationId: String,
    ): Response<Void> {
        federationRepositoryService.fullSyncFederationRepository(projectId, repoName, federationId)
        return ResponseBuilder.success()
    }

    @Operation(summary = "更新全量同步结束状态")
    @PutMapping("/fullSync/end/{projectId}/{repoName}/{federationId}")
    fun updateFullSyncEnd(
        @PathVariable("projectId") projectId: String,
        @PathVariable("repoName") repoName: String,
        @PathVariable("federationId") federationId: String,
    ): Response<Void> {
        federationRepositoryService.updateFullSyncEnd(projectId, repoName, federationId)
        return ResponseBuilder.success()
    }

    // ===================== 联邦仓库差异对比接口（分层目录对比） =====================
    // 推荐使用流程：
    // 1. 先调用 /diff/count 快速检查总数是否一致
    //    - 对于 generic 仓库：对比节点数量
    //    - 对于非 generic 仓库（maven、npm、docker 等）：对比 package 数量
    // 2. 如不一致，调用 /diff/path 从根目录 "/" 开始逐层对比
    //    - 对于 generic 仓库：对比节点层级
    //    - 对于非 generic 仓库：对比 package → version 层级
    // 3. 找到不一致的目录后，将 path 改为该目录继续对比
    // 4. 最终定位到具体不一致的文件或 version

    @Operation(summary = "对比联邦仓库制品数量（最轻量级）", description = "对于 generic 仓库对比节点数量，对于非 generic 仓库对比 package 数量")
    @PostMapping("/diff/count")
    fun compareFederationNodeCount(
        @RequestBody request: FederationDiffRequest,
    ): Response<List<FederationNodeCount>> {
        return ResponseBuilder.success(federationRepositoryService.compareFederationNodeCount(request))
    }

    @Operation(summary = "分层差异对比", description = "对于 generic 仓库按目录层级对比，对于非 generic 仓库按 package/version 层级对比")
        @PostMapping("/diff/path")
    fun compareFederationPathDiff(
        @RequestBody request: FederationPathDiffRequest,
    ): Response<FederationPathDiff> {
        return ResponseBuilder.success(federationRepositoryService.compareFederationPathDiff(request))
    }

    @Operation(summary = "多层目录聚合差异对比（推荐，一次请求获取多层统计，支持1-3层深度）")
    @PostMapping("/diff/stats")
    fun compareFederationDiffStats(
        @RequestBody request: FederationDiffStatsRequest,
    ): Response<FederationDiffStats> {
        return ResponseBuilder.success(federationRepositoryService.compareFederationDiffStats(request))
    }

    @Operation(summary = "智能差异对比（最推荐，自动定位不一致路径）")
    @PostMapping("/diff/smart")
    fun smartCompareFederationDiff(
        @RequestBody request: FederationSmartDiffRequest,
    ): Response<List<String>> {
        return ResponseBuilder.success(
            federationRepositoryService.smartCompareFederationDiff(
                projectId = request.projectId,
                repoName = request.repoName,
                federationId = request.federationId,
                targetClusterId = request.targetClusterId,
                rootPath = request.rootPath,
                maxDepth = request.maxDepth
            )
        )
    }
}