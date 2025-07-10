/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.controller.user

import com.tencent.bkrepo.analyst.component.ScannerPermissionCheckHandler
import com.tencent.bkrepo.analyst.pojo.ScanPlan
import com.tencent.bkrepo.analyst.pojo.request.ArtifactPlanRelationRequest
import com.tencent.bkrepo.analyst.pojo.request.CreateScanPlanRequest
import com.tencent.bkrepo.analyst.pojo.request.PlanCountRequest
import com.tencent.bkrepo.analyst.pojo.request.SubtaskInfoRequest
import com.tencent.bkrepo.analyst.pojo.request.UpdateScanPlanRequest
import com.tencent.bkrepo.analyst.pojo.response.ArtifactPlanRelations
import com.tencent.bkrepo.analyst.pojo.response.ScanLicensePlanInfo
import com.tencent.bkrepo.analyst.pojo.response.ScanPlanInfo
import com.tencent.bkrepo.analyst.pojo.response.SubtaskInfo
import com.tencent.bkrepo.analyst.service.ScanPlanCorrectService
import com.tencent.bkrepo.analyst.service.ScanPlanService
import com.tencent.bkrepo.analyst.service.ScanTaskService
import com.tencent.bkrepo.analyst.utils.ScanPlanConverter
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/scan/plan")
class UserScanPlanController(
    private val scanPlanService: ScanPlanService,
    private val scanPlanCorrectService: ScanPlanCorrectService,
    private val scanTaskService: ScanTaskService,
    private val permissionCheckHandler: ScannerPermissionCheckHandler
) {

    @Operation(summary = "创建扫描方案")
    @PostMapping("/create")
    fun createScanPlan(@RequestBody request: CreateScanPlanRequest): Response<Boolean> {
        permissionCheckHandler.checkProjectPermission(request.projectId, PermissionAction.MANAGE)
        val scanPlan = ScanPlanConverter.convert(request)
        scanPlanService.create(scanPlan)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "查询扫描方案基础信息")
    @GetMapping("/detail/{projectId}/{id}")
    fun getScanPlan(
        @Parameter(name = "projectId")
        @PathVariable
        projectId: String,
        @Parameter(name = "方案id")
        @PathVariable
        id: String
    ): Response<ScanPlan?> {
        permissionCheckHandler.checkProjectPermission(projectId, PermissionAction.MANAGE)
        return ResponseBuilder.success(scanPlanService.find(projectId, id))
    }

    @Operation(summary = "删除扫描方案")
    @DeleteMapping("/delete/{projectId}/{id}")
    fun deleteScanPlan(
        @Parameter(name = "projectId")
        @PathVariable projectId: String,
        @Parameter(name = "方案id")
        @PathVariable id: String
    ): Response<Boolean> {
        permissionCheckHandler.checkProjectPermission(projectId, PermissionAction.MANAGE)
        scanPlanService.delete(projectId, id)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "更新扫描方案")
    @PostMapping("/update")
    fun updateScanPlan(@RequestBody request: UpdateScanPlanRequest): Response<Boolean> {
        scanPlanService.update(request)
        return ResponseBuilder.success(true)
    }

    @Operation(summary = "扫描方案列表-分页")
    @GetMapping("/list/{projectId}")
    fun scanPlanList(
        @Parameter(name = "projectId", required = true)
        @PathVariable
        projectId: String,
        @Parameter(name = "方案类型")
        @RequestParam
        type: String?,
        @Parameter(name = "方案名")
        @RequestParam
        name: String?,
        @Parameter(name = "页数", required = false)
        @RequestParam(required = false, defaultValue = DEFAULT_PAGE_NUMBER.toString())
        pageNumber: Int = DEFAULT_PAGE_NUMBER,
        @Parameter(name = "每页数量", required = false)
        @RequestParam(required = false, defaultValue = DEFAULT_PAGE_SIZE.toString())
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Response<Page<ScanPlanInfo>> {
        permissionCheckHandler.checkProjectPermission(projectId, PermissionAction.MANAGE)
        val page = scanPlanService.page(
            projectId = projectId, type = type, planNameContains = name, pageLimit = PageLimit(pageNumber, pageSize)
        )
        return ResponseBuilder.success(page)
    }

    @Operation(summary = "所有扫描方案")
    @GetMapping("/all/{projectId}")
    fun scanPlanList(
        @Parameter(name = "projectId", required = true)
        @PathVariable
        projectId: String,
        @Parameter(name = "方案类型")
        @RequestParam
        type: String?,
        @Parameter(name = "待扫描文件名后缀，该参数尽在type为GENERIC时有效")
        @RequestParam(required = false)
        fileNameExt: String? = null
    ): Response<List<ScanPlan>> {
        permissionCheckHandler.checkProjectPermission(projectId, PermissionAction.READ)
        val planList = scanPlanService.list(projectId, type, fileNameExt)
        planList.forEach { ScanPlanConverter.keepProps(it, KEEP_PROPS) }
        return ResponseBuilder.success(planList)
    }

    @Operation(summary = "方案详情-统计数据")
    @GetMapping("/count")
    fun planDetailCount(countRequest: PlanCountRequest): Response<ScanPlanInfo?> {
        // TODO 等前端流水线扫描报告移除调用该接口后改会项目管理员权限
        permissionCheckHandler.checkProjectPermission(countRequest.projectId, PermissionAction.READ)
        return ResponseBuilder.success(scanPlanService.scanPlanInfo(ScanPlanConverter.convert(countRequest)))
    }

    @Operation(summary = "校正扫描方案预览信息数据")
    @PostMapping("/count/correct")
    @Principal(type = PrincipalType.ADMIN)
    fun correctPlanOverview(@RequestParam(required = false) planId: String? = null): Response<Any?> {
        scanPlanCorrectService.correctPlanOverview(planId)
        return ResponseBuilder.success()
    }

    @Operation(summary = "方案详情-制品信息")
    @GetMapping("/artifact")
    fun planArtifactSubtaskList(subtaskInfoRequest: SubtaskInfoRequest): Response<Page<SubtaskInfo>> {
        permissionCheckHandler.checkProjectPermission(subtaskInfoRequest.projectId, PermissionAction.MANAGE)
        return ResponseBuilder.success(
            scanTaskService.planArtifactSubtaskPage(ScanPlanConverter.convert(subtaskInfoRequest))
        )
    }

    @Operation(summary = "扫描方案数据导出")
    @GetMapping("/export")
    fun planScanRecordExport(subtaskInfoRequest: SubtaskInfoRequest) {
        permissionCheckHandler.checkProjectPermission(subtaskInfoRequest.projectId, PermissionAction.MANAGE)
        scanTaskService.exportScanPlanRecords(ScanPlanConverter.convert(subtaskInfoRequest))
    }

    @Operation(summary = "文件/包关联的扫描方案列表")
    @GetMapping("/relation/artifact")
    fun artifactPlanList(
        artifactRequest: ArtifactPlanRelationRequest
    ): Response<ArtifactPlanRelations> {
        return ResponseBuilder.success(scanPlanService.artifactPlanList(artifactRequest))
    }

    @Operation(summary = "方案详情-许可-统计数据")
    @GetMapping("/license/count")
    fun planLicenseDetailCount(countRequest: PlanCountRequest): Response<ScanLicensePlanInfo?> {
        permissionCheckHandler.checkProjectPermission(countRequest.projectId, PermissionAction.MANAGE)
        return ResponseBuilder.success(scanPlanService.scanLicensePlanInfo(ScanPlanConverter.convert(countRequest)))
    }

    companion object {
        private val KEEP_PROPS = listOf(ScanPlan::id, ScanPlan::name)
    }
}
