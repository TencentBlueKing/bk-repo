package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.docker.api.Tag
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.service.DockerV2LocalRepoService
import com.tencent.bkrepo.docker.util.UserUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class TagImpl @Autowired constructor(val dockerRepo: DockerV2LocalRepoService) : Tag {

    override fun list(
        userId: String,
        projectId: String,
        repoName: String,
        name: String,
        n: Int?,
        last: String?
    ): ResponseEntity<Any> {
        var maxEntries = 0
        var index = ""
        if (n != null) {
            maxEntries = n
        }
        if (last != null) {
            index = last
        }
        val uId = UserUtil.getContextUserId(userId)
        val pathContext = RequestContext(uId, projectId, repoName, name)
        return dockerRepo.getTags(pathContext, maxEntries, index)
    }
}
