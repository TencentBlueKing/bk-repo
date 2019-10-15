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

@Api(tags = ["SERVICE_USER"], description = "服务-用户接口")
@FeignClient(SERVICE_NAME, contextId = "ServiceUserResource")
@RequestMapping("/service/auth/user")
interface ServiceUserResource {
    @ApiOperation("创建用户")
    @PostMapping("/create")
    fun createUser(
        @RequestBody request: CreateUserRequest
    ): Response<Boolean>

    @ApiOperation("删除用户")
    @DeleteMapping("/deleteByName/{name}")
    fun deleteByName(
        @ApiParam(value = "用户名")
        @PathVariable name: String
    ): Response<Boolean>
}
