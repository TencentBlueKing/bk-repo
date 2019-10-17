package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.docker.api.Blob
import com.tencent.bkrepo.docker.v2.model.DockerDigest
import com.tencent.bkrepo.docker.v2.rest.handler.DockerV2LocalRepoHandler
import javax.ws.rs.core.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.HttpHeaders

@RestController
class BlobImpl @Autowired constructor(val dockerRepo: DockerV2LocalRepoHandler) : Blob {

    override fun putBlob(
        projectId: String,
        repoName: String,
        name: String,
        digest: String
    ): Response {
        return Response.ok().header("aaaaaa", "bbbbbb").build()
    }

    override fun isBlobExists(
        projectId: String,
        repoName: String,
        name: String,
        digest: String
    ): Response {
        return dockerRepo.isBlobExists(name, DockerDigest(digest))
    }

    override fun getBlob(
        projectId: String,
        repoName: String,
        name: String,
        digest: String
    ): Response {
        var dockerRepoName = projectId + "/" + repoName + "/" + name
        return dockerRepo.isBlobExists(dockerRepoName, DockerDigest(digest))
    }

   override fun startBlobUpload(
           headers : HttpHeaders,
           projectId: String,
           repoName: String,
           name: String,
           mount: String?
   ): Response {
        dockerRepo.httpHeaders = headers
        return dockerRepo.startBlobUpload(projectId,repoName, name, mount)
    }
}
