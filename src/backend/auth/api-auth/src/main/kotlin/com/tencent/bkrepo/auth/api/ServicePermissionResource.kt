package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.CreatePermissionRequest
import com.tencent.bkrepo.auth.pojo.PermissionRequest
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Api("权限接口")
@FeignClient(SERVICE_NAME, contextId = "ServicePermissionResource")
@RequestMapping("/service/auth/permission")
interface ServicePermissionResource {
    @ApiOperation("校验系统权限")
    @PostMapping("/check")
    fun checkPermission(
        @RequestBody permissionRequest: PermissionRequest
    ): Response<Boolean>

    @ApiOperation("校验系统权限")
    @PostMapping("/checkAdmin/{name}")
    fun checkAdmin(
        @ApiParam(value = "用户名")
        @PathVariable name: String
    ): Response<Boolean>

    @ApiOperation("创建权限")
    @PostMapping("/create")
    fun createPermission(
        @RequestBody createPermissionRequest: CreatePermissionRequest
    ): Response<Boolean>
}
