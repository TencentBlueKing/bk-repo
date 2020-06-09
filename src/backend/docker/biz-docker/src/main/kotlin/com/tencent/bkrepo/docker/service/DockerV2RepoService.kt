package com.tencent.bkrepo.docker.service

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerDigest
import org.springframework.http.ResponseEntity

interface DockerV2RepoService {
    fun ping(): ResponseEntity<Any>

    fun isBlobExists(pathContext: RequestContext, digest: DockerDigest): ResponseEntity<Any>

    fun getBlob(pathContext: RequestContext, digest: DockerDigest): ResponseEntity<Any>

    fun startBlobUpload(pathContext: RequestContext, mount: String?): ResponseEntity<Any>

    fun patchUpload(pathContext: RequestContext, uuid: String, artifactFile: ArtifactFile): ResponseEntity<Any>

    fun uploadBlob(
        pathContext: RequestContext,
        digest: DockerDigest,
        uuid: String,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any>

    fun uploadManifest(
        pathContext: RequestContext,
        tag: String,
        mediaType: String,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any>

    fun getManifest(pathContext: RequestContext, reference: String): ResponseEntity<Any>

    fun deleteManifest(pathContext: RequestContext, reference: String): ResponseEntity<Any>

    fun getTags(pathContext: RequestContext, maxEntries: Int, lastEntry: String): ResponseEntity<Any>

    fun catalog(projectId: String, name: String, maxEntries: Int, lastEntry: String): ResponseEntity<Any>
}
