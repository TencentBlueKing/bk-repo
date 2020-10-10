package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.project.UserProjectCreateRequest
import com.tencent.bkrepo.repository.service.ProjectService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api("项目用户接口")
@Principal(PrincipalType.PLATFORM)
@RestController
@RequestMapping("/api/project")
class UserProjectController(
    private val permissionManager: PermissionManager,
    private val projectService: ProjectService
) {
    @ApiOperation("创建项目")
    @PostMapping("/create")
    fun create(
        @RequestAttribute userId: String,
        @RequestBody userProjectRequest: UserProjectCreateRequest
    ): Response<Void> {
        val createRequest = with(userProjectRequest) {
            ProjectCreateRequest(
                name = name,
                displayName = displayName,
                description = description,
                operator = userId
            )
        }
        projectService.create(createRequest)
        return ResponseBuilder.success()
    }

    @ApiOperation("查询项目是否存在")
    @GetMapping("/exist/{projectId}")
    fun checkProjectExist(
        @RequestAttribute userId: String,
        @ApiParam(value = "项目ID", required = true)
        @PathVariable projectId: String
    ): Response<Boolean> {
        permissionManager.checkPermission(userId, ResourceType.PROJECT, PermissionAction.READ, projectId)
        return ResponseBuilder.success(projectService.exist(projectId))
    }

    @ApiOperation("项目列表")
    @GetMapping("/list")
    fun list(): Response<List<ProjectInfo>> {
        return ResponseBuilder.success(projectService.list())
    }
}
