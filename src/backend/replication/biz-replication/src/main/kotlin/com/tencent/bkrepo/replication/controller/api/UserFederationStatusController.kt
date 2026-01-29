package com.tencent.bkrepo.replication.controller.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.federation.FederationMemberStatusInfo
import com.tencent.bkrepo.replication.pojo.federation.FederationRepositoryStatusInfo
import com.tencent.bkrepo.replication.service.FederationStatusService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 联邦仓库状态管理接口
 *
 * 提供联邦仓库状态查询和监控功能
 */
@Tag(name = "联邦仓库状态接口")
@RestController
@Principal(type = PrincipalType.ADMIN)
@RequestMapping("/api/federation/status")
class UserFederationStatusController(
    private val federationStatusService: FederationStatusService
) {

    @Operation(summary = "获取联邦仓库状态")
    @GetMapping("/repository")
    fun getFederationRepositoryStatus(
        @Parameter(description = "项目ID", required = true)
        @RequestParam projectId: String,
        @Parameter(description = "仓库名称", required = true)
        @RequestParam repoName: String,
        @Parameter(description = "联邦ID", required = false)
        @RequestParam(required = false) federationId: String?
    ): Response<List<FederationRepositoryStatusInfo>> {
        val statusList = federationStatusService.getFederationRepositoryStatus(projectId, repoName, federationId)
        return ResponseBuilder.success(statusList)
    }

    @Operation(summary = "获取联邦成员状态")
    @GetMapping("/members")
    fun getFederationMemberStatus(
        @Parameter(description = "项目ID", required = true)
        @RequestParam projectId: String,
        @Parameter(description = "仓库名称", required = true)
        @RequestParam repoName: String,
        @Parameter(description = "联邦ID", required = true)
        @RequestParam federationId: String
    ): Response<List<FederationMemberStatusInfo>> {
        val members = federationStatusService.getFederationMemberStatus(projectId, repoName, federationId)
        return ResponseBuilder.success(members)
    }

    @Operation(summary = "刷新联邦成员状态")
    @PostMapping("/members/refresh")
    fun refreshMemberStatus(
        @RequestAttribute userId: String,
        @Parameter(description = "项目ID", required = true)
        @RequestParam projectId: String,
        @Parameter(description = "仓库名称", required = true)
        @RequestParam repoName: String,
        @Parameter(description = "联邦ID", required = true)
        @RequestParam federationId: String
    ): Response<Void> {
        federationStatusService.refreshMemberStatus(projectId, repoName, federationId)
        return ResponseBuilder.success()
    }
}


