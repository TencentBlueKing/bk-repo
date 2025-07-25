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

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.federation.FederatedRepositoryInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederatedRepositoryCreateRequest
import com.tencent.bkrepo.replication.service.FederationRepositoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 联邦仓库配置接口
 */
@Tag(name = "联邦仓库接口")
@RestController
@RequestMapping("/api/federation")
class UserFederationRepositoryController(
    private val federationRepositoryService: FederationRepositoryService,
    private val permissionManager: PermissionManager,
) {


    @Operation(summary = "创建联邦仓库")
    @PostMapping("/create")
    fun federatedRepositoryCreate(
        @RequestBody requests: FederatedRepositoryCreateRequest,
    ): Response<String> {
        permissionManager.checkRepoPermission(PermissionAction.WRITE, requests.projectId, requests.repoName)
        return ResponseBuilder.success(federationRepositoryService.createFederationRepository(requests))

    }

    @Operation(summary = "删除联邦仓库")
    @DeleteMapping("/delete/{projectId}/{repoName}/{federationId}")
    fun federatedRepositoryDelete(
        @PathVariable("projectId") projectId: String,
        @PathVariable("repoName") repoName: String,
        @PathVariable("federationId") federationId: String,
    ): Response<Void> {
        permissionManager.checkRepoPermission(PermissionAction.WRITE, projectId, repoName)
        federationRepositoryService.deleteFederationRepositoryConfig(projectId, repoName, federationId)
        return ResponseBuilder.success()
    }


    @Operation(summary = "查询联邦仓库")
    @GetMapping("/list/{projectId}/{repoName}")
    fun federatedRepositoryQuery(
        @PathVariable("projectId") projectId: String,
        @PathVariable("repoName") repoName: String,
        @RequestParam("federationId") federationId: String? = null,
    ): Response<List<FederatedRepositoryInfo>> {
        permissionManager.checkRepoPermission(PermissionAction.READ, projectId, repoName)
        return ResponseBuilder.success(
            federationRepositoryService.listFederationRepository(projectId, repoName, federationId)
        )
    }
}