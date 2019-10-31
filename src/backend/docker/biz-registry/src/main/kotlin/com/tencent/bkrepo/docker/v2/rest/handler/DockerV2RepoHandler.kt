package com.tencent.bkrepo.docker.v2.rest.handler

import com.tencent.bkrepo.docker.v2.model.DockerDigest
import java.io.InputStream
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.core.Response
import org.springframework.http.ResponseEntity

interface DockerV2RepoHandler {
    fun ping(): Response

    fun isBlobExists(projectId: String, repoName: String, name: String, digest: DockerDigest): ResponseEntity<Any>

    fun getBlob(rojectId: String, repoName: String, name: String, digest: DockerDigest): ResponseEntity<Any>

    fun startBlobUpload(projectId: String, repoName: String, name: String, digest: String?): ResponseEntity<Any>

    fun patchUpload(projectId: String, repoName: String, name: String, uuid: String, request: HttpServletRequest): ResponseEntity<Any>

    fun uploadBlob(projectId: String, repoName: String, name: String, digest: DockerDigest, uuid: String, stream: InputStream): ResponseEntity<Any>

    fun uploadManifest(projectId: String, repoName: String, name: String, tag: String, mediaType: String, stream: InputStream): ResponseEntity<Any>

//    fun getManifest(var1: String, var2: String): Response
//
//    fun deleteManifest(var1: String, var2: String): Response
//
//    fun getTags(var1: String, var2: Int, var3: String): Response
//
//    fun catalog(var1: Int, var2: String): Response
}
