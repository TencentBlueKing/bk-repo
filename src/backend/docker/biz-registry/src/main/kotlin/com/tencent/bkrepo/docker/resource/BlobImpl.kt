package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.docker.api.Blob
import com.tencent.bkrepo.docker.v2.rest.handler.DockerV2LocalRepoHandler
import javax.ws.rs.core.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import com.tencent.bkrepo.docker.v2.model.DockerDigest

@RestController
class BlobImpl @Autowired constructor(val dockerRepo: DockerV2LocalRepoHandler) : Blob {

    override fun putBlob(
        name: String,
        digest: String
    ): Response {
        return Response.ok().header("aaaaaa", "bbbbbb").build()
    }

    override fun isBlobExists(
        name: String,
        digest: String
    ): Response {
        return dockerRepo.isBlobExists(name ,DockerDigest(digest))
    }
}
