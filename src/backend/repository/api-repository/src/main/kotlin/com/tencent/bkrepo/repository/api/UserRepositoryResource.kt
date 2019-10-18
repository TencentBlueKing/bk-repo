package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID
import com.tencent.bkrepo.common.api.constant.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.repo.UserRepoCreateRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 仓库服务接口
 *
 * @author: carrypan
 * @date: 2019-10-18
 */
@Api("仓库服务接口")
@FeignClient(SERVICE_NAME, contextId = "UserRepositoryResource")
@RequestMapping("/user/repo")
interface UserRepositoryResource {

    @ApiOperation("创建仓库")
    @PostMapping
    fun create(
        @ApiParam(value = "用户id", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @RequestHeader(AUTH_HEADER_USER_ID)
        userId: String,
        @RequestBody userRepoCreateRequest: UserRepoCreateRequest
    ): Response<Void>
}
