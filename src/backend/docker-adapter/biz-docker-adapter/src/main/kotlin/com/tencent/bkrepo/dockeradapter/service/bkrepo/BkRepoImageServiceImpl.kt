package com.tencent.bkrepo.dockeradapter.service.bkrepo

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.dockeradapter.client.BkRepoClient
import com.tencent.bkrepo.dockeradapter.constant.PUBLIC_PROJECT_ID
import com.tencent.bkrepo.dockeradapter.pojo.DockerRepo
import com.tencent.bkrepo.dockeradapter.pojo.DockerTag
import com.tencent.bkrepo.dockeradapter.pojo.QueryImageTagRequest
import com.tencent.bkrepo.dockeradapter.pojo.QueryProjectImageRequest
import com.tencent.bkrepo.dockeradapter.pojo.QueryPublicImageRequest
import com.tencent.bkrepo.dockeradapter.service.ImageService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "adapter", name = ["realm"], havingValue = "bkrepo")
class BkRepoImageServiceImpl(
    private val bkRepoClient: BkRepoClient
) : ImageService {
    override fun queryPublicImage(request: QueryPublicImageRequest): Page<DockerRepo> {
        with(request) {
            return queryProjectImage(QueryProjectImageRequest(searchKey, PUBLIC_PROJECT_ID, PUBLIC_PROJECT_ID, page, page))
        }
    }

    override fun queryProjectImage(request: QueryProjectImageRequest): Page<DockerRepo> {
        return bkRepoClient.queryProjectImage(request)
    }

    override fun queryImageTag(request: QueryImageTagRequest): Page<DockerTag> {
        return bkRepoClient.queryImageTag(request)
    }
}