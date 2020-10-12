package com.tencent.bkrepo.dockerapi.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.dockerapi.pojo.DockerRepo
import com.tencent.bkrepo.dockerapi.pojo.DockerTag
import com.tencent.bkrepo.dockerapi.pojo.QueryImageTagRequest
import com.tencent.bkrepo.dockerapi.pojo.QueryProjectImageRequest
import com.tencent.bkrepo.dockerapi.pojo.QueryPublicImageRequest

interface ImageService {
    fun queryPublicImage(request: QueryPublicImageRequest): Page<DockerRepo>
    fun queryProjectImage(request: QueryProjectImageRequest): Page<DockerRepo>
    fun queryImageTag(request: QueryImageTagRequest): Page<DockerTag>
}