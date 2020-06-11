package com.tencent.bkrepo.docker.service

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerDigest
import org.springframework.http.ResponseEntity

interface DockerV2RepoService {
    fun ping(): ResponseEntity<Any>

    fun isBlobExists(context: RequestContext, digest: DockerDigest): ResponseEntity<Any>

    fun getBlob(context: RequestContext, digest: DockerDigest): ResponseEntity<Any>

    fun startBlobUpload(context: RequestContext, mount: String?): ResponseEntity<Any>

    fun patchUpload(context: RequestContext, uuid: String, file: ArtifactFile): ResponseEntity<Any>

    fun uploadBlob(
        context: RequestContext,
        digest: DockerDigest,
        uuid: String,
        file: ArtifactFile
    ): ResponseEntity<Any>

    fun uploadManifest(
        context: RequestContext,
        tag: String,
        mediaType: String,
        file: ArtifactFile
    ): ResponseEntity<Any>

    fun getManifest(context: RequestContext, reference: String): ResponseEntity<Any>

    fun deleteManifest(context: RequestContext, reference: String): ResponseEntity<Any>

    fun getTags(context: RequestContext, maxEntries: Int, lastEntry: String): ResponseEntity<Any>

    fun catalog(projectId: String, name: String, maxEntries: Int, lastEntry: String): ResponseEntity<Any>
}
