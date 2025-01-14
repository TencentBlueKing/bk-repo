/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
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
import com.tencent.bk.audit.annotations.AuditAttribute
import com.tencent.bk.audit.annotations.AuditEntry
import com.tencent.bk.audit.annotations.AuditInstanceRecord
import com.tencent.bk.audit.annotations.AuditRequestBody
import com.tencent.bk.audit.context.ActionAuditContext
import com.tencent.bkrepo.common.artifact.audit.PROJECT_RESOURCE
import com.tencent.bkrepo.common.artifact.audit.PROJECT_VIEW_ACTION
import com.tencent.bkrepo.common.artifact.audit.REPO_CREATE_ACTION
import com.tencent.bkrepo.common.artifact.audit.REPO_DELETE_ACTION
import com.tencent.bkrepo.common.artifact.audit.REPO_EDIT_ACTION
import com.tencent.bkrepo.common.artifact.audit.REPO_RESOURCE
import com.tencent.bkrepo.common.artifact.audit.REPO_VIEW_ACTION
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.metadata.permission.PermissionManager
import com.tencent.bkrepo.common.artifact.audit.ActionAuditContent
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.QuotaService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.repository.pojo.repo.ArchiveInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoListOption
import com.tencent.bkrepo.repository.pojo.repo.RepoQuotaInfo
import com.tencent.bkrepo.repository.pojo.repo.RepoUpdateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepositoryDetail
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.pojo.repo.UserRepoCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.UserRepoUpdateRequest
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
    private val repositoryService: RepositoryService,
    private val quotaService: QuotaService,
    private val nodeService: NodeService
) {

    @AuditEntry(
        actionId = REPO_VIEW_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_VIEW_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#repoName",
            instanceNames = "#repoName"
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#projectId"),
        ],
        scopeId = "#projectId",
        content = ActionAuditContent.REPO_VIEW_CONTENT
    )
    @ApiOperation("根据名称类型查询仓库")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/info/{projectId}/{repoName}", "/info/{projectId}/{repoName}/{type}")
    fun getRepoInfo(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable
        projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam(value = "仓库类型", required = true)
        @PathVariable
        type: String? = null,
    ): Response<RepositoryInfo?> {
        return ResponseBuilder.success(repositoryService.getRepoInfo(projectId, repoName, type, true))
    }

    @ApiOperation("根据名称查询仓库是否存在")
    @GetMapping("/exist/{projectId}/{repoName}")
    fun checkExist(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable
        projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable
        repoName: String,
    ): Response<Boolean> {
        return ResponseBuilder.success(repositoryService.checkExist(projectId, repoName))
    }

    @AuditEntry(
        actionId = REPO_CREATE_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_CREATE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#userRepoCreateRequest?.name",
            instanceNames = "#userRepoCreateRequest?.name"
        ),
        attributes = [
            AuditAttribute(
                name = ActionAuditContent.PROJECT_CODE_TEMPLATE,
                value = "#userRepoCreateRequest?.projectId"
            ),
        ],
        scopeId = "#userRepoCreateRequest?.projectId",
        content = ActionAuditContent.REPO_CREATE_CONTENT
    )
    @ApiOperation("创建仓库")
    @PostMapping("/create")
    fun createRepo(
        @RequestAttribute userId: String,
        @AuditRequestBody
        @RequestBody userRepoCreateRequest: UserRepoCreateRequest,
    ): Response<RepositoryDetail> {
        val createRequest = with(userRepoCreateRequest) {
            permissionManager.checkProjectPermission(
                action = if (pluginRequest) PermissionAction.WRITE else PermissionAction.MANAGE,
                projectId = projectId,
            )
            RepoCreateRequest(
                projectId = projectId,
                name = name,
                type = type,
                category = category,
                public = public,
                description = description,
                configuration = configuration,
                storageCredentialsKey = storageCredentialsKey,
                operator = userId,
                quota = quota,
                pluginRequest = pluginRequest,
                display = display,
            )
        }
        ActionAuditContext.current().setInstance(createRequest)
        return ResponseBuilder.success(repositoryService.createRepo(createRequest, true))
    }

    @AuditEntry(
        actionId = REPO_VIEW_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_VIEW_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#repoListOption?.name",
            instanceNames = "#repoListOption?.name",
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#projectId"),
        ],
        scopeId = "#projectId",
        content = ActionAuditContent.REPO_LIST_CONTENT
    )
    @ApiOperation("查询有权限的仓库列表")
    @GetMapping("/list/{projectId}")
    fun listUserRepo(
        @RequestAttribute userId: String,
        @ApiParam(value = "项目id", required = true)
        @PathVariable
        projectId: String,
        repoListOption: RepoListOption,
    ): Response<List<RepositoryInfo>> {
        return ResponseBuilder.success(repositoryService.listPermissionRepo(userId, projectId, repoListOption, true))
    }

    @AuditEntry(
        actionId = PROJECT_VIEW_ACTION
    )
    @ActionAuditRecord(
        actionId = PROJECT_VIEW_ACTION,
        instance = AuditInstanceRecord(
            resourceType = PROJECT_RESOURCE,
            instanceIds = "#projectId",
            instanceNames = "#projectId",
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#projectId"),
        ],
        scopeId = "#projectId",
        content = ActionAuditContent.REPO_LIST_CONTENT
    )
    @ApiOperation("分页查询有权限的仓库列表")
    @GetMapping("/page/{projectId}/{pageNumber}/{pageSize}")
    fun listUserRepoPage(
        @RequestAttribute userId: String,
        @ApiParam(value = "项目id", required = true)
        @PathVariable
        projectId: String,
        @ApiParam(value = "当前页", required = true, example = "0")
        @PathVariable
        pageNumber: Int,
        @ApiParam(value = "分页大小", required = true, example = "20")
        @PathVariable
        pageSize: Int,
        repoListOption: RepoListOption,
    ): Response<Page<RepositoryInfo>> {
        val page = repositoryService.listPermissionRepoPage(
            userId, projectId, pageNumber, pageSize, repoListOption, true
        )
        return ResponseBuilder.success(page)
    }

    @AuditEntry(
        actionId = REPO_VIEW_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_VIEW_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#repoName",
            instanceNames = "#repoName",
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#projectId"),
        ],
        scopeId = "#projectId",
        content = ActionAuditContent.REPO_QUOTE_VIEW_CONTENT
    )
    @ApiOperation("查询仓库配额")
    @Permission(type = ResourceType.REPO, action = PermissionAction.READ)
    @GetMapping("/quota/{projectId}/{repoName}")
    fun getRepoQuota(
        @ApiParam(value = "所属项目", required = true)
        @PathVariable
        projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable
        repoName: String,
    ): Response<RepoQuotaInfo> {
        return ResponseBuilder.success(quotaService.getRepoQuotaInfo(projectId, repoName))
    }


    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#repoName",
            instanceNames = "#repoName",
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#projectId"),
        ],
        scopeId = "#projectId",
        content = ActionAuditContent.REPO_QUOTE_EDIT_CONTENT
    )
    @ApiOperation("修改仓库配额")
    @Permission(type = ResourceType.REPO, action = PermissionAction.MANAGE)
    @PostMapping("/quota/{projectId}/{repoName}")
    fun updateRepoQuota(
        @RequestAttribute userId: String,
        @ApiParam(value = "所属项目", required = true)
        @PathVariable
        projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam(value = "仓库配额", required = true)
        @RequestParam
        quota: Long,
    ): Response<Void> {
        val repoUpdateRequest = RepoUpdateRequest(
            projectId = projectId,
            name = repoName,
            quota = quota,
            operator = userId,
        )
        ActionAuditContext.current().setInstance(repoUpdateRequest)
        repositoryService.updateRepo(repoUpdateRequest)
        return ResponseBuilder.success()
    }

    @AuditEntry(
        actionId = REPO_DELETE_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_DELETE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#repoName",
            instanceNames = "#repoName",
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#projectId"),
        ],
        scopeId = "#projectId",
        content = ActionAuditContent.REPO_DELETE_CONTENT
    )
    @ApiOperation("删除仓库")
    @Permission(type = ResourceType.REPO, action = PermissionAction.DELETE)
    @DeleteMapping("/delete/{projectId}/{repoName}")
    fun deleteRepo(
        @RequestAttribute userId: String,
        @ApiParam(value = "所属项目", required = true)
        @PathVariable
        projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable
        repoName: String,
        @ApiParam(value = "是否强制删除", required = false)
        @RequestParam
        forced: Boolean = false,
    ): Response<Void> {
        repositoryService.deleteRepo(RepoDeleteRequest(projectId, repoName, forced, userId))
        return ResponseBuilder.success()
    }

    @AuditEntry(
        actionId = REPO_EDIT_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_EDIT_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#repoName",
            instanceNames = "#repoName",
        ),
        attributes = [
            AuditAttribute(name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#projectId"),
        ],
        scopeId = "#projectId",
        content = ActionAuditContent.REPO_EDIT_CONTENT
    )
    @ApiOperation("更新仓库")
    @Permission(type = ResourceType.REPO, action = PermissionAction.MANAGE)
    @PostMapping("/update/{projectId}/{repoName}")
    fun updateRepo(
        @RequestAttribute userId: String,
        @ApiParam(value = "所属项目", required = true)
        @PathVariable
        projectId: String,
        @ApiParam(value = "仓库名称", required = true)
        @PathVariable
        repoName: String,
        @RequestBody request: UserRepoUpdateRequest,
    ): Response<Void> {
        val repoUpdateRequest = RepoUpdateRequest(
            projectId = projectId,
            name = repoName,
            public = request.public,
            description = request.description,
            configuration = request.configuration,
            operator = userId,
            display = request.display
        )
        ActionAuditContext.current().setInstance(repoUpdateRequest)
        repositoryService.updateRepo(repoUpdateRequest)
        return ResponseBuilder.success()
    }

    @AuditEntry(
        actionId = REPO_CREATE_ACTION
    )
    @ActionAuditRecord(
        actionId = REPO_CREATE_ACTION,
        instance = AuditInstanceRecord(
            resourceType = REPO_RESOURCE,
            instanceIds = "#userRepoCreateRequest?.name",
            instanceNames = "#userRepoCreateRequest?.name"
        ),
        attributes = [
            AuditAttribute(
                name = ActionAuditContent.PROJECT_CODE_TEMPLATE, value = "#userRepoCreateRequest?.projectId"
            ),
        ],
        scopeId = "#userRepoCreateRequest?.projectId",
        content = ActionAuditContent.REPO_CREATE_CONTENT
    )
    @Deprecated("waiting kb-ci and bk", replaceWith = ReplaceWith("createRepo"))
    @ApiOperation("创建仓库")
    @PostMapping
    fun create(
        @RequestAttribute userId: String,
        @RequestBody userRepoCreateRequest: UserRepoCreateRequest,
    ): Response<RepositoryDetail> {
        return this.createRepo(userId, userRepoCreateRequest)
    }

    @ApiOperation("查询可归档文件大小")
    @GetMapping("/archive/available")
    fun getArchivableSize(
        @RequestParam projectId: String,
        @RequestParam(required = false) repoName: String?,
        @RequestParam days: Int,
        @RequestParam size: Long,
        @RequestAttribute userId: String,
    ): Response<ArchiveInfo> {
        permissionManager.checkProjectPermission(PermissionAction.MANAGE, projectId, userId)
        val archiveInfo = ArchiveInfo(
            available = nodeService.getArchivableSize(projectId, repoName, days, size),
        )
        return ResponseBuilder.success(archiveInfo)
    }
}
