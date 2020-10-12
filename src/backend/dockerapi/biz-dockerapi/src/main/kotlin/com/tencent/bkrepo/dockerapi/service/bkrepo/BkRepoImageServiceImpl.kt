package com.tencent.bkrepo.dockerapi.service.bkrepo

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.dockerapi.client.BkRepoClient
import com.tencent.bkrepo.dockerapi.constant.DEFAULT_DOCKER_REPO_NAME
import com.tencent.bkrepo.dockerapi.constant.PUBLIC_PROJECT_ID
import com.tencent.bkrepo.dockerapi.pojo.DockerRepo
import com.tencent.bkrepo.dockerapi.pojo.DockerTag
import com.tencent.bkrepo.dockerapi.pojo.QueryImageTagRequest
import com.tencent.bkrepo.dockerapi.pojo.QueryProjectImageRequest
import com.tencent.bkrepo.dockerapi.pojo.QueryPublicImageRequest
import com.tencent.bkrepo.dockerapi.service.ImageService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "dockerapi", name = ["realm"], havingValue = "bkrepo")
class BkRepoImageServiceImpl(
    private val bkRepoClient: BkRepoClient
) : ImageService {
    override fun queryPublicImage(request: QueryPublicImageRequest): Page<DockerRepo> {
        with(request) {
            return queryProjectImage(QueryProjectImageRequest(searchKey, PUBLIC_PROJECT_ID, DEFAULT_DOCKER_REPO_NAME, page, pageSize))
        }
    }

    override fun queryProjectImage(request: QueryProjectImageRequest): Page<DockerRepo> {
        return bkRepoClient.queryProjectImage(request)
    }

    override fun queryImageTag(request: QueryImageTagRequest): Page<DockerTag> {
        return bkRepoClient.queryImageTag(request)
    }
}