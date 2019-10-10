package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.Permission
import com.tencent.bkrepo.auth.pojo.CheckPermissionRequest
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

@Api(tags = ["SERVICE_PERMISSION"], description = "服务-权限接口")
@FeignClient(SERVICE_NAME, contextId = "ServicePermissionResource")
@RequestMapping("/service/auth/permission")
interface ServicePermissionResource {
    @ApiOperation("校验系统权限")
    @PostMapping("/check")
    fun checkPermission(
        @RequestBody request: CheckPermissionRequest
    ): Response<Boolean>

    @ApiOperation("校验管理员")
    @PostMapping("/checkAdmin/{name}")
    fun checkAdmin(
        @ApiParam(value = "用户名")
        @PathVariable name: String
    ): Response<Boolean>

    @ApiOperation("创建权限")
    @PostMapping("/create")
    fun createPermission(
        @RequestBody request: CreatePermissionRequest
    ): Response<Boolean>

    @ApiOperation("删除权限")
    @DeleteMapping("/delete/{id}")
    fun deletePermission(
        @ApiParam(value = "ID")
        @PathVariable id: String
    ): Response<Boolean>

    @ApiOperation("list权限")
    @GetMapping("/list")
    fun listPermission(
        @ApiParam(value = "资源类型")
        @RequestParam resourceType: ResourceType?
    ): Response<List<Permission>>
}
