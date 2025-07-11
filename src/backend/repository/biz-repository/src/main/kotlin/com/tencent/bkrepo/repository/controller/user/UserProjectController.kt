/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
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

import com.tencent.bk.audit.annotations.ActionAuditRecord
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bk.audit.annotations.AuditRequestBody
import com.tencent.bk.audit.context.ActionAuditContext
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.artifact.audit.PROJECT_CREATE_ACTION
import com.tencent.bkrepo.common.artifact.audit.PROJECT_EDIT_ACTION
import com.tencent.bkrepo.common.artifact.audit.PROJECT_RESOURCE
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.metadata.service.project.ProjectService
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectListOption
import com.tencent.bkrepo.repository.pojo.project.ProjectMetricsInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectSearchOption
import com.tencent.bkrepo.repository.pojo.project.ProjectUpdateRequest
import com.tencent.bkrepo.repository.pojo.project.UserProjectCreateRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "项目用户接口")
@RestController
@RequestMapping("/api/project")
class UserProjectController(
    private val permissionManager: PermissionManager,
    private val projectService: ProjectService
) {
    @AuditEntry(
        actionId = PROJECT_CREATE_ACTION
    )
    @ActionAuditRecord(
        actionId = PROJECT_CREATE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = PROJECT_RESOURCE,
            instanceIds = "#userProjectRequest?.name",
            instanceNames = "#userProjectRequest?.displayName"
        ),
        scopeId = "#userProjectRequest?.name",
        content = ActionAuditContent.PROJECT_CREATE_CONTENT
    )
    @Operation(summary = "创建项目")
    @Principal(PrincipalType.GENERAL)
    @PostMapping("/create")
    fun createProject(
        @RequestAttribute userId: String,
        @AuditRequestBody
        @RequestBody userProjectRequest: UserProjectCreateRequest
    ): Response<String> {
        val createRequest = with(userProjectRequest) {
            ProjectCreateRequest(
                name = name,
                displayName = displayName,
                description = description,
                operator = userId,
                createPermission = createPermission,
                metadata = metadata
            )
        }
        ActionAuditContext.current().setInstance(createRequest)
        val projectInfo = projectService.createProject(createRequest)
        return ResponseBuilder.success(projectInfo.name)
    }

    @Operation(summary = "查询项目是否存在")
    @GetMapping("/exist/{projectId}")
    @Principal(PrincipalType.GENERAL)
    fun checkExist(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @PathVariable projectId: String
    ): Response<Boolean> {
        return ResponseBuilder.success(projectService.checkExist(projectId))
    }

    @Operation(summary = "校验项目参数是否存在")
    @GetMapping("/exist")
    fun checkProjectExist(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = false)
        @RequestParam name: String?,
        @Parameter(name = "项目ID", required = false)
        @RequestParam displayName: String?
    ): Response<Boolean> {
        return ResponseBuilder.success(projectService.checkProjectExist(name, displayName))
    }


    @AuditEntry(
        actionId = PROJECT_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = PROJECT_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = PROJECT_RESOURCE,
            instanceIds = "#name",
            instanceNames = "#name"
        ),
        scopeId = "#name",
        content = ActionAuditContent.PROJECT_EDIT_CONTENT
    )
    @Operation(summary = "编辑项目")
    @PutMapping("/{name}")
    fun updateProject(
        @RequestAttribute userId: String,
        @Parameter(name = "项目ID", required = true)
        @PathVariable name: String,
        @AuditRequestBody
        @RequestBody projectUpdateRequest: ProjectUpdateRequest
    ): Response<Boolean> {
        ActionAuditContext.current().setInstance(projectUpdateRequest)
        permissionManager.checkProjectPermission(PermissionAction.UPDATE, name)
        return ResponseBuilder.success(projectService.updateProject(name, projectUpdateRequest))
    }

    @Operation(summary = "分页查询所有项目列表")
    @GetMapping("/search")
    @Principal(PrincipalType.ADMIN)
    fun searchProject(option: ProjectSearchOption): Response<Page<ProjectInfo>> {
        return ResponseBuilder.success(projectService.searchProject(option))
    }

    @Operation(summary = "项目列表")
    @GetMapping("/list")
    fun listProject(
        @RequestAttribute userId: String,
        option: ProjectListOption
    ): Response<List<ProjectInfo>> {
        return ResponseBuilder.success(projectService.listPermissionProject(userId, option))
    }

    @AuditEntry(
        actionId = PROJECT_CREATE_ACTION
    )
    @ActionAuditRecord(
        actionId = PROJECT_CREATE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = PROJECT_RESOURCE,
            instanceIds = "#userProjectRequest?.name",
            instanceNames = "#userProjectRequest?.displayName"
        ),
        scopeId = "#userProjectRequest?.name",
        content = ActionAuditContent.PROJECT_CREATE_CONTENT
    )
    @Deprecated("waiting kb-ci", replaceWith = ReplaceWith("createProject"))
    @Operation(summary = "创建项目")
    @Principal(PrincipalType.PLATFORM)
    @PostMapping
    fun create(
        @RequestAttribute userId: String,
        @RequestBody userProjectRequest: UserProjectCreateRequest
    ): Response<String> {
        return this.createProject(userId, userProjectRequest)
    }

    @Operation(summary = "项目仓库统计信息列表")
    @GetMapping("/metrics/{projectId}")
    fun projectMetricsList(
        @Parameter(name = "项目ID", required = true)
        @PathVariable projectId: String
    ): Response<ProjectMetricsInfo?> {
        permissionManager.checkProjectPermission(PermissionAction.READ, projectId)
        return ResponseBuilder.success(projectService.getProjectMetricsInfo(projectId))
    }

    @Operation(summary = "获取项目启用/禁用状态")
    @GetMapping("/enabled/{projectId}")
    fun isProjectEnabled(
        @Parameter(name = "项目ID", required = true)
        @PathVariable projectId: String
    ): Response<Boolean> {
        return ResponseBuilder.success(projectService.isProjectEnabled(projectId))
    }
}
