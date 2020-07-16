package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import com.tencent.bkrepo.docker.api.Catalog
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.service.DockerV2LocalRepoService
import com.tencent.bkrepo.docker.util.UserUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class CatalogImpl @Autowired constructor(val dockerRepo: DockerV2LocalRepoService) : Catalog {

    override fun list(
        userId: String,
        projectId: String,
        repoName: String,
        n: Int?,
        last: String?
    ): ResponseEntity<Any> {
        var maxEntries = 0
        var index = EMPTY
        n?.let {
            maxEntries = n
        }
        last?.let {
            index = last
        }
        val uId = UserUtil.getContextUserId(userId)
        val pathContext = RequestContext(uId, projectId, repoName, EMPTY)
        return dockerRepo.catalog(pathContext, maxEntries, index)
    }
}
