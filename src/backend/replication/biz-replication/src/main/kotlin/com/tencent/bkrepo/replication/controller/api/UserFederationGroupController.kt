package com.tencent.bkrepo.replication.controller.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.security.permission.Principal
import com.tencent.bkrepo.common.security.permission.PrincipalType
import com.tencent.bkrepo.common.security.util.SecurityUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.replication.pojo.federation.FederationGroupInfo
import com.tencent.bkrepo.replication.pojo.federation.request.FederationGroupCreateRequest
import com.tencent.bkrepo.replication.pojo.federation.request.FederationGroupUpdateRequest
import com.tencent.bkrepo.replication.service.FederationGroupService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "联邦集群组接口")
@RestController
@Principal(type = PrincipalType.ADMIN)
@RequestMapping("/api/federation/group")
class UserFederationGroupController(
    private val federationGroupService: FederationGroupService,
) {

    @Operation(summary = "创建联邦集群组")
    @PostMapping
    fun create(@RequestBody request: FederationGroupCreateRequest): Response<FederationGroupInfo> {
        return ResponseBuilder.success(federationGroupService.create(request, SecurityUtils.getUserId()))
    }

    @Operation(summary = "更新联邦集群组")
    @PutMapping
    fun update(@RequestBody request: FederationGroupUpdateRequest): Response<FederationGroupInfo> {
        return ResponseBuilder.success(federationGroupService.update(request, SecurityUtils.getUserId()))
    }

    @Operation(summary = "删除联邦集群组")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): Response<Void> {
        federationGroupService.delete(id)
        return ResponseBuilder.success()
    }

    @Operation(summary = "查询联邦集群组详情")
    @GetMapping("/{id}")
    fun get(@PathVariable id: String): Response<FederationGroupInfo?> {
        return ResponseBuilder.success(federationGroupService.getById(id))
    }

    @Operation(summary = "查询所有联邦集群组")
    @GetMapping
    fun list(): Response<List<FederationGroupInfo>> {
        return ResponseBuilder.success(federationGroupService.list())
    }
}
