package com.tencent.bkrepo.dockeradapter.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.dockeradapter.pojo.DockerRepo
import com.tencent.bkrepo.dockeradapter.pojo.ImagePageData
import com.tencent.bkrepo.dockeradapter.pojo.QueryProjectImageRequest
import com.tencent.bkrepo.dockeradapter.pojo.QueryPublicImageRequest
import com.tencent.bkrepo.dockeradapter.pojo.Repository

interface ImageService {
    fun queryPublicImage(request: QueryPublicImageRequest): Page<DockerRepo>
    fun queryProjectImage(request: QueryProjectImageRequest): Page<Repository>

}