package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.docker.api.Blob
import com.tencent.bkrepo.docker.v2.model.DockerDigest
import com.tencent.bkrepo.docker.v2.rest.handler.DockerV2LocalRepoHandler
import javax.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.xnio.IoUtils

@RestController
class BlobImpl @Autowired constructor(val dockerRepo: DockerV2LocalRepoHandler) : Blob {

    override fun uploadBlob(
        headers: HttpHeaders,
        projectId: String,
        repoName: String,
        name: String,
        uuid: String,
        digest: String?,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        dockerRepo.httpHeaders = headers
        return dockerRepo.uploadBlob(projectId,repoName,name,DockerDigest(digest),uuid,request.inputStream)
    }

    override fun isBlobExists(
        projectId: String,
        repoName: String,
        name: String,
        digest: String
    ): ResponseEntity<Any> {
        return dockerRepo.isBlobExists(name, DockerDigest(digest))
    }

//    override fun getBlob(
//        projectId: String,
//        repoName: String,
//        name: String,
//        digest: String
//    ): Response {
//        var dockerRepoName = projectId + "/" + repoName + "/" + name
//        return dockerRepo.isBlobExists(dockerRepoName, DockerDigest(digest))
//    }

   override fun startBlobUpload(
       headers: HttpHeaders,
       projectId: String,
       repoName: String,
       name: String,
       mount: String?
   ): ResponseEntity<Any> {
        dockerRepo.httpHeaders = headers
        return dockerRepo.startBlobUpload(projectId, repoName, name, mount)
    }

    override fun patchUpload(
            headers: HttpHeaders,
            projectId: String,
            repoName: String,
            name: String,
            uuid: String
    ): ResponseEntity<Any> {
        dockerRepo.httpHeaders = headers
        return dockerRepo.startBlobUpload(projectId, repoName, name, uuid)
    }

    override fun test(
            request: HttpServletRequest
    ): ResponseEntity<Any> {
        //return dockerRepo.startBlobUpload(projectId, repoName, name, mount)
        return dockerRepo.testUpload(request.inputStream)
    }
}
