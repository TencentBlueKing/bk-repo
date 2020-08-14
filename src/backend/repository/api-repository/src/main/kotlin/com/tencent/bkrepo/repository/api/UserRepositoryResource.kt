package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import com.tencent.bkrepo.repository.pojo.repo.UserRepoCreateRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 仓库服务接口
 */
@Api("仓库服务接口")
@RequestMapping("/api/repo")
interface UserRepositoryResource {

    @ApiOperation("创建仓库")
    @PostMapping
    fun create(
        @RequestAttribute userId: String,
        @RequestBody userRepoCreateRequest: UserRepoCreateRequest
    ): Response<Void>

    @ApiOperation("列表查询项目所有仓库")
    @GetMapping("/list/{projectId}")
    fun list(
        @ApiParam(value = "项目id", required = true)
        @PathVariable projectId: String
    ): Response<List<RepositoryInfo>>
}
