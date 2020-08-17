package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.pojo.repo.UserRepoCreateRequest
import com.tencent.bkrepo.repository.service.RepositoryService
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

@Api("仓库用户接口")
@Principal(PrincipalType.PLATFORM)
@RestController
@RequestMapping("/api/repo")
class UserRepositoryController(
    private val permissionManager: PermissionManager,
    private val repositoryService: RepositoryService
) {

    @ApiOperation("创建仓库")
    @PostMapping
    fun create(
        @RequestAttribute userId: String,
        @RequestBody userRepoCreateRequest: UserRepoCreateRequest
    ): Response<Void> {
        permissionManager.checkPermission(userId, ResourceType.PROJECT, PermissionAction.MANAGE, userRepoCreateRequest.projectId)

        val createRequest = with(userRepoCreateRequest) {
            RepoCreateRequest(
                projectId = projectId,
                name = name,
                type = type,
                category = category,
                public = public,
                description = description,
                configuration = configuration,
                storageCredentialsKey = storageCredentialsKey,
                operator = userId
            )
        }
        repositoryService.create(createRequest)
        return ResponseBuilder.success()
    }

    @ApiOperation("列表查询项目所有仓库")
    @GetMapping("/list/{projectId}")
    fun list(
        @ApiParam(value = "项目id", required = true)
        @PathVariable projectId: String
    ): Response<List<RepositoryInfo>> {
        return ResponseBuilder.success(repositoryService.list(projectId))
    }
}
