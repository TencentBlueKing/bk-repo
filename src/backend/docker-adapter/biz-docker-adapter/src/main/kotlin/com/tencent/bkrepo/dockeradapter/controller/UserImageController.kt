package com.tencent.bkrepo.dockeradapter.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.dockeradapter.client.BkAuthClient
import com.tencent.bkrepo.dockeradapter.pojo.DockerRepo
import com.tencent.bkrepo.dockeradapter.pojo.DockerTag
import com.tencent.bkrepo.dockeradapter.pojo.QueryImageTagRequest
import com.tencent.bkrepo.dockeradapter.pojo.QueryProjectImageRequest
import com.tencent.bkrepo.dockeradapter.pojo.QueryPublicImageRequest
import com.tencent.bkrepo.dockeradapter.service.ImageService
import com.tencent.bkrepo.dockeradapter.util.JwtUtils
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api("镜像接口")
@RestController
@RequestMapping("/api/image")
class UserImageController @Autowired constructor(
    private val imageService: ImageService,
    private val jwtUtils: JwtUtils,
    private val bkAuthClient: BkAuthClient
) {
    @ApiOperation("查询镜像")
    @PostMapping("/queryPublicImage")
    fun queryPublicImage(
        @RequestBody request: QueryPublicImageRequest
    ): Response<Page<DockerRepo>> {
        return ResponseBuilder.success(imageService.queryPublicImage(request))
    }

    @ApiOperation("查询镜像")
    @PostMapping("/queryProjectImage")
    fun queryProjectImage(
        @RequestHeader("X-BKAPI-JWT") jwkToken: String?,
        @RequestBody request: QueryProjectImageRequest
    ): Response<Page<DockerRepo>> {
//        val jwkData = jwtUtils.parseJwtToken(jwkToken!!) ?: throw StatusCodeException(HttpStatus.UNAUTHORIZED, "invalid jwt token")
//        val hashPermission = bkAuthClient.checkProjectPermission(jwkData!!.userName, request.projectId)
//        if (!hashPermission) throw StatusCodeException(HttpStatus.UNAUTHORIZED, "permission denied")
        return ResponseBuilder.success(imageService.queryProjectImage(request))
    }

    @ApiOperation("查询tag")
    @PostMapping("/queryImageTag")
    fun queryImageTag(
        @RequestBody request: QueryImageTagRequest
    ): Response<Page<DockerTag>> {
        return ResponseBuilder.success(imageService.queryImageTag(request))
    }
}
