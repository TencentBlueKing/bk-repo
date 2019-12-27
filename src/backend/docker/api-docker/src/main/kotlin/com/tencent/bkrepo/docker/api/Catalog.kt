package com.tencent.bkrepo.docker.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam

/**
 *  docker image catalog api
 *
 * @author: owenlxu
 * @date: 2019-12-01
 */

@Api("docker镜像catalog相关接口")
@RequestMapping("/v2/")
interface Catalog {

    @ApiOperation("获取catalog列表")
    @RequestMapping(method = [RequestMethod.GET], value = ["_catalog"])
    fun list(
        @RequestAttribute
        userId: String,
        @RequestParam(required = false)
        @ApiParam(value = "projectId", required = false)
        projectId: String,
        @RequestParam(required = false)
        @ApiParam(value = "repoName", required = false)
        repoName: String,
        @RequestParam(required = false)
        @ApiParam(value = "n", required = false)
        n: Int?,
        @RequestParam(required = false)
        @ApiParam(value = "last", required = false)
        last: String?

    ): ResponseEntity<Any>
}
