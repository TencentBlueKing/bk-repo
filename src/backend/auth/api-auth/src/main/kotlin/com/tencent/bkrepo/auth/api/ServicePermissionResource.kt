package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.PermissionData
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

@Api("auth接口")
@FeignClient(SERVICE_NAME, contextId = "ServicePermissionResource")
@RequestMapping("/service/auth/permission")
interface ServicePermissionResource {
    @ApiOperation("校验权限")
    @PostMapping("/check")
    fun check(
        @ApiParam(value = "用户ID")
        @RequestHeader userId: String,
        @ApiParam(value = "权限类型")
        @RequestHeader permissionType: String,
        @ApiParam(value = "action")
        @RequestBody checkPermissionData: PermissionData?
    ): Response<Boolean>
}
