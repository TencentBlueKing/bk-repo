package com.tencent.bkrepo.docker.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

/**
 *  docker image manifest 文件处理接口
 *
 * @author: owenlxu
 * @date: 2019-10-03
 */
@Api("docker镜像manifest文件处理接口")
@RequestMapping("/v2")
interface Manifest {

    @ApiOperation("上传manifetst文件")
    @PutMapping("/{projectId}/{repoName}/{name}/manifests/{tag}")
    fun putManifest(
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = "name", required = true)
        name: String,
        @PathVariable
        @ApiParam(value = "tag", required = true)
        tag: String,
        @ApiParam
        @RequestHeader(value = "Content-Type", required = true)
        contentType: String,
        request: HttpServletRequest
    ): ResponseEntity<Any>

    @ApiOperation("获取manifest文件")
    @GetMapping("/{projectId}/{repoName}/{name}/manifests/{reference}")
    fun getManifest(
            @PathVariable
            @ApiParam(value = "projectId", required = true)
            projectId: String,
            @PathVariable
            @ApiParam(value = "repoName", required = true)
            repoName: String,
            @PathVariable
            @ApiParam(value = "name", required = true)
            name: String,
            @PathVariable
            @ApiParam(value = "reference", required = true)
            reference: String
    ): ResponseEntity<Any>

    @ApiOperation("检查manifest文件存在")
    @RequestMapping(method = [RequestMethod.HEAD], value = ["{projectId}/{repoName}/{name}/manifests/{reference}"])
    fun existManifest(
            @PathVariable
            @ApiParam(value = "projectId", required = true)
            projectId: String,
            @PathVariable
            @ApiParam(value = "repoName", required = true)
            repoName: String,
            @PathVariable
            @ApiParam(value = "name", required = true)
            name: String,
            @PathVariable
            @ApiParam(value = "reference", required = true)
            reference: String
    ): ResponseEntity<Any>
}
