package com.tencent.bkrepo.repository.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.pojo.repo.UserRepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.UserRepoUpdateRequest
import com.tencent.bkrepo.repository.service.RepositoryService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("仓库用户接口")
@RestController
@RequestMapping("/api/repo")
class UserRepositoryController(
    private val permissionManager: PermissionManager,
    private val repositoryService: RepositoryService
) {

    @ApiOperation("根据名称类型查询仓库")
    @GetMapping("/info/{projectId}/{repoName}", "/info/{projectId}/{repoName}/{type}")
    fun getRepoInfo(
        @RequestAttribute userId: String,
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam(value = "仓库类型", required = true)
        @PathVariable type: String? = null
    ): Response<RepositoryInfo?> {
        permissionManager.checkPermission(userId, ResourceType.PROJECT, PermissionAction.READ, projectId)
        return ResponseBuilder.success(repositoryService.getRepoInfo(projectId, repoName))
    }

    @ApiOperation("创建仓库")
    @PostMapping("/create")
    fun createRepo(
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

    @ApiOperation("分页查询仓库列表")
    @GetMapping("/page/{projectId}/{page}/{size}")
    fun page(
        @RequestAttribute userId: String,
        @ApiParam(value = "项目id", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "当前页", required = true, example = "0")
        @PathVariable page: Int,
        @ApiParam(value = "分页大小", required = true, example = "20")
        @PathVariable size: Int,
        @ApiParam("仓库名称", required = false)
        @RequestParam name: String? = null,
        @ApiParam("仓库类型", required = false)
        @RequestParam type: String? = null
    ): Response<Page<RepositoryInfo>> {
        permissionManager.checkPermission(userId, ResourceType.PROJECT, PermissionAction.READ, projectId)
        return ResponseBuilder.success(repositoryService.page(projectId, page, size, name, type))
    }

    @ApiOperation("列表查询项目所有仓库")
    @GetMapping("/list/{projectId}")
    fun list(
        @RequestAttribute userId: String,
        @ApiParam(value = "项目id", required = true)
        @PathVariable projectId: String,
        @ApiParam("仓库名称", required = false)
        @RequestParam name: String? = null,
        @ApiParam("仓库类型", required = false)
        @RequestParam type: String? = null
    ): Response<List<RepositoryInfo>> {
        permissionManager.checkPermission(userId, ResourceType.PROJECT, PermissionAction.READ, projectId)
        return ResponseBuilder.success(repositoryService.list(projectId))
    }

    @ApiOperation("删除仓库")
    @DeleteMapping("/delete/{projectId}/{repoName}")
    fun deleteRepo(
        @RequestAttribute userId: String,
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String,
        @ApiParam(value = "是否强制删除", required = false)
        @RequestParam forced: Boolean = false
    ): Response<Void> {
        permissionManager.checkPermission(userId, ResourceType.REPO, PermissionAction.DELETE, projectId)
        repositoryService.delete(RepoDeleteRequest(projectId, repoName, forced, userId))
        return ResponseBuilder.success()
    }

    @ApiOperation("更新仓库")
    @DeleteMapping("/update/{projectId}/{repoName}")
    fun deleteRepo(
        @RequestAttribute userId: String,
        @ApiParam(value = "所属项目", required = true)
        @PathVariable projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable repoName: String,
        @RequestBody request: UserRepoUpdateRequest
    ): Response<Void> {
        permissionManager.checkPermission(userId, ResourceType.PROJECT, PermissionAction.DELETE, projectId)
        val repoUpdateRequest = RepoUpdateRequest(
            projectId = projectId,
            name = repoName,
            public = request.public,
            description = request.description,
            configuration = request.configuration,
            operator = userId
        )
        repositoryService.update(repoUpdateRequest)
        return ResponseBuilder.success()
    }
}
