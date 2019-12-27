package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.pojo.UpdateUserRequest
import com.tencent.bkrepo.auth.pojo.User
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Api(tags = ["SERVICE_USER"], description = "服务-用户接口")
@FeignClient(SERVICE_NAME, contextId = "ServiceUserResource")
@RequestMapping("/user")
interface ServiceUserResource {
    @ApiOperation("创建用户")
    @PostMapping("/create")
    fun createUser(
        @RequestBody request: CreateUserRequest
    ): Response<Boolean>

    @ApiOperation("删除用户")
    @DeleteMapping("/{uid}")
    fun deleteById(
        @ApiParam(value = "用户id")
        @PathVariable uid: String
    ): Response<Boolean>

    @ApiOperation("用户详情")
    @GetMapping("/detail/{uid}")
    fun detail(
        @ApiParam(value = "用户id")
        @PathVariable uid: String
    ): Response<User?>

    @ApiOperation("更新用户信息")
    @PutMapping("/{uid}")
    fun updateById(
        @ApiParam(value = "用户id")
        @PathVariable uid: String,
        @ApiParam(value = "用户更新信息")
        @RequestBody request: UpdateUserRequest
    ): Response<Boolean>

    @ApiOperation("新增用户所属角色")
    @PostMapping("/role/{uid}/{rid}")
    fun addUserRole(
        @ApiParam(value = "用户id")
        @PathVariable uid: String,
        @ApiParam(value = "用户角色id")
        @PathVariable rid: String
    ): Response<User?>

    @ApiOperation("删除用户所属角色")
    @DeleteMapping("/role/{uid}/{rid}")
    fun removeUserRole(
        @ApiParam(value = "用户id")
        @PathVariable uid: String,
        @ApiParam(value = "用户角色")
        @PathVariable rid: String
    ): Response<User?>

    @ApiOperation("批量新增用户所属角色")
    @PatchMapping("/role/add/{rid}")
    fun addUserRolePatch(
        @ApiParam(value = "用户角色Id")
        @PathVariable rid: String,
        @ApiParam(value = "用户id集合")
        @RequestBody request: List<String>
    ): Response<Boolean>

    @ApiOperation("批量删除用户所属角色")
    @PatchMapping("/role/delete/{rid}")
    fun deleteUserRolePatch(
        @ApiParam(value = "用户角色Id")
        @PathVariable rid: String,
        @ApiParam(value = "用户id集合")
        @RequestBody request: List<String>
    ): Response<Boolean>

    @ApiOperation("创建用户token")
    @PostMapping("/token/{uid}")
    fun createToken(
        @ApiParam(value = "用户id")
        @PathVariable uid: String
    ): Response<User?>

    @ApiOperation("删除用户token")
    @DeleteMapping("/token/{uid}/{token}")
    fun deleteToken(
        @ApiParam(value = "用户id")
        @PathVariable uid: String,
        @ApiParam(value = "用户token")
        @PathVariable token: String
    ): Response<User?>

    @ApiOperation("校验用户token")
    @GetMapping("/token/{uid}/{token}")
    fun checkUserToken(
        @ApiParam(value = "用户id")
        @PathVariable uid: String,
        @ApiParam(value = "用户token")
        @PathVariable token: String
    ): Response<Boolean>
}
