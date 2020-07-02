package com.tencent.bkrepo.docker.api

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.docker.constant.DOCKER_USER_LAYER_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_USER_MANIFEST_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_USER_REPO_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_USER_TAG_SUFFIX
import com.tencent.bkrepo.docker.constant.USER_API_PREFIX
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import javax.servlet.http.HttpServletRequest

/**
 *  docker image extension api
 *
 * @author: owenlxu
 * @date: 2020-03-12
 */
@Api("docker镜像仓库扩展查询api")
@RequestMapping(USER_API_PREFIX)
interface User {

    @ApiOperation("获取manifest文件")
    @GetMapping(DOCKER_USER_MANIFEST_SUFFIX)
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
    ): Response<String>

    @ApiOperation("获取layer文件")
    @GetMapping(DOCKER_USER_LAYER_SUFFIX)
    fun getLayer(
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
        @ApiParam(value = "id", required = true)
        id: String
    ): ResponseEntity<Any>

    @ApiOperation("获取所有image")
    @GetMapping(DOCKER_USER_REPO_SUFFIX)
    fun getRepo(
        request: HttpServletRequest,
        @RequestAttribute
        userId: String?,
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String
    ): Response<List<String>>

    @ApiOperation("获取repo所有的tag")
    @GetMapping(DOCKER_USER_TAG_SUFFIX)
    fun getRepoTag(
        request: HttpServletRequest,
        @RequestAttribute
        userId: String?,
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String
    ): Response<Map<String, String>>
}
