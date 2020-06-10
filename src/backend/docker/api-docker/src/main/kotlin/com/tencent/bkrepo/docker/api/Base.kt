package com.tencent.bkrepo.docker.api

import com.tencent.bkrepo.docker.constant.DOCKER_API_PREFIX
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping

/**
 *  docker image base api
 *
 * @author: owenlxu
 * @date: 2019-11-08
 */
@Api("docker镜像仓库base")
interface Base {

    @ApiOperation("校验仓库版本")
    @GetMapping(DOCKER_API_PREFIX)
    fun ping(): ResponseEntity<Any>
}
