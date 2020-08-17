package com.tencent.bkrepo.docker.api

import com.tencent.bkrepo.docker.constant.DOCKER_API_PREFIX
import com.tencent.bkrepo.docker.constant.DOCKER_API_SUFFIX
import com.tencent.bkrepo.docker.response.DockerResponse
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * docker image auth api
 */
@RequestMapping(DOCKER_API_PREFIX)
interface Auth {

    @ApiOperation("校验仓库版本")
    @GetMapping(DOCKER_API_SUFFIX)
    fun auth(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): DockerResponse
}
