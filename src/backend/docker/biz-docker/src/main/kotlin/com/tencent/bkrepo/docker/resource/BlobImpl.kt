package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.api.Blob
import com.tencent.bkrepo.docker.constant.BLOB_PATTERN
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.service.DockerV2LocalRepoService
import com.tencent.bkrepo.docker.util.PathUtil
import com.tencent.bkrepo.docker.util.UserUtil.Companion.getContextUserId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
class BlobImpl @Autowired constructor(val dockerRepo: DockerV2LocalRepoService) : Blob {

    override fun uploadBlob(
        request: HttpServletRequest,
        userId: String?,
        headers: HttpHeaders,
        projectId: String,
        repoName: String,
        uuid: String,
        digest: String?,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any> {
        dockerRepo.httpHeaders = headers
        dockerRepo.userId = getContextUserId(userId)
        val name = PathUtil.artifactName(request, BLOB_PATTERN, projectId, repoName)
        return dockerRepo.uploadBlob(
            RequestContext(projectId, repoName, name),
            DockerDigest(digest),
            uuid,
            artifactFile
        )
    }

    override fun isBlobExists(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String,
        digest: String
    ): ResponseEntity<Any> {
        dockerRepo.userId = getContextUserId(userId)
        val name = PathUtil.artifactName(request, BLOB_PATTERN, projectId, repoName)
        return dockerRepo.isBlobExists(
            RequestContext(
                projectId,
                repoName,
                name
            ), DockerDigest(digest))
    }

    override fun getBlob(
        request: HttpServletRequest,
        userId: String?,
        projectId: String,
        repoName: String,
        digest: String
    ): ResponseEntity<Any> {
        dockerRepo.userId = getContextUserId(userId)
        val name = PathUtil.artifactName(request, BLOB_PATTERN, projectId, repoName)
        return dockerRepo.getBlob(
            RequestContext(
                projectId,
                repoName,
                name
            ), DockerDigest(digest))
    }

    override fun startBlobUpload(
        request: HttpServletRequest,
        userId: String?,
        headers: HttpHeaders,
        projectId: String,
        repoName: String,
        mount: String?
    ): ResponseEntity<Any> {
        dockerRepo.httpHeaders = headers
        dockerRepo.userId = getContextUserId(userId)
        val name = PathUtil.artifactName(request, BLOB_PATTERN, projectId, repoName)
        return dockerRepo.startBlobUpload(
            RequestContext(
                projectId,
                repoName,
                name
            ), mount)
    }

    override fun patchUpload(
        request: HttpServletRequest,
        userId: String?,
        headers: HttpHeaders,
        projectId: String,
        repoName: String,
        uuid: String,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any> {
        dockerRepo.httpHeaders = headers
        dockerRepo.userId = getContextUserId(userId)
        val name = PathUtil.artifactName(request, BLOB_PATTERN, projectId, repoName)
        return dockerRepo.patchUpload(
            RequestContext(
                projectId,
                repoName,
                name
            ), uuid, artifactFile)
    }
}
