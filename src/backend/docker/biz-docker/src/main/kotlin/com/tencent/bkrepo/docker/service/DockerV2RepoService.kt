package com.tencent.bkrepo.docker.service

import com.tencent.bkrepo.docker.model.DockerDigest
import java.io.InputStream
import javax.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity

interface DockerV2RepoService {
    fun ping(): ResponseEntity<Any>

    fun isBlobExists(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest): ResponseEntity<Any>

    fun getBlob(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest): ResponseEntity<Any>

    fun startBlobUpload(projectId: String, repoName: String, dockerRepo: String, mount: String?): ResponseEntity<Any>

    fun patchUpload(projectId: String, repoName: String, dockerRepo: String, uuid: String, request: HttpServletRequest): ResponseEntity<Any>

    fun uploadBlob(projectId: String, repoName: String, dockerRepo: String, digest: DockerDigest, uuid: String, stream: InputStream): ResponseEntity<Any>

    fun uploadManifest(projectId: String, repoName: String, dockerRepo: String, tag: String, mediaType: String, stream: InputStream): ResponseEntity<Any>

    fun getManifest(projectId: String, repoName: String, dockerRepo: String, reference: String): ResponseEntity<Any>

    fun deleteManifest(projectId: String, repoName: String, dockerRepo: String, reference: String): ResponseEntity<Any>

    fun getTags(projectId: String, repoName: String, dockerRepo: String, maxEntries: Int, lastEntry: String): ResponseEntity<Any>

    fun catalog(projectId: String, repoName: String, maxEntries: Int, lastEntry: String): ResponseEntity<Any>
}
