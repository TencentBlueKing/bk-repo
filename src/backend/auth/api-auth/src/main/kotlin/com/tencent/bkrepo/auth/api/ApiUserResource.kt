package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.CreateUserToProjectRequest
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Api(tags = ["API_USER"], description = "用户api-用户接口")
@FeignClient(SERVICE_NAME, contextId = "ApiUserResource")
@RequestMapping("/api/user")
interface ApiUserResource : UserResource {

    @ApiOperation("创建项目用户")
    @PostMapping("/create/project")
    fun createUserToProject(
        @RequestBody request: CreateUserToProjectRequest
    ): Response<Boolean>
}
