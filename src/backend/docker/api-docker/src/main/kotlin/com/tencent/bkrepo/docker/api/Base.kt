package com.tencent.bkrepo.docker.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import javax.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping

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
    fun ping(): ResponseEntity<Any>

    @ApiOperation("校验仓库版本")
    @PatchMapping("/v3/bs")
    fun ping2(
        request: HttpServletRequest
    ): ResponseEntity<Any>
}
