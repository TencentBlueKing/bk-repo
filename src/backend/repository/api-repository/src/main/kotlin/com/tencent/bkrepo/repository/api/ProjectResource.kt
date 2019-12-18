package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

@Api(description = "服务-项目接口")
@FeignClient(SERVICE_NAME, contextId = "ServiceProjectResource")
@RequestMapping("/service/project")
interface ProjectResource {

    @ApiOperation("查询项目")
    @GetMapping("/query/{name}")
    fun query(@ApiParam(value = "项目名") @PathVariable name: String): Response<ProjectInfo?>

    @ApiOperation("项目列表")
    @GetMapping("/list")
    fun list(): Response<List<ProjectInfo>>

    @ApiOperation("创建项目")
    @PostMapping
    fun create(@RequestBody request: ProjectCreateRequest): Response<Void>

}
