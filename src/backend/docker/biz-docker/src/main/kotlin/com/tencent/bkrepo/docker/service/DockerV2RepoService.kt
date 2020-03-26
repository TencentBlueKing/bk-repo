package com.tencent.bkrepo.docker.service

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.docker.model.DockerBasicPath
import com.tencent.bkrepo.docker.model.DockerDigest
import org.springframework.http.ResponseEntity

interface DockerV2RepoService {
    fun ping(): ResponseEntity<Any>

    fun isBlobExists(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest): ResponseEntity<Any>

    fun getBlob(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest): ResponseEntity<Any>

    fun startBlobUpload(projectId: String, repoName: String, dockerRepo: String, mount: String?): ResponseEntity<Any>

    fun patchUpload(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        uuid: String,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any>

    fun uploadBlob(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        digest: DockerDigest,
        uuid: String,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any>

    fun uploadManifest(
        path: DockerBasicPath,
        tag: String,
        mediaType: String,
        artifactFile: ArtifactFile
    ): ResponseEntity<Any>

    fun getManifest(path: DockerBasicPath, reference: String): ResponseEntity<Any>

    fun deleteManifest(projectId: String, repoName: String, dockerRepo: String, reference: String): ResponseEntity<Any>

    fun getTags(
        projectId: String,
        repoName: String,
        dockerRepo: String,
        maxEntries: Int,
        lastEntry: String
    ): ResponseEntity<Any>

    fun catalog(projectId: String, name: String, maxEntries: Int, lastEntry: String): ResponseEntity<Any>
}
