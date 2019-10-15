package com.tencent.bkrepo.auth.api

import com.tencent.bkrepo.auth.constant.SERVICE_NAME
import com.tencent.bkrepo.auth.pojo.CreateProjectRequest
import com.tencent.bkrepo.auth.pojo.Project
import com.tencent.bkrepo.common.api.pojo.Response
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

@Api(tags = ["SERVICE_PROJECT"], description = "服务-项目接口")
@FeignClient(SERVICE_NAME, contextId = "ServiceProjectResource")
@RequestMapping("/service/auth/project")
interface ServiceProjectResource {
    @ApiOperation("创建项目")
    @PostMapping("/create")
    fun createProject(
        @RequestBody request: CreateProjectRequest
    ): Response<Boolean>

    @ApiOperation("删除项目")
    @DeleteMapping("/delete/{name}")
    fun deleteByName(
        @ApiParam(value = "项目名")
        @PathVariable name: String
    ): Response<Boolean>

    @ApiOperation("list项目")
    @GetMapping("/list")
    fun listProject(): Response<List<Project>>
}
