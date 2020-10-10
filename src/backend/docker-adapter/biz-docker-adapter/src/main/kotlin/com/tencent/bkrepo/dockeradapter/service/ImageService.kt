package com.tencent.bkrepo.dockeradapter.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.dockeradapter.pojo.DockerRepo
import com.tencent.bkrepo.dockeradapter.pojo.DockerTag
import com.tencent.bkrepo.dockeradapter.pojo.QueryImageTagRequest
import com.tencent.bkrepo.dockeradapter.pojo.QueryProjectImageRequest
import com.tencent.bkrepo.dockeradapter.pojo.QueryPublicImageRequest

interface ImageService {
    fun queryPublicImage(request: QueryPublicImageRequest): Page<DockerRepo>
    fun queryProjectImage(request: QueryProjectImageRequest): Page<DockerRepo>
    fun queryImageTag(request: QueryImageTagRequest): Page<DockerTag>
}