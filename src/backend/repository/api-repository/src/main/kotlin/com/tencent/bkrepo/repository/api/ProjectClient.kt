package com.tencent.bkrepo.repository.api

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.repository.constant.SERVICE_NAME
import com.tencent.bkrepo.repository.pojo.project.ProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.project.ProjectInfo
import com.tencent.bkrepo.repository.pojo.project.ProjectRangeQueryRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Primary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Api(description = "服务-项目接口")
@Primary
@FeignClient(SERVICE_NAME, contextId = "ProjectClient")
@RequestMapping("/service/project")
interface ProjectClient {

    @ApiOperation("查询项目")
    @GetMapping("/query/{name}")
    fun query(@ApiParam(value = "项目名") @PathVariable name: String): Response<ProjectInfo?>

    @ApiOperation("项目列表")
    @GetMapping("/list")
    fun list(): Response<List<ProjectInfo>>

    @ApiOperation("项目分页查询")
    @GetMapping("/rangeQuery")
    fun rangeQuery(@RequestBody request: ProjectRangeQueryRequest): Response<Page<ProjectInfo?>>

    @ApiOperation("创建项目")
    @PostMapping
    fun create(@RequestBody request: ProjectCreateRequest): Response<ProjectInfo>
}
