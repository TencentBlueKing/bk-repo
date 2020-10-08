package com.tencent.bkrepo.dockeradapter.controller

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.dockeradapter.pojo.DockerRepo
import com.tencent.bkrepo.dockeradapter.pojo.ImagePageData
import com.tencent.bkrepo.dockeradapter.pojo.QueryProjectImageRequest
import com.tencent.bkrepo.dockeradapter.pojo.QueryPublicImageRequest
import com.tencent.bkrepo.dockeradapter.pojo.Repository
import com.tencent.bkrepo.dockeradapter.service.ImageService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api("镜像接口")
@RestController
@RequestMapping("/api/image")
class UserImageController @Autowired constructor(
    private val imageService: ImageService
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
        @RequestBody request: QueryProjectImageRequest
    ): Response<Page<Repository>> {
        return ResponseBuilder.success(imageService.queryProjectImage(request))
    }

    @ApiOperation("查询tag")
    @PostMapping("/account/create")
    fun queryImageTag(
        @RequestBody request: QueryImageTagRequest
    ): Response<Repository> {
        return ResponseBuilder.success(Repository("dummy", "dummy"))
    }
}
