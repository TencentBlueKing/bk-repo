package com.tencent.bkrepo.docker.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.constant.DOCKER_API_PREFIX
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_REFERENCE_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_TAG_SUFFIX
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

/**
 *  docker image manifest 文件处理接口
 *
 * @author: owenlxu
 * @date: 2019-10-03
 */
@Api("docker镜像manifest文件处理接口")
@RequestMapping(DOCKER_API_PREFIX)
interface Manifest {

    @ApiOperation("上传manifest文件")
    @PutMapping(DOCKER_MANIFEST_TAG_SUFFIX)
    fun putManifest(
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
        tag: String,
        @ApiParam
        @RequestHeader(value = "Content-Type", required = true)
        contentType: String,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any>

    @ApiOperation("获取manifest文件")
    @GetMapping(DOCKER_MANIFEST_REFERENCE_SUFFIX)
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
        @ApiParam(value = "reference", required = true)
        reference: String
    ): ResponseEntity<Any>

    @ApiOperation("检查manifest文件存在")
    @RequestMapping(method = [RequestMethod.HEAD], value = [DOCKER_MANIFEST_REFERENCE_SUFFIX])
    fun existManifest(
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
        @ApiParam(value = "reference", required = true)
        reference: String
    ): ResponseEntity<Any>
}
