package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.*
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

@Api(tags = ["SERVICE_ROLE"], description = "服务-角色接口")
@FeignClient(SERVICE_NAME, contextId = "ServiceRoleResource")
@RequestMapping("/api/service/auth/role")
interface ServiceRoleResource {
    @ApiOperation("创建角色")
    @PostMapping("/create")
    fun createRole(
        @RequestBody request: CreateRoleRequest
    ): Response<Boolean>

    @ApiOperation("删除角色")
    @DeleteMapping("/delete/{name}")
    fun deleteRole(
        @ApiParam(value = "角色名")
        @PathVariable name: String
    ): Response<Boolean>

    @ApiOperation("list项目角色")
    @GetMapping("/listProjectRole")
    fun listProjectRole(): Response<List<Role>>

    @ApiOperation("list仓库角色")
    @GetMapping("/listRepoRole")
    fun listRepoRole(): Response<List<Role>>

    @ApiOperation("添加用户角色")
    @PostMapping("/addUserRole")
    fun addUserRole(
        @RequestBody request: AddUserRoleRequest
    ): Response<Boolean>

    @ApiOperation("添加角色权限")
    @PostMapping("/addRolePermission")
    fun addRolePermission(
        @RequestBody request: AddRolePermissionRequest
    ): Response<Boolean>
}
