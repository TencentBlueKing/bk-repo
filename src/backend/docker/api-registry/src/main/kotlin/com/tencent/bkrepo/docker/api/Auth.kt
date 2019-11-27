package com.tencent.bkrepo.docker.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping


/**
 *  docker image base api
 *
 * @author: owenlxu
 * @date: 2019-11-27
 */

interface Auth {

    @ApiOperation("校验仓库版本")
    @GetMapping("/auth")
    fun auth (
    ): ResponseEntity<Any>
}

