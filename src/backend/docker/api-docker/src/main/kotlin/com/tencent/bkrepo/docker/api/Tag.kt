package com.tencent.bkrepo.docker.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 *  docker 查询所有的tag
 *
 * @author: owenlxu
 * @date: 2019-12-01
 */
@Api("docker镜像blob文件处理接口")
@RequestMapping("/v2/")

interface Tag {

    @ApiOperation("列出指定name的所有tag")
    @GetMapping("/{projectId}/{repoName}/{name}/tags/list")
    fun list(
        @RequestAttribute
        userId: String,
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = "name", required = true)
        name: String,
        @RequestParam(required = false)
        @ApiParam(value = "n", required = false)
        n: Int?,
        @RequestParam(required = false)
        @ApiParam(value = "last", required = false)
        last: String?
    ): ResponseEntity<Any>
}
