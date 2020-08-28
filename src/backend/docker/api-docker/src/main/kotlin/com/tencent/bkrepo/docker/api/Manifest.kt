package com.tencent.bkrepo.docker.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.constant.DOCKER_API_PREFIX
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_REFERENCE_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_MANIFEST_TAG_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_PROJECT_ID
import com.tencent.bkrepo.docker.constant.DOCKER_REFERENCE
import com.tencent.bkrepo.docker.constant.DOCKER_REPO_NAME
import com.tencent.bkrepo.docker.constant.DOCKER_TAG
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import javax.servlet.http.HttpServletRequest

/**
 * docker image manifest 文件处理接口
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
        @ApiParam(value = DOCKER_PROJECT_ID, required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = DOCKER_REPO_NAME, required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = DOCKER_TAG, required = true)
        tag: String,
        @ApiParam
        @RequestHeader(value = CONTENT_TYPE, required = true)
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
        @ApiParam(value = DOCKER_PROJECT_ID, required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = DOCKER_REPO_NAME, required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = DOCKER_REFERENCE, required = true)
        reference: String
    ): ResponseEntity<Any>

    @ApiOperation("检查manifest文件存在")
    @RequestMapping(method = [RequestMethod.HEAD], value = [DOCKER_MANIFEST_REFERENCE_SUFFIX])
    fun existManifest(
        request: HttpServletRequest,
        @RequestAttribute
        userId: String?,
        @PathVariable
        @ApiParam(value = DOCKER_PROJECT_ID, required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = DOCKER_REPO_NAME, required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = DOCKER_REFERENCE, required = true)
        reference: String
    ): ResponseEntity<Any>
}
