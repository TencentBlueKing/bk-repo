package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.AddRolePermissionRequest
import com.tencent.bkrepo.auth.pojo.CreateRoleRequest
import com.tencent.bkrepo.auth.pojo.PermissionRequest
import com.tencent.bkrepo.auth.pojo.Role
import com.tencent.bkrepo.auth.pojo.enums.RoleType
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

@Api("角色接口")
@FeignClient(SERVICE_NAME, contextId = "ServiceRoleResource")
@RequestMapping("/service/auth/role")
interface ServiceUserResource {
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

    @ApiOperation("添加角色权限")
    @PostMapping("/addRolePermission")
    fun addRolePermission(
        @RequestBody request: AddRolePermissionRequest
    ): Response<Boolean>

    @ApiOperation("创建角色")
    @GetMapping("/list/{roleType}")
    fun listByType(
        @ApiParam(value = "角色类型")
        @PathVariable roleType: RoleType
    ): Response<List<Role>>
}
