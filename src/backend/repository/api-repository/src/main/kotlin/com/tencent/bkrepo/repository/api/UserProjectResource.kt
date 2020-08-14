package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.project.UserProjectCreateRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 项目服务接口
 */
@Api("项目服务接口")
@RequestMapping("/api/project")
interface UserProjectResource {

    @ApiOperation("创建项目")
    @PostMapping
    fun create(
        @RequestAttribute userId: String,
        @RequestBody userProjectRequest: UserProjectCreateRequest
    ): Response<Void>

    @ApiOperation("项目列表")
    @GetMapping("/list")
    fun list(): Response<List<ProjectInfo>>
}
