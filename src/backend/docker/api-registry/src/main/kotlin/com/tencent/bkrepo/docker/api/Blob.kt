package com.tencent.bkrepo.docker.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.ws.rs.core.Response
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

/**
 *  docker image blob 文件处理接口
 *
 * @author: owenlxu
 * @date: 2019-10-13
 */
@Api("docker镜像blob文件处理接口")
@RequestMapping("/v2")
interface Blob {

    @ApiOperation("上传blob文件")
    @PutMapping("{name}/blobs/{digest}")
    fun putBlob(
        @PathVariable
        @ApiParam(value = "name", required = true)
        name: String,
        @PathVariable
        @ApiParam(value = "digest", required = true)
        digest: String
    ): Response

    @ApiOperation("上传blob文件")
    @RequestMapping(method = [RequestMethod.GET], value = ["{name}/blobs/{digest}"])
    fun isBlobExists(
        @PathVariable
        @ApiParam(value = "name", required = true)
        name: String,
        @PathVariable
        @ApiParam(value = "digest", required = true)
        digest: String
    ): Response
}
