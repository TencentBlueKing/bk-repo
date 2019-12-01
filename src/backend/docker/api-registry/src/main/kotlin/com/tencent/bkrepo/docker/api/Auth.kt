package com.tencent.bkrepo.docker.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


/**
 *  docker image auth api
 *
 * @author: owenlxu
 * @date: 2019-11-27
 */

interface Auth {

    @ApiOperation("校验仓库版本")
    @GetMapping("/v2/auth")
    fun auth (
            request: HttpServletRequest,
            response : HttpServletResponse
    ):ResponseEntity<Any>
}

