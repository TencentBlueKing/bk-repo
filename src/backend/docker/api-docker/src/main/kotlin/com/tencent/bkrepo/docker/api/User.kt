package com.tencent.bkrepo.docker.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping

/**
 *  docker image extension api
 *
 * @author: owenlxu
 * @date: 2020-03-12
 */
@Api("docker镜像仓库扩展查询api")
@RequestMapping("/user")
interface User {

    @ApiOperation("获取manifest文件")
    @GetMapping("/manifest/{projectId}/{repoName}/**/{tag}")
    fun getManifest(
        request: HttpServletRequest,
        @RequestAttribute
        userId: String?,
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = "tag", required = true)
        tag: String
    ): ResponseEntity<Any>

    @ApiOperation("获取layer文件")
    @GetMapping("/layer/{id}")
    fun getLayer(
        @PathVariable
        @ApiParam(value = "id", required = true)
        id: String
    ): ResponseEntity<Any>

    @ApiOperation("获取所有repo")
    @GetMapping("/repo/{projectId}/{repoName}")
    fun getRepo(
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String
    ): ResponseEntity<Any>

    @ApiOperation("获取repo所有的tag")
    @GetMapping("/repo/tag/{projectId}/{repoName}/{repo}")
    fun getRepoTag(
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String
    ): ResponseEntity<Any>

}
