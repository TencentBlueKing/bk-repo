package com.tencent.bkrepo.docker.api

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 *  docker image blob 文件处理接口
 *
 * @author: owenlxu
 * @date: 2019-10-13
 */
@Api("docker镜像blob文件处理接口")
@RequestMapping("/v2/")
interface Blob {

    @ApiOperation("上传blob文件")
    @PutMapping("{projectId}/{repoName}/**/blobs/uploads/{uuid}")
    fun uploadBlob(
        request: HttpServletRequest,
        @RequestAttribute
        userId: String?,
        @RequestHeader
        headers: HttpHeaders,
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = "uuid", required = true)
        uuid: String,
        @RequestParam
        @ApiParam(value = "digest", required = false)
        digest: String?
    ): ResponseEntity<Any>

    @ApiOperation("检查blob文件是否存在")
    @RequestMapping(method = [RequestMethod.HEAD], value = ["{projectId}/{repoName}/**/blobs/{digest}"])
    fun isBlobExists(
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
        @ApiParam(value = "digest", required = true)
        digest: String
    ): ResponseEntity<Any>

    @ApiOperation("获取blob文件")
    @RequestMapping(method = [RequestMethod.GET], value = ["{projectId}/{repoName}/**/blobs/{digest}"])
    fun getBlob(
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
        @ApiParam(value = "digest", required = true)
        digest: String
    ): ResponseEntity<Any>

    @ApiOperation("开始上传blob文件")
    @RequestMapping(method = [RequestMethod.POST], value = ["{projectId}/{repoName}/**/blobs/uploads"])
    fun startBlobUpload(
        request: HttpServletRequest,
        @RequestAttribute
        userId: String?,
        @RequestHeader
        headers: HttpHeaders,
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String,
        @RequestParam
        @ApiParam(value = "mount", required = false)
        mount: String?
    ): ResponseEntity<Any>

    @ApiOperation("分片上传blob文件")
    @RequestMapping(method = [RequestMethod.PATCH], value = ["{projectId}/{repoName}/**/blobs/uploads/{uuid}"])
    fun patchUpload(
        request: HttpServletRequest,
        @RequestAttribute
        userId: String?,
        @RequestHeader
        headers: HttpHeaders,
        @PathVariable
        @ApiParam(value = "projectId", required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = "repoName", required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = "uuid", required = false)
        uuid: String
    ): ResponseEntity<Any>
}
