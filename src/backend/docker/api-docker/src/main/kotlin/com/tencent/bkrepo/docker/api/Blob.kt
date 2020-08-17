package com.tencent.bkrepo.docker.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.constant.DOCKER_API_PREFIX
import com.tencent.bkrepo.docker.constant.DOCKER_BLOB_DIGEST_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_BLOB_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_BLOB_UUID_SUFFIX
import com.tencent.bkrepo.docker.constant.DOCKER_DIGEST
import com.tencent.bkrepo.docker.constant.DOCKER_PROJECT_ID
import com.tencent.bkrepo.docker.constant.DOCKER_REPO_NAME
import com.tencent.bkrepo.docker.constant.DOCKER_UUID
import com.tencent.bkrepo.docker.response.DockerResponse
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam

/**
 * docker image blob api
 */
@Api("docker镜像blob文件处理接口")
@RequestMapping(DOCKER_API_PREFIX)
interface Blob {

    @ApiOperation("上传blob文件")
    @PutMapping(DOCKER_BLOB_UUID_SUFFIX)
    fun uploadBlob(
        request: HttpServletRequest,
        @RequestAttribute
        userId: String?,
        @RequestHeader
        headers: HttpHeaders,
        @PathVariable
        @ApiParam(value = DOCKER_PROJECT_ID, required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = DOCKER_REPO_NAME, required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = DOCKER_UUID, required = true)
        uuid: String,
        @RequestParam
        @ApiParam(value = DOCKER_DIGEST, required = false)
        digest: String?,
        artifactFile: ArtifactFile
    ): DockerResponse

    @ApiOperation("检查blob文件是否存在")
    @RequestMapping(method = [RequestMethod.HEAD], value = [DOCKER_BLOB_DIGEST_SUFFIX])
    fun isBlobExists(
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
        @ApiParam(value = DOCKER_DIGEST, required = true)
        digest: String
    ): DockerResponse

    @ApiOperation("获取blob文件")
    @RequestMapping(method = [RequestMethod.GET], value = [DOCKER_BLOB_DIGEST_SUFFIX])
    fun getBlob(
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
        @ApiParam(value = DOCKER_DIGEST, required = true)
        digest: String
    ): DockerResponse

    @ApiOperation("开始上传blob文件")
    @RequestMapping(method = [RequestMethod.POST], value = [DOCKER_BLOB_SUFFIX])
    fun startBlobUpload(
        request: HttpServletRequest,
        @RequestAttribute
        userId: String?,
        @RequestHeader
        headers: HttpHeaders,
        @PathVariable
        @ApiParam(value = DOCKER_PROJECT_ID, required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = DOCKER_REPO_NAME, required = true)
        repoName: String,
        @RequestParam
        @ApiParam(value = "mount", required = false)
        mount: String?
    ): DockerResponse

    @ApiOperation("分片上传blob文件")
    @RequestMapping(method = [RequestMethod.PATCH], value = [DOCKER_BLOB_UUID_SUFFIX])
    fun patchUpload(
        request: HttpServletRequest,
        @RequestAttribute
        userId: String?,
        @RequestHeader
        headers: HttpHeaders,
        @PathVariable
        @ApiParam(value = DOCKER_PROJECT_ID, required = true)
        projectId: String,
        @PathVariable
        @ApiParam(value = DOCKER_REPO_NAME, required = true)
        repoName: String,
        @PathVariable
        @ApiParam(value = DOCKER_UUID, required = false)
        uuid: String,
        artifactFile: ArtifactFile
    ): DockerResponse
}
