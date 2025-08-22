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
import com.tencent.bkrepo.analyst.pojo.ScanTask
import com.tencent.bkrepo.analyst.pojo.ScanTriggerType
import com.tencent.bkrepo.analyst.pojo.request.GlobalScanRequest
import com.tencent.bkrepo.analyst.pojo.request.PipelineScanRequest
import com.tencent.bkrepo.analyst.pojo.request.ScanRequest
import com.tencent.bkrepo.analyst.pojo.request.ScanTaskQuery
import com.tencent.bkrepo.analyst.pojo.request.SubtaskInfoRequest
import com.tencent.bkrepo.analyst.pojo.response.FileLicensesResultOverview
import com.tencent.bkrepo.analyst.pojo.response.SubtaskInfo
import com.tencent.bkrepo.analyst.pojo.response.SubtaskResultOverview
import com.tencent.bkrepo.analyst.service.ScanService
import com.tencent.bkrepo.analyst.service.ScanTaskService
import com.tencent.bkrepo.analyst.utils.ScanPlanConverter
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.exception.BadRequestException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "扫描接口")
@RestController
@RequestMapping("/api/scan")
class UserScanController @Autowired constructor(
    private val scanService: ScanService,
    private val scanTaskService: ScanTaskService,
    private val permissionCheckHandler: ScannerPermissionCheckHandler
) {

    @Operation(summary = "手动创建全局扫描任务")
    @PostMapping("/global")
    @Principal(PrincipalType.ADMIN)
    fun globalScan(@RequestBody scanRequest: GlobalScanRequest): Response<ScanTask> {
        return ResponseBuilder.success(scanService.globalScan(scanRequest))
    }

    @Operation(summary = "手动创建扫描任务")
    @PostMapping
    fun scan(@RequestBody scanRequest: ScanRequest): Response<ScanTask> {
        return ResponseBuilder.success(scanService.scan(scanRequest, ScanTriggerType.MANUAL, SecurityUtils.getUserId()))
    }

    @Operation(summary = "从流水线触发扫描")
    @PostMapping("/pipeline")
    fun pipelineScan(@RequestBody request: PipelineScanRequest): Response<ScanTask> {
        return ResponseBuilder.success(scanService.pipelineScan(request))
    }

    @Operation(summary = "中止制品扫描")
    @PostMapping("/{projectId}/stop")
    fun stopScan(
        @Parameter(name = "projectId")
        @PathVariable projectId: String,
        @Parameter(name = "记录id")
        @RequestParam("recordId") subtaskId: String?,
        @Parameter(name = "方案id")
        @RequestParam("id") planId: String?
    ): Response<Boolean> {
        permissionCheckHandler.checkProjectPermission(projectId, PermissionAction.MANAGE)
        return when {
            !subtaskId.isNullOrBlank() -> {
                ResponseBuilder.success(scanService.stopByPlanArtifactLatestSubtaskId(projectId, subtaskId))
            }
            !planId.isNullOrBlank() -> {
                ResponseBuilder.success(scanService.stopScanPlan(projectId, planId))
            }
            else -> {
                throw BadRequestException(CommonMessageCode.PARAMETER_INVALID)
            }
        }
    }

    @Operation(summary = "中止制品扫描")
    @PostMapping("/{projectId}/tasks/{taskId}/stop")
    fun stopTask(
        @Parameter(name = "projectId")
        @PathVariable projectId: String,
        @Parameter(name = "任务id")
        @PathVariable("taskId") taskId: String
    ): Response<Boolean> {
        permissionCheckHandler.checkProjectPermission(projectId, PermissionAction.MANAGE)
        return ResponseBuilder.success(scanService.stopTask(projectId, taskId))
    }

    @Operation(summary = "中止制品扫描")
    @PostMapping("/tasks/{taskId}/stop")
    @Principal(PrincipalType.ADMIN)
    fun stopTask(
        @Parameter(name = "任务id")
        @PathVariable("taskId") taskId: String
    ): Response<Boolean> {
        return ResponseBuilder.success(scanService.stopTask(null, taskId))
    }

    @Operation(summary = "获取扫描任务信息")
    @GetMapping("/tasks/{taskId}")
    fun task(@PathVariable("taskId") taskId: String): Response<ScanTask> {
        return ResponseBuilder.success(scanTaskService.task(taskId))
    }

    @Operation(summary = "分页获取扫描任务信息")
    @GetMapping("/tasks")
    fun tasks(scanTaskQuery: ScanTaskQuery, pageLimit: PageLimit): Response<Page<ScanTask>> {
        return ResponseBuilder.success(scanTaskService.tasks(scanTaskQuery, pageLimit))
    }

    @Operation(summary = "获取扫描子任务信息")
    @GetMapping("/tasks/{taskId}/subtasks/{subtaskId}")
    fun subtask(
        @PathVariable("taskId") taskId: String,
        @PathVariable("subtaskId") subtaskId: String
    ): Response<SubtaskResultOverview> {
        return ResponseBuilder.success(scanTaskService.subtaskOverview(subtaskId))
    }

    @Operation(summary = "分页获取扫描子任务信息")
    @GetMapping("/tasks/{taskId}/subtasks")
    fun subtasks(
        @PathVariable("taskId") taskId: String,
        subtaskInfoRequest: SubtaskInfoRequest
    ): Response<Page<SubtaskInfo>> {
        subtaskInfoRequest.parentScanTaskId = taskId
        return ResponseBuilder.success(scanTaskService.subtasks(ScanPlanConverter.convert(subtaskInfoRequest)))
    }

    @Operation(summary = "获取许可扫描子任务信息")
    @GetMapping("/license/tasks/{taskId}/subtasks/{subtaskId}")
    fun licenseSubtask(
        @PathVariable("taskId") taskId: String,
        @PathVariable("subtaskId") subtaskId: String
    ): Response<FileLicensesResultOverview> {
        return ResponseBuilder.success(scanTaskService.subtaskLicenseOverview(subtaskId))
    }
}
