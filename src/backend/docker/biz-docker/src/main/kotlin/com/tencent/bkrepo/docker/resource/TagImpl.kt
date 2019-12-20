package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.docker.api.Tag
import com.tencent.bkrepo.docker.service.DockerV2LocalRepoService
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
        dockerRepo.userId = userId
        return dockerRepo.getTags(projectId, repoName, name, maxEntries, index)
    }
}
