package com.tencent.bkrepo.docker.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import javax.servlet.http.HttpServletRequest


/**
 *  docker image base api
 *
 * @author: owenlxu
 * @date: 2019-11-08
 */
@Api("docker镜像仓库base")
interface Base {

    @ApiOperation("校验仓库版本")
    @GetMapping("/v2")
    fun ping (
    ): ResponseEntity<Any>

    @ApiOperation("校验仓库版本")
    @GetMapping("/v3/{projectId}/{repoName}/**/bbs/{name}")
    fun ping2 (
        request : HttpServletRequest,
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = "name", required = true)
        name: String
    ): ResponseEntity<Any>
}