package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.RoleData
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

@Api("auth接口")
@FeignClient(SERVICE_NAME, contextId = "ServicePermissionResource")
@RequestMapping("/service/auth/role")
interface ServiceRoleResource {
    @ApiOperation("添加用户角色")
    @PostMapping("/{userId}/{roleId}")
    fun addUserRole(
        @ApiParam(value = "用户ID")
        @PathVariable userId: String,
        @ApiParam(value = "RoleID")
        @PathVariable roleId: String
    ): Response<Boolean>

    @ApiOperation("/{userId}/{roleId}")
    @DeleteMapping("/role/")
    fun addRole(
        @ApiParam(value = "用户ID")
        @PathVariable userId: String,
        @ApiParam(value = "RoleID")
        @PathVariable roleId: String,
        @RequestBody roleData: RoleData?
    ): Response<Boolean>


}
