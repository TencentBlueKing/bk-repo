/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.repository.controller.user

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadPlan
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategy
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategyCreateRequest
import com.tencent.bkrepo.common.artifact.cache.pojo.ArtifactPreloadStrategyUpdateRequest
import com.tencent.bkrepo.common.artifact.cache.pojo.PreloadStrategyType
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadPlanService
import com.tencent.bkrepo.common.artifact.cache.service.ArtifactPreloadStrategyService
import com.tencent.bkrepo.common.mongo.dao.util.Pages
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Api("制品预加载接口")
@RestController
@RequestMapping("/api/preload")
@ExperimentalContracts
class UserArtifactPreloadController(
    private val preloadStrategyService: ArtifactPreloadStrategyService?,
    private val preloadPlanService: ArtifactPreloadPlanService?,
    private val permissionManager: PermissionManager,
) {

    @ApiOperation("创建预加载策略")
    @PostMapping("/strategy")
    fun createStrategy(@RequestBody request: ArtifactPreloadStrategyCreateRequest): Response<ArtifactPreloadStrategy> {
        checkPreloadEnabled(preloadPlanService, preloadStrategyService)
        permissionManager.checkRepoPermission(PermissionAction.MANAGE, request.projectId, request.repoName)
        val strategy = preloadStrategyService.create(
            request.copy(operator = SecurityUtils.getUserId(), type = PreloadStrategyType.CUSTOM.name)
        )
        return ResponseBuilder.success(strategy)
    }

    @ApiOperation("更新预加载策略")
    @PutMapping("/strategy")
    fun updateStrategy(@RequestBody request: ArtifactPreloadStrategyUpdateRequest): Response<ArtifactPreloadStrategy> {
        checkPreloadEnabled(preloadPlanService, preloadStrategyService)
        permissionManager.checkRepoPermission(PermissionAction.MANAGE, request.projectId, request.repoName)
        val strategy = preloadStrategyService.update(request.copy(operator = SecurityUtils.getUserId()))
        return ResponseBuilder.success(strategy)
    }

    @ApiOperation("删除预加载策略")
    @DeleteMapping("/strategy/{projectId}/{repoName}/{id}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.MANAGE)
    fun deleteStrategy(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable id: String,
    ) {
        checkPreloadEnabled(preloadPlanService, preloadStrategyService)
        preloadStrategyService.delete(projectId, repoName, id)
    }

    @ApiOperation("获取所有预加载策略")
    @GetMapping("/strategy/{projectId}/{repoName}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    fun listStrategies(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
    ): Response<List<ArtifactPreloadStrategy>> {
        checkPreloadEnabled(preloadPlanService, preloadStrategyService)
        return ResponseBuilder.success(preloadStrategyService.list(projectId, repoName))
    }

    @ApiOperation("分页查询预加载计划")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/plan/{projectId}/{repoName}")
    fun pagePlans(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_NUMBER") pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @RequestParam(required = false, defaultValue = "$DEFAULT_PAGE_SIZE") pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Response<Page<ArtifactPreloadPlan>> {
        checkPreloadEnabled(preloadPlanService, preloadStrategyService)
        val page = preloadPlanService.plans(projectId, repoName, Pages.ofRequest(pageNumber, pageSize))
        return ResponseBuilder.success(page)
    }

    @ApiOperation("删除预加载计划")
    @DeleteMapping("/plan/{projectId}/{repoName}/{id}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.MANAGE)
    fun deletePlan(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
        @PathVariable id: String,
    ) {
        checkPreloadEnabled(preloadPlanService, preloadStrategyService)
        preloadPlanService.deletePlan(projectId, repoName, id)
    }

    @ApiOperation("删除仓库的所有预加载计划")
    @DeleteMapping("/plan/{projectId}/{repoName}")
    @Permission(type = ResourceType.REPO, action = PermissionAction.MANAGE)
    fun deleteAllPlans(
        @PathVariable projectId: String,
        @PathVariable repoName: String,
    ) {
        checkPreloadEnabled(preloadPlanService, preloadStrategyService)
        preloadPlanService.deletePlan(projectId, repoName)
    }

    private fun checkPreloadEnabled(
        preloadPlanService: ArtifactPreloadPlanService?,
        preloadStrategyService: ArtifactPreloadStrategyService?
    ) {
        contract {
            returns() implies (preloadPlanService != null && preloadStrategyService != null)
        }
        if (preloadPlanService == null || preloadStrategyService == null) {
            throw ErrorCodeException(CommonMessageCode.METHOD_NOT_ALLOWED, "artifact preload unsupported")
        }
    }
}
